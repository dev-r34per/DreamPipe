package com.newtube.mobile.library;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.newtube.mobile.ui.browse.NavigationPreferences;
import androidx.recyclerview.widget.ItemTouchHelper;
import com.newtube.mobile.ui.common.MobileSnackbar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Local playlist/library surface hosted in Browse, so bottom navigation remains present. */
public final class LocalLibraryPanel implements LocalPlaylistStore.Listener, LocalHistoryStore.Listener {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> new Thread(r, "DreamPipe-transfer"));
    private final ComponentActivity mActivity;
    private final View mRoot;
    private final RecyclerView mList;
    private final EditText mSearch;
    private final LocalPlaylistStore mStore;
    private final LocalHistoryStore mHistory;
    private final LocalListAdapter mPlaylists;
    private final LocalListAdapter mVideos;
    private final ItemTouchHelper mTouchHelper;
    private boolean mDragging;
    private String mMovingId;
    private String mDragPlaylistId;
    private String mAnchorId;
    private boolean mMovingAfter;
    private final Runnable mChromeChanged;
    private final com.google.android.material.appbar.AppBarLayout mHeader;
    private final ActivityResultLauncher<String[]> mImport;
    private final ActivityResultLauncher<Intent> mExport;
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final ExecutorService mQueries = Executors.newSingleThreadExecutor(r -> new Thread(r, "DreamPipe-library-query"));
    private final Runnable mApplySearch = this::render;
    private Future<?> mPendingQuery;
    private int mQueryGeneration;
    private final File mPendingExport;
    private String mPlaylistId;
    private boolean mShowingHistory;
    private boolean mReleased;
    private boolean mSelecting;
    private boolean mSelectionBusy;
    private final java.util.Set<String> mSelectedIds = new java.util.LinkedHashSet<>();
    private int mRevealed = 0;

    public LocalLibraryPanel(ComponentActivity activity, ViewGroup container, Runnable chromeChanged) {
        this(activity, container, chromeChanged, LocalPlaylistStore.instance(activity), LocalHistoryStore.instance(activity));
    }

    LocalLibraryPanel(ComponentActivity activity, ViewGroup container, Runnable chromeChanged,
                      LocalPlaylistStore store, LocalHistoryStore history) {
        mActivity = activity;
        mChromeChanged = chromeChanged;
        mRoot = LayoutInflater.from(activity).inflate(R.layout.view_local_library, container, true);
        mHeader = mRoot.findViewById(R.id.local_playlist_appbar);
        mList = mRoot.findViewById(R.id.local_list);
        mList.setLayoutManager(new LinearLayoutManager(activity));
        mSearch = mRoot.findViewById(R.id.local_search);
        mStore = store;
        mHistory = history;
        mPlaylists = new LocalListAdapter(mRowsHost);
        mVideos = new LocalListAdapter(mRowsHost);
        mTouchHelper = new ItemTouchHelper(mDragCallback);
        mTouchHelper.attachToRecyclerView(mList);
        mPendingExport = new File(activity.getCacheDir(), "dreampipe-playlists-export.txt");
        mImport = activity.registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::readImport);
        mExport = activity.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            Uri uri = result.getData() != null ? result.getData().getData() : null;
            if (result.getResultCode() != android.app.Activity.RESULT_OK || uri == null) {
                mPendingExport.delete();
                return;
            }
            IO.execute(() -> {
                try (InputStream input = new java.io.FileInputStream(mPendingExport);
                     OutputStream output = activity.getContentResolver().openOutputStream(uri, "wt")) {
                    if (output == null) throw new java.io.IOException("No writable document");
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                    output.flush();
                    post(() -> MobileSnackbar.show(activity, R.string.dreampipe_export_done));
                } catch (Exception error) {
                    post(() -> MobileSnackbar.show(activity, R.string.dreampipe_export_failed));
                } finally { mPendingExport.delete(); }
            });
        });
        mSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                mRevealed = 0;
                mQueryGeneration++;
                if (mPendingQuery != null) mPendingQuery.cancel(true);
                mMain.removeCallbacks(mApplySearch);
                mMain.postDelayed(mApplySearch, 140);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        mRoot.findViewById(R.id.local_sort).setOnClickListener(v -> chooseSort());
        mRoot.findViewById(R.id.local_actions).setOnClickListener(v -> beginSelection(null));
        mRoot.findViewById(R.id.local_import).setOnClickListener(v -> mImport.launch(new String[] {
                "application/json", "text/*", "audio/x-mpegurl", "application/x-mpegurl", "application/octet-stream"}));
        mRoot.findViewById(R.id.local_manage).setOnClickListener(v -> {
            LocalPlaylist playlist = selected();
            if (playlist != null) playlistActions(playlist); else libraryActions();
        });
        mRoot.findViewById(R.id.local_selection_done).setOnClickListener(v -> cancelSelection());
        mRoot.findViewById(R.id.local_select_shown).setOnClickListener(v -> {
            if (mSelectionBusy) return;
            boolean checked = ((android.widget.CheckBox) v).isChecked();
            LocalListAdapter adapter = activeRows();
            for (int i = 0; i < adapter.getItemCount(); i++) {
                String key = LocalListAdapter.key(adapter.item(i));
                if (checked) mSelectedIds.add(key); else mSelectedIds.remove(key);
            }
            updateSelectionUi();
        });
        mRoot.findViewById(R.id.local_bulk_merge).setOnClickListener(v -> {
            if (selected() != null) moveSelectedVideos();
            else if (mSelectedIds.size() == 1) renameSelection();
            else mergeSelection();
        });
        mRoot.findViewById(R.id.local_bulk_export).setOnClickListener(v -> exportSelection());
        mRoot.findViewById(R.id.local_bulk_delete).setOnClickListener(v -> deleteSelection());
        mRoot.findViewById(R.id.local_bulk_download).setOnClickListener(v ->
                com.newtube.mobile.downloads.PlaylistDownloadDialog.show(mActivity, selectionVideos()));
        ((TextView) mRoot.findViewById(R.id.local_bulk_delete)).setTextColor(mActivity.getColor(R.color.dreampipe_danger));
        mRoot.findViewById(R.id.local_create).setOnClickListener(v -> create());
        mRoot.findViewById(R.id.local_play_all).setOnClickListener(v -> playPlaylist(false, false));
        mRoot.findViewById(R.id.local_shuffle).setOnClickListener(v -> playPlaylist(true, false));
        mRoot.findViewById(R.id.local_background).setOnClickListener(v -> playPlaylist(false, true));
        mRoot.findViewById(R.id.local_show_more).setOnClickListener(v -> {
            mRevealed = visibleLimit() + NavigationPreferences.rowLimit(activity);
            render();
        });
        mStore.addListener(this);
        mHistory.addListener(this);
        render();
    }

    public void showPlaylists() { cancelSelection(); mShowingHistory = false; mPlaylistId = null; resetSearch(); }
    public void showHistory() { cancelSelection(); mShowingHistory = true; mPlaylistId = null; resetSearch(); }
    public boolean isDetail() { return mPlaylistId != null; }
    public CharSequence title() {
        LocalPlaylist playlist = selected();
        return playlist != null ? playlist.name : mActivity.getString(mShowingHistory
                ? R.string.dreampipe_history : R.string.dreampipe_local_playlists);
    }
    public boolean back() {
        if (mSelecting) { cancelSelection(); return true; }
        if (mSearch.length() > 0) { mSearch.setText(""); return true; }
        if (mPlaylistId == null) return false;
        showPlaylists();
        return true;
    }
    public void saveState(Bundle state) {
        state.putString("dreampipe:playlist", mPlaylistId);
        state.putString("dreampipe:library_query", mSearch.getText().toString());
        state.putInt("dreampipe:library_rows", mRevealed);
        state.putBoolean("dreampipe:selecting", mSelecting);
        state.putStringArrayList("dreampipe:selected_ids", new ArrayList<>(mSelectedIds));
    }
    public void restoreState(Bundle state) {
        mPlaylistId = state.getString("dreampipe:playlist");
        mSearch.setText(state.getString("dreampipe:library_query", ""));
        mRevealed = state.getInt("dreampipe:library_rows", 0);
        mSelecting = state.getBoolean("dreampipe:selecting", false);
        List<String> checked = state.getStringArrayList("dreampipe:selected_ids");
        mSelectedIds.clear();
        if (checked != null) mSelectedIds.addAll(checked);
        render();
    }
    public void release() {
        mReleased = true;
        mQueryGeneration++;
        mMain.removeCallbacksAndMessages(null);
        mQueries.shutdownNow();
        mTouchHelper.attachToRecyclerView(null);
        mStore.removeListener(this);
        mHistory.removeListener(this);
        mList.setAdapter(null);
    }
    public void refresh() { render(); }
    @Override public void onPlaylistsChanged() { render(); }
    @Override public void onHistoryChanged() { if (mShowingHistory) render(); }
    private LocalPlaylist selected() { return mPlaylistId != null ? mStore.find(mPlaylistId) : null; }

    private void resetSearch() {
        mRevealed = 0;
        mList.setVisibility(View.GONE);
        mSearch.setText("");
        mSearch.clearFocus();
        render();
        mList.scrollToPosition(0);
        mHeader.setExpanded(true, false);
        mChromeChanged.run();
    }

    private int visibleLimit() {
        int limit = NavigationPreferences.rowLimit(mActivity);
        return limit == 0 ? Integer.MAX_VALUE : Math.max(limit, mRevealed);
    }

    private void render() {
        if (mReleased || mDragging) return;
        mMain.removeCallbacks(mApplySearch);
        if (mPendingQuery != null) mPendingQuery.cancel(true);
        final int generation = ++mQueryGeneration;
        LocalPlaylist playlist = selected();
        if (mPlaylistId != null && playlist == null && mStore.isLoaded()) {
            mPlaylistId = null; mSelecting = false; mSelectedIds.clear();
        }
        boolean detail = playlist != null;
        mRoot.findViewById(R.id.local_playlist_header).setVisibility(detail ? View.VISIBLE : View.GONE);
        mRoot.findViewById(R.id.local_create).setVisibility(!mShowingHistory && !detail && !mSelecting ? View.VISIBLE : View.GONE);
        mSearch.setHint(!mShowingHistory && !detail ? R.string.dreampipe_search_playlists : R.string.dreampipe_search_videos);
        String query = mSearch.getText().toString().trim().toLowerCase(Locale.ROOT);
        final int limit = visibleLimit();
        final boolean history = mShowingHistory;
        final boolean error = history ? mHistory.error() != null : mStore.error() != null;
        final boolean loading = !history && !mStore.isLoaded();
        java.util.Set<String> valid = new java.util.HashSet<>();
        if (detail) for (LocalVideo video : playlist.videos) valid.add(video.id);
        else if (!history) for (LocalPlaylist item : mStore.snapshot()) valid.add(item.id);
        if (mStore.isLoaded()) mSelectedIds.retainAll(valid);
        updateSelectionUi();
        if (!mShowingHistory && !detail) {
            final List<LocalPlaylist> source = mStore.snapshot();
            final int sort = prefs().getInt("playlist_sort", 3);
            if (mList.getAdapter() != mPlaylists) mList.setAdapter(mPlaylists);
            mPendingQuery = mQueries.submit(() -> {
                LocalLibraryQuery.Result<LocalPlaylist> result = LocalLibraryQuery.playlists(source, query, sort, limit);
                if (Thread.currentThread().isInterrupted()) return;
                post(() -> {
                    if (generation != mQueryGeneration) return;
                    mPlaylists.submit(result.items, sort == 3 && query.isEmpty());
                    updateSelectionUi();
                    renderEmpty(result.total, query, false, history, error, loading, limit);
                });
            });
        } else {
            final List<LocalVideo> source = detail ? playlist.videos : mHistory.snapshot();
            final int sort = prefs().getInt(history ? "history_sort" : "video_sort", 0);
            if (mList.getAdapter() != mVideos) mList.setAdapter(mVideos);
            mPendingQuery = mQueries.submit(() -> {
                LocalLibraryQuery.Result<LocalVideo> result = LocalLibraryQuery.videos(source, query, sort, limit);
                if (Thread.currentThread().isInterrupted()) return;
                post(() -> {
                    if (generation != mQueryGeneration) return;
                    mVideos.submit(result.items, detail && sort == 0 && query.isEmpty());
                    updateSelectionUi();
                    renderEmpty(result.total, query, detail, history, error, loading, limit);
                });
            });
        }
        if (detail) {
            ((TextView) mRoot.findViewById(R.id.local_playlist_name)).setText(playlist.name);
            long seconds = 0;
            for (LocalVideo video : playlist.videos) seconds += video.durationMs / 1000;
            String duration = seconds > 0 ? " · " + (seconds / 3600 > 0 ? seconds / 3600 + ":" : "")
                    + String.format(Locale.ROOT, seconds / 3600 > 0 ? "%02d:%02d" : "%d:%02d", seconds / 60 % 60, seconds % 60) : "";
            ((TextView) mRoot.findViewById(R.id.local_playlist_count)).setText(videoCount(playlist.videos.size()) + duration);
            for (int id : new int[] {R.id.local_play_all, R.id.local_shuffle, R.id.local_background}) {
                mRoot.findViewById(id).setEnabled(!playlist.videos.isEmpty());
            }
        }
        mChromeChanged.run();
    }

    private void renderEmpty(int count, String query, boolean detail, boolean history, boolean error, boolean loading, int limit) {
        boolean empty = count == 0 || error || loading;
        mRoot.findViewById(R.id.local_empty).setVisibility(empty ? View.VISIBLE : View.GONE);
        mList.setVisibility(empty ? View.GONE : View.VISIBLE);
        int title = error ? R.string.dreampipe_library_error : loading ? R.string.dreampipe_loading
                : !query.isEmpty() ? R.string.dreampipe_no_matches : detail ? R.string.dreampipe_empty_playlist
                : history ? R.string.dreampipe_empty_history : R.string.dreampipe_empty_playlists;
        ((TextView) mRoot.findViewById(R.id.local_empty_title)).setText(title);
        TextView body = mRoot.findViewById(R.id.local_empty_body);
        body.setVisibility(error || loading || !query.isEmpty() ? View.GONE : View.VISIBLE);
        body.setText(detail ? R.string.dreampipe_empty_playlist_body : history
                ? R.string.dreampipe_empty_history_body : R.string.dreampipe_empty_playlists_body);
        mRoot.findViewById(R.id.local_show_more).setVisibility(!empty && count > limit ? View.VISIBLE : View.GONE);
    }

    private android.content.SharedPreferences prefs() {
        return mActivity.getSharedPreferences("dreampipe_library", Context.MODE_PRIVATE);
    }
    private String videoCount(int count) {
        return mActivity.getResources().getQuantityString(R.plurals.dreampipe_video_count, count, count);
    }

    private void chooseSort() {
        boolean library = !mShowingHistory && selected() == null;
        int[] ids = library ? new int[] {R.string.dreampipe_sort_name, R.string.dreampipe_sort_newest, R.string.dreampipe_sort_updated, R.string.dreampipe_sort_manual}
                : new int[] {mShowingHistory ? R.string.dreampipe_sort_newest : R.string.dreampipe_sort_manual,
                    R.string.dreampipe_sort_name, R.string.dreampipe_sort_duration};
        CharSequence[] labels = new CharSequence[ids.length];
        for (int i = 0; i < ids.length; i++) labels[i] = mActivity.getString(ids[i]);
        String key = library ? "playlist_sort" : mShowingHistory ? "history_sort" : "video_sort";
        new MaterialAlertDialogBuilder(mActivity).setTitle(R.string.dreampipe_sort)
                .setSingleChoiceItems(labels, prefs().getInt(key, library ? 3 : 0), (dialog, which) -> {
                    prefs().edit().putInt(key, which).apply();
                    dialog.dismiss(); render();
                }).setNegativeButton(R.string.dreampipe_cancel, null).show();
    }

    private void create() {
        LocalPlaylistActions.promptName(mActivity, R.string.dreampipe_create, "", name -> mStore.create(name,
                LocalPlaylistActions.result(mActivity, playlist -> MobileSnackbar.show(mActivity,
                        mActivity.getString(R.string.dreampipe_created, playlist.name)))));
    }

    private void libraryActions() {
        if (!mShowingHistory) return;
        new MaterialAlertDialogBuilder(mActivity).setTitle(R.string.dreampipe_clear_history)
                .setMessage(R.string.dreampipe_clear_history_body)
                .setNegativeButton(R.string.dreampipe_cancel, null)
                .setPositiveButton(R.string.dreampipe_clear_history, (d, w) -> mHistory.clear()).show();
    }

    private void playlistActions(LocalPlaylist playlist) {
        actions(playlist.name, new int[] {R.string.dreampipe_rename, R.string.dreampipe_merge,
                R.string.dialog_download, R.string.dreampipe_export, R.string.dreampipe_remove_watched,
                R.string.dreampipe_delete}, java.util.Arrays.asList(
                () -> rename(playlist), () -> mergeInto(playlist),
                () -> com.newtube.mobile.downloads.PlaylistDownloadDialog.show(mActivity, playlist.videos),
                () -> chooseExport(playlist), () -> removeWatched(playlist), () -> delete(playlist)));
    }

    private void rename(LocalPlaylist playlist) {
        LocalPlaylistActions.promptName(mActivity, R.string.dreampipe_rename, playlist.name,
                name -> mStore.rename(playlist.id, name, LocalPlaylistActions.result(mActivity, null)));
    }

    private void removeWatched(LocalPlaylist playlist) {
        List<com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService.State> states = new ArrayList<>(
                com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService.instance(mActivity).getStates());
        IO.execute(() -> {
            java.util.Set<String> completed = new java.util.HashSet<>();
            for (com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService.State state : states)
                if (state.durationMs > 0 && state.positionMs >= state.durationMs * 0.9) completed.add(state.video.videoId);
            List<String> watched = new ArrayList<>();
            for (LocalVideo video : playlist.videos) if (completed.contains(video.id)) watched.add(video.id);
            post(() -> {
                if (watched.isEmpty()) { MobileSnackbar.show(mActivity, R.string.dreampipe_no_watched); return; }
                new MaterialAlertDialogBuilder(mActivity).setTitle(R.string.dreampipe_remove_watched)
                        .setMessage(mActivity.getString(R.string.dreampipe_remove_watched_confirm, watched.size()))
                        .setNegativeButton(R.string.dreampipe_cancel, null)
                        .setPositiveButton(R.string.dreampipe_delete_item, (d, w) -> mStore.removeVideos(playlist.id, watched,
                                LocalPlaylistActions.result(mActivity, null))).show();
            });
        });
    }

    private void chooseDestination(String exceptId, int title, java.util.function.Consumer<LocalPlaylist> accept) {
        List<LocalPlaylist> choices = new ArrayList<>();
        for (LocalPlaylist playlist : mStore.snapshot()) if (!playlist.id.equals(exceptId)) choices.add(playlist);
        if (choices.isEmpty()) { MobileSnackbar.show(mActivity, R.string.dreampipe_no_other_playlist); return; }
        CharSequence[] names = new CharSequence[choices.size()];
        for (int i = 0; i < choices.size(); i++) names[i] = choices.get(i).name;
        new MaterialAlertDialogBuilder(mActivity).setTitle(title).setItems(names,
                (d, which) -> accept.accept(choices.get(which))).setNegativeButton(R.string.dreampipe_cancel, null).show();
    }

    private void delete(LocalPlaylist playlist) {
        new MaterialAlertDialogBuilder(mActivity).setTitle(mActivity.getString(R.string.dreampipe_delete_title, playlist.name))
                .setMessage(R.string.dreampipe_delete_body).setNegativeButton(R.string.dreampipe_cancel, null)
                .setPositiveButton(R.string.dreampipe_delete, (d, w) -> mStore.remove(playlist.id,
                        LocalPlaylistActions.result(mActivity, ignored -> MobileSnackbar.show(mActivity, R.string.dreampipe_deleted)))).show();
    }

    private void mergeSelection() {
        List<LocalPlaylist> chosen = selectionPlaylists();
        if (chosen.size() < 2) return;
        CharSequence[] labels = new CharSequence[chosen.size()];
        for (int i = 0; i < chosen.size(); i++) labels[i] = chosen.get(i).name;
        int[] destination = {0};
        new MaterialAlertDialogBuilder(mActivity).setTitle(R.string.dreampipe_merge_target)
                .setSingleChoiceItems(labels, 0, (dialog, position) -> destination[0] = position)
                .setNegativeButton(R.string.dreampipe_cancel, null)
                .setPositiveButton(R.string.dreampipe_continue, (dialog, which) -> {
                    LocalPlaylist target = chosen.get(destination[0]);
                    List<LocalPlaylist> sources = new ArrayList<>(chosen); sources.remove(target);
                    mergeInto(target, sources, true);
                }).show();
    }

    private void mergeInto(LocalPlaylist target) {
        List<LocalPlaylist> sources = new ArrayList<>();
        for (LocalPlaylist item : mStore.snapshot()) if (!item.id.equals(target.id)) sources.add(item);
        mergeInto(target, sources, false);
    }

    private void mergeInto(LocalPlaylist target, List<LocalPlaylist> sources, boolean preselect) {
        if (sources.isEmpty()) { MobileSnackbar.show(mActivity, R.string.dreampipe_merge_none); return; }
        CharSequence[] labels = new CharSequence[sources.size()];
        boolean[] checked = new boolean[sources.size()];
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) {
            labels[i] = sources.get(i).name; checked[i] = preselect;
            if (preselect) ids.add(sources.get(i).id);
        }
        android.widget.LinearLayout options = new android.widget.LinearLayout(mActivity);
        options.setOrientation(android.widget.LinearLayout.VERTICAL);
        int padding = Math.round(20 * mActivity.getResources().getDisplayMetrics().density);
        options.setPadding(padding, padding / 2, padding, padding / 2);
        TextView note = new TextView(mActivity); note.setText(R.string.dreampipe_merge_help);
        note.setTextColor(mActivity.getColor(R.color.mobile_color_on_surface_secondary)); options.addView(note);
        android.widget.CheckBox remove = new androidx.appcompat.widget.AppCompatCheckBox(mActivity);
        remove.setText(R.string.dreampipe_merge_delete_sources); remove.setChecked(true);
        options.addView(remove);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(mActivity)
                .setTitle(mActivity.getString(R.string.dreampipe_merge_into, target.name))
                .setMultiChoiceItems(labels, checked, (d, which, value) -> {
                    if (value) ids.add(sources.get(which).id); else ids.remove(sources.get(which).id);
                    ((androidx.appcompat.app.AlertDialog) d).getButton(-1).setEnabled(!ids.isEmpty());
                }).setView(options).setNegativeButton(R.string.dreampipe_cancel, null)
                .setPositiveButton(R.string.dreampipe_merge, null).create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(-1).setEnabled(!ids.isEmpty());
            dialog.getButton(-1).setOnClickListener(v -> {
                dialog.getButton(-1).setEnabled(false); dialog.getButton(-2).setEnabled(false); dialog.setCancelable(false);
                dialog.getListView().setEnabled(false); remove.setEnabled(false);
                mStore.merge(target.id, ids, remove.isChecked(), new LocalPlaylistStore.Result<LocalPlaylist>() {
                    @Override public void onSuccess(LocalPlaylist merged) {
                        dialog.dismiss(); cancelSelection();
                        if (!mReleased) MobileSnackbar.show(mActivity, R.string.dreampipe_merge_done);
                    }
                    @Override public void onError(Exception error) {
                        if (mReleased) { dialog.dismiss(); return; }
                        dialog.setCancelable(true); dialog.getButton(-2).setEnabled(true); dialog.getButton(-1).setEnabled(true);
                        dialog.getListView().setEnabled(true); remove.setEnabled(true);
                        if (!mReleased) MobileSnackbar.show(mActivity, R.string.dreampipe_change_failed);
                    }
                });
            });
        });
        dialog.show();
    }

    private void playPlaylist(boolean shuffle, boolean background) {
        LocalPlaylist playlist = selected();
        if (playlist != null) launch(playlist, 0, shuffle, background);
    }
    private void launch(LocalPlaylist playlist, int index, boolean shuffle, boolean background) {
        if (playlist.videos.isEmpty()) return;
        LocalPlaybackLauncher.play(mActivity, playlist, index, shuffle, background);
    }
    private void playVideo(Video video) {
        LocalPlaylist playlist = selected();
        if (playlist == null) LocalPlaybackLauncher.play(mActivity, video, false);
        else {
            for (int i = 0; i < playlist.videos.size(); i++) if (playlist.videos.get(i).id.equals(video.videoId)) {
                launch(playlist, i, false, false); return;
            }
        }
    }

    private boolean videoActions(Video video) {
        LocalPlaylist playlist = selected();
        actions(video.getTitle(), new int[] {R.string.dreampipe_background, R.string.dreampipe_delete_item,
                R.string.mobile_watch_share, R.string.dreampipe_more_actions}, java.util.Arrays.asList(
                () -> LocalPlaybackLauncher.play(mActivity, video, true),
                () -> confirmRemove(playlist, video), () -> VideoActions.share(mActivity, video),
                () -> moreVideoActions(playlist, video)));
        return true;
    }

    private void confirmRemove(LocalPlaylist playlist, Video video) {
        new MaterialAlertDialogBuilder(mActivity).setTitle(R.string.dreampipe_delete_item)
                .setMessage(mActivity.getString(playlist != null ? R.string.dreampipe_remove_video_confirm
                        : R.string.dreampipe_remove_history_confirm, video.getTitle()))
                .setNegativeButton(R.string.dreampipe_cancel, null)
                .setPositiveButton(R.string.dreampipe_delete_item, (d, w) -> {
                    if (playlist != null) mStore.removeVideo(playlist.id, video.videoId,
                            LocalPlaylistActions.result(mActivity, null));
                    else mHistory.remove(video.videoId);
                }).show();
    }

    private void moreVideoActions(LocalPlaylist playlist, Video video) {
        if (playlist == null) { LocalPlaylistActions.showSave(mActivity, video); return; }
        actions(video.getTitle(), new int[] {R.string.dreampipe_set_thumbnail, R.string.dreampipe_move_to_playlist},
                java.util.Arrays.asList(
                        () -> mStore.setThumbnail(playlist.id, video.videoId, LocalPlaylistActions.result(mActivity, null)),
                        () -> chooseDestination(playlist.id, R.string.dreampipe_move_to_playlist,
                                target -> mStore.moveToPlaylist(playlist.id, target.id, video.videoId,
                                        LocalPlaylistActions.result(mActivity, null)))));
    }

    private void actions(CharSequence title, int[] labelIds, List<Runnable> callbacks) {
        CharSequence[] labels = new CharSequence[labelIds.length];
        for (int i = 0; i < labels.length; i++) labels[i] = mActivity.getString(labelIds[i]);
        new MaterialAlertDialogBuilder(mActivity).setTitle(title).setItems(labels, (d, which) -> callbacks.get(which).run()).show();
    }

    private void chooseExport(LocalPlaylist playlist) {
        new MaterialAlertDialogBuilder(mActivity).setTitle(R.string.dreampipe_export)
                .setItems(new CharSequence[] {mActivity.getString(R.string.dreampipe_export_json), mActivity.getString(R.string.dreampipe_export_m3u)},
                        (d, which) -> prepareExport(Collections.singletonList(playlist), which == 1)).show();
    }

    private void prepareExport(List<LocalPlaylist> playlists, boolean m3u) {
        List<LocalPlaylist> snapshot = new ArrayList<>(playlists);
        IO.execute(() -> {
            try {
                String data = m3u ? PlaylistArchive.encodeM3u(snapshot.get(0)) : PlaylistArchive.encode(snapshot);
                try (FileOutputStream output = new FileOutputStream(mPendingExport)) { output.write(data.getBytes(StandardCharsets.UTF_8)); }
                post(() -> {
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                            .setType(m3u ? "audio/x-mpegurl" : "application/json")
                            .putExtra(Intent.EXTRA_TITLE, m3u ? "DreamPipe-playlist.m3u" : "DreamPipe-playlists.json");
                    try { mExport.launch(intent); }
                    catch (Exception error) { MobileSnackbar.show(mActivity, R.string.dreampipe_export_failed); }
                });
            } catch (Exception error) { post(() -> MobileSnackbar.show(mActivity, R.string.dreampipe_export_failed)); }
        });
    }

    private void readImport(Uri uri) {
        if (uri == null) return;
        IO.execute(() -> {
            try (InputStream input = mActivity.getContentResolver().openInputStream(uri)) {
                if (input == null) throw new java.io.IOException("No readable document");
                String name = "Imported playlist";
                try (android.database.Cursor cursor = mActivity.getContentResolver().query(uri,
                        new String[] {android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        String value = cursor.getString(0);
                        if (value != null) name = value.replaceFirst("(?i)\\.(m3u8?|json)$", "");
                    }
                }
                List<LocalPlaylist> imported = PlaylistArchive.decode(PlaylistArchive.read(input), name, System.currentTimeMillis());
                post(() -> previewImport(imported));
            } catch (Exception error) { post(() -> MobileSnackbar.show(mActivity, R.string.dreampipe_import_failed)); }
        });
    }

    private void previewImport(List<LocalPlaylist> imported) {
        if (mStore.error() != null) {
            new MaterialAlertDialogBuilder(mActivity).setTitle(R.string.dreampipe_restore_backup)
                    .setMessage(R.string.dreampipe_restore_backup_body)
                    .setNegativeButton(R.string.dreampipe_cancel, null)
                    .setPositiveButton(R.string.dreampipe_restore_backup, (d, w) -> mStore.restoreBackup(imported,
                            LocalPlaylistActions.result(mActivity, count -> MobileSnackbar.show(mActivity,
                                    mActivity.getString(R.string.dreampipe_import_done, count))))).show();
            return;
        }
        int count = 0;
        for (LocalPlaylist playlist : imported) count += playlist.videos.size();
        new MaterialAlertDialogBuilder(mActivity).setTitle(mActivity.getString(R.string.dreampipe_import_title, imported.size()))
                .setMessage(mActivity.getString(R.string.dreampipe_import_summary, count))
                .setNegativeButton(R.string.dreampipe_cancel, null)
                .setNeutralButton(R.string.dreampipe_import_copies, (d, w) -> commitImport(imported, false))
                .setPositiveButton(R.string.dreampipe_import_merge, (d, w) -> commitImport(imported, true)).show();
    }
    private void commitImport(List<LocalPlaylist> imported, boolean merge) {
        mStore.importPlaylists(imported, merge, LocalPlaylistActions.result(mActivity,
                count -> MobileSnackbar.show(mActivity, mActivity.getString(R.string.dreampipe_import_done, count))));
    }
    private void post(Runnable task) {
        mMain.post(() -> { if (!mReleased && !mActivity.isFinishing() && !mActivity.isDestroyed()) task.run(); });
    }

    private LocalListAdapter activeRows() { return mPlaylistId == null && !mShowingHistory ? mPlaylists : mVideos; }

    private void beginSelection(Object item) {
        if (mShowingHistory || mSelectionBusy) return;
        mSelecting = true;
        if (item != null) mSelectedIds.add(LocalListAdapter.key(item));
        updateSelectionUi();
    }
    private void toggleSelected(Object item) {
        if (mSelectionBusy) return;
        String key = LocalListAdapter.key(item);
        if (!mSelectedIds.remove(key)) mSelectedIds.add(key);
        updateSelectionUi();
    }
    public void cancelSelection() {
        if (!mSelecting || mSelectionBusy || mReleased) return;
        mSelecting = false; mSelectedIds.clear(); updateSelectionUi();
    }
    private void updateSelectionUi() {
        if (mReleased) return;
        boolean detail = selected() != null;
        mRoot.findViewById(R.id.local_playlist_header).setVisibility(detail && !mSelecting ? View.VISIBLE : View.GONE);
        mPlaylists.selection(mSelecting && !detail && !mShowingHistory, mSelectedIds);
        mVideos.selection(mSelecting && detail, mSelectedIds);
        mRoot.findViewById(R.id.local_tools).setVisibility(mSelecting ? View.GONE : View.VISIBLE);
        mRoot.findViewById(R.id.local_selection).setVisibility(mSelecting ? View.VISIBLE : View.GONE);
        mRoot.findViewById(R.id.local_actions).setVisibility(mShowingHistory ? View.GONE : View.VISIBLE);
        mRoot.findViewById(R.id.local_import).setVisibility(!mShowingHistory && !detail ? View.VISIBLE : View.GONE);
        TextView manage = mRoot.findViewById(R.id.local_manage);
        manage.setVisibility(detail || mShowingHistory ? View.VISIBLE : View.GONE);
        manage.setText(mShowingHistory ? R.string.dreampipe_clear_history : R.string.dreampipe_manage_playlist);
        mRoot.findViewById(R.id.local_create).setVisibility(!mSelecting && !detail && !mShowingHistory ? View.VISIBLE : View.GONE);
        ((TextView) mRoot.findViewById(R.id.local_selection_count)).setText(mActivity.getString(R.string.dreampipe_selected_count, mSelectedIds.size()));
        LocalListAdapter adapter = activeRows(); boolean all = adapter.getItemCount() > 0;
        for (int i = 0; i < adapter.getItemCount(); i++) all &= mSelectedIds.contains(LocalListAdapter.key(adapter.item(i)));
        ((android.widget.CheckBox) mRoot.findViewById(R.id.local_select_shown)).setChecked(all);
        mRoot.findViewById(R.id.local_select_shown).setEnabled(!mSelectionBusy);
        mRoot.findViewById(R.id.local_selection_done).setEnabled(!mSelectionBusy);
        for (int id : new int[] {R.id.local_bulk_download, R.id.local_bulk_export, R.id.local_bulk_delete})
            mRoot.findViewById(id).setEnabled(!mSelectionBusy && !mSelectedIds.isEmpty());
        TextView merge = mRoot.findViewById(R.id.local_bulk_merge);
        merge.setText(detail ? R.string.dreampipe_move_action : mSelectedIds.size() == 1
                ? R.string.dreampipe_rename : R.string.dreampipe_merge);
        merge.setEnabled(!mSelectionBusy && !mSelectedIds.isEmpty());
    }
    private List<LocalPlaylist> selectionPlaylists() {
        List<LocalPlaylist> chosen = new ArrayList<>();
        for (LocalPlaylist playlist : mStore.snapshot()) if (mSelectedIds.contains(playlist.id)) chosen.add(playlist);
        return chosen;
    }
    private List<LocalVideo> selectionVideos() {
        java.util.LinkedHashMap<String, LocalVideo> videos = new java.util.LinkedHashMap<>();
        LocalPlaylist detail = selected();
        if (detail != null) { for (LocalVideo video : detail.videos) if (mSelectedIds.contains(video.id)) videos.put(video.id, video); }
        else for (LocalPlaylist playlist : selectionPlaylists()) for (LocalVideo video : playlist.videos) videos.putIfAbsent(video.id, video);
        return new ArrayList<>(videos.values());
    }
    private void renameSelection() {
        List<LocalPlaylist> chosen = selectionPlaylists();
        if (chosen.size() != 1) return;
        LocalPlaylist playlist = chosen.get(0);
        LocalPlaylistActions.promptName(mActivity, R.string.dreampipe_rename, playlist.name,
                name -> mStore.rename(playlist.id, name, selectionResult()));
    }
    private void exportSelection() {
        List<LocalPlaylist> chosen = selectionPlaylists();
        if (selected() != null) chosen = Collections.singletonList(LocalPlaylist.create(
                mActivity.getString(R.string.dreampipe_selected_videos), System.currentTimeMillis())
                .withVideos(selectionVideos(), System.currentTimeMillis()));
        if (chosen.size() == 1) chooseExport(chosen.get(0)); else if (!chosen.isEmpty()) prepareExport(chosen, false);
    }
    private void deleteSelection() {
        final String playlistId = mPlaylistId;
        final List<String> ids = new ArrayList<>(mSelectedIds);
        if (ids.isEmpty() || mSelectionBusy) return;
        StringBuilder message = new StringBuilder(mActivity.getString(playlistId == null
                ? R.string.dreampipe_delete_selected_body : R.string.dreampipe_remove_video_body));
        List<String> names = new ArrayList<>();
        if (playlistId == null) for (LocalPlaylist playlist : selectionPlaylists()) names.add(playlist.name);
        else for (LocalVideo video : selectionVideos()) names.add(video.title);
        message.append("\n\n");
        for (int i = 0; i < Math.min(names.size(), 8); i++) message.append("• ").append(names.get(i)).append("\n");
        if (names.size() > 8) message.append(mActivity.getString(R.string.dreampipe_more_selected, names.size() - 8));
        new MaterialAlertDialogBuilder(mActivity).setTitle(mActivity.getResources().getQuantityString(playlistId == null
                ? R.plurals.dreampipe_delete_selected_playlists : R.plurals.dreampipe_delete_selected_videos, ids.size(), ids.size()))
                .setMessage(message)
                .setNegativeButton(R.string.dreampipe_cancel, null)
                .setPositiveButton(R.string.dreampipe_delete, (dialog, which) -> {
                    if (playlistId == null) mStore.removePlaylists(ids, selectionResult());
                    else mStore.removeVideos(playlistId, ids, selectionResult());
                }).show();
    }
    private void moveSelectedVideos() {
        final String source = mPlaylistId;
        final List<String> ids = new ArrayList<>(mSelectedIds);
        if (source == null || ids.isEmpty()) return;
        chooseDestination(source, R.string.dreampipe_move_selected,
                target -> mStore.moveVideos(source, target.id, ids, selectionResult()));
    }
    private LocalPlaylistStore.Result<Void> selectionResult() {
        mSelectionBusy = true; updateSelectionUi();
        return new LocalPlaylistStore.Result<Void>() {
            @Override public void onSuccess(Void ignored) {
                mSelectionBusy = false; cancelSelection();
            }
            @Override public void onError(Exception error) {
                mSelectionBusy = false; updateSelectionUi();
                if (!mReleased) MobileSnackbar.show(mActivity, R.string.dreampipe_change_failed);
            }
        };
    }

    private final LocalListAdapter.Host mRowsHost = new LocalListAdapter.Host() {
        @Override public void open(Object item) {
            if (mSelecting) { toggleSelected(item); return; }
            if (item instanceof LocalPlaylist) { mPlaylistId = ((LocalPlaylist) item).id; resetSearch(); }
            else playVideo(LocalVideos.toVideo((LocalVideo) item));
        }
        @Override public void actions(Object item) {
            if (mSelecting) { toggleSelected(item); return; }
            if (item instanceof LocalPlaylist) beginSelection(item);
            else videoActions(LocalVideos.toVideo((LocalVideo) item));
        }
        @Override public void drag(RecyclerView.ViewHolder holder) { mTouchHelper.startDrag(holder); }
        @Override public void reorderAccessibly(Object item) { choosePosition(item); }
    };

    private void choosePosition(Object item) {
        if (mDragging) return;
        LocalListAdapter adapter = (LocalListAdapter) mList.getAdapter();
        if (adapter == null || !adapter.isReorderable()) return;
        int position = -1;
        for (int i = 0; i < adapter.getItemCount(); i++) if (LocalListAdapter.key(adapter.item(i)).equals(LocalListAdapter.key(item))) position = i;
        final int at = position;
        if (at < 0) return;
        actions(mActivity.getString(R.string.dreampipe_sort_manual), new int[] {R.string.dreampipe_move_up, R.string.dreampipe_move_down},
                java.util.Arrays.asList(() -> moveAccessibly(adapter, at, at - 1), () -> moveAccessibly(adapter, at, at + 1)));
    }

    private void moveAccessibly(LocalListAdapter adapter, int from, int to) {
        if (to < 0 || to >= adapter.getItemCount()) return;
        persistMove(LocalListAdapter.key(adapter.item(from)), LocalListAdapter.key(adapter.item(to)), to > from);
    }

    private void persistMove(String id, String anchor, boolean after) { persistMove(mPlaylistId, id, anchor, after); }

    private void persistMove(String playlistId, String id, String anchor, boolean after) {
        LocalPlaylistStore.Result<Void> callback = new LocalPlaylistStore.Result<Void>() {
            @Override public void onSuccess(Void ignored) { render(); }
            @Override public void onError(Exception error) { render(); MobileSnackbar.show(mActivity, R.string.dreampipe_change_failed); }
        };
        if (playlistId == null) mStore.reorderPlaylist(id, anchor, after, callback);
        else mStore.reorderVideo(playlistId, id, anchor, after, callback);
    }

    private final ItemTouchHelper.Callback mDragCallback = new ItemTouchHelper.Callback() {
        @Override public boolean isLongPressDragEnabled() { return false; }
        @Override public boolean isItemViewSwipeEnabled() { return false; }
        @Override public int getMovementFlags(@NonNull RecyclerView list, @NonNull RecyclerView.ViewHolder holder) {
            LocalListAdapter adapter = (LocalListAdapter) list.getAdapter();
            return makeMovementFlags(adapter != null && adapter.isReorderable() ? ItemTouchHelper.UP | ItemTouchHelper.DOWN : 0, 0);
        }
        @Override public void onSelectedChanged(RecyclerView.ViewHolder holder, int state) {
            super.onSelectedChanged(holder, state);
            if (state == ItemTouchHelper.ACTION_STATE_DRAG) {
                mDragging = true; mMovingId = null; mAnchorId = null; mDragPlaylistId = mPlaylistId;
                mQueryGeneration++;
                if (mPendingQuery != null) mPendingQuery.cancel(true);
            }
        }
        @Override public boolean onMove(@NonNull RecyclerView list, @NonNull RecyclerView.ViewHolder source,
                                        @NonNull RecyclerView.ViewHolder target) {
            int from = source.getBindingAdapterPosition(), to = target.getBindingAdapterPosition();
            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false;
            LocalListAdapter adapter = (LocalListAdapter) list.getAdapter();
            if (adapter == null) return false;
            mMovingId = LocalListAdapter.key(adapter.item(from));
            mAnchorId = LocalListAdapter.key(adapter.item(to));
            mMovingAfter = to > from;
            adapter.move(from, to);
            return true;
        }
        @Override public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) {}
        @Override public void clearView(@NonNull RecyclerView list, @NonNull RecyclerView.ViewHolder holder) {
            super.clearView(list, holder);
            mDragging = false;
            if (mMovingId != null && mAnchorId != null) persistMove(mDragPlaylistId, mMovingId, mAnchorId, mMovingAfter);
            else render();
            mMovingId = null; mAnchorId = null;
        }
    };
}
