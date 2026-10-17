package com.newtube.mobile.library;

import android.app.Activity;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.liskovsoft.mediaserviceinterfaces.data.PlaylistInfo;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.newtube.mobile.ui.common.MobileSnackbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/** Phone-only picker. Local and account destinations share a view, never a storage identity. */
public final class PlaylistSaveDialog implements DefaultLifecycleObserver, LocalPlaylistStore.Listener {
    private final Activity mActivity;
    private final Video mVideo;
    private final LocalVideo mEntry;
    private final LocalPlaylistStore mStore;
    public interface Listener {
        /** A null account value means its list is still loading or unavailable. */
        void onMembershipChanged(boolean local, Boolean account);
    }
    private final Listener mChanged;
    private final AccountPlaylistClient mAccount;
    private final List<Target> mTargets = new ArrayList<>();
    private final CompositeDisposable mRequests = new CompositeDisposable();
    private final TargetAdapter mAdapter = new TargetAdapter();
    private final AlertDialog mDialog;
    private final TextView mStatus;
    private boolean mClosed;
    private boolean mRemoteLoading;
    private boolean mRemoteFailed;
    private int mSaving;

    private static final class Target {
        final String id;
        final String title;
        final String image;
        final int count;
        final boolean local;
        boolean selected;
        boolean saving;
        Target(String id, String title, String image, int count, boolean local, boolean selected) {
            this.id = id; this.title = title; this.image = image; this.count = count;
            this.local = local; this.selected = selected;
        }
    }

    public static void show(Activity activity, Video video, Listener changed) {
        if (video == null || video.videoId == null || activity.isFinishing()) return;
        try { new PlaylistSaveDialog(activity, video, changed, LocalPlaylistStore.instance(activity),
                AccountPlaylistClient.existing(activity)).show(); }
        catch (IllegalArgumentException error) { MobileSnackbar.show(activity, R.string.dreampipe_change_failed); }
    }

    PlaylistSaveDialog(Activity activity, Video video, Listener changed,
                       LocalPlaylistStore store, AccountPlaylistClient account) {
        mActivity = activity;
        // A reload can mutate the player model while the picker is open. Save this snapshot.
        mVideo = Video.from(video);
        mEntry = LocalVideos.from(video);
        mChanged = changed;
        mStore = store;
        mAccount = account;
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        mStatus = new TextView(activity);
        int inset = Math.round(20 * activity.getResources().getDisplayMetrics().density);
        mStatus.setPadding(inset, inset / 2, inset, inset / 2);
        mStatus.setTextColor(activity.getColor(R.color.mobile_color_on_surface_secondary));
        mStatus.setOnClickListener(v -> { if (mRemoteFailed) loadRemote(); });
        content.addView(mStatus);
        RecyclerView list = new RecyclerView(activity);
        list.setLayoutManager(new LinearLayoutManager(activity));
        list.setAdapter(mAdapter);
        list.setItemAnimator(null);
        int height = Math.min(Math.round(336 * activity.getResources().getDisplayMetrics().density),
                activity.getResources().getDisplayMetrics().heightPixels / 2);
        content.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
        mDialog = new MaterialAlertDialogBuilder(activity).setTitle(R.string.dreampipe_save_to_playlist)
                .setView(content).setNeutralButton(R.string.dreampipe_create, null)
                .setPositiveButton(R.string.dreampipe_done, null).create();
        mDialog.setOnDismissListener(d -> release());
    }

    void show() {
        mDialog.show();
        mDialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(v -> create());
        mDialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> mDialog.dismiss());
        if (mActivity instanceof LifecycleOwner) {
            ((LifecycleOwner) mActivity).getLifecycle().addObserver(this);
        }
        mStore.addListener(this);
        mStore.read(new LocalPlaylistStore.Result<List<LocalPlaylist>>() {
            @Override public void onSuccess(List<LocalPlaylist> value) { refreshLocal(); }
            @Override public void onError(Exception error) { if (!mClosed) updateStatus(); }
        });
        if (mAccount.signedIn()) loadRemote();
        updateStatus();
    }

    @Override public void onPlaylistsChanged() { refreshLocal(); }

    private void refreshLocal() {
        if (mClosed) return;
        List<Target> remote = new ArrayList<>();
        for (Target target : mTargets) if (!target.local) remote.add(target);
        List<Target> local = new ArrayList<>();
        for (LocalPlaylist playlist : mStore.snapshot()) {
            Target pending = null;
            for (Target target : mTargets) {
                if (target.local && target.id.equals(playlist.id) && target.saving) pending = target;
            }
            LocalVideo cover = playlist.thumbnail();
            local.add(pending != null ? pending : new Target(playlist.id, playlist.name,
                    cover == null ? null : LocalVideos.toVideo(cover).getCardImageUrl(),
                    playlist.videos.size(), true, playlist.videos.contains(mEntry)));
        }
        mTargets.clear(); mTargets.addAll(local); mTargets.addAll(remote);
        mAdapter.notifyDataSetChanged();
        updateStatus();
        dispatchMembership();
    }

    private void dispatchMembership() {
        if (mChanged == null || mClosed) return;
        boolean local = false, account = false;
        for (Target target : mTargets) if (target.selected) {
            if (target.local) local = true; else account = true;
        }
        mChanged.onMembershipChanged(local, mRemoteLoading || mRemoteFailed ? null : account);
    }

    private void loadRemote() {
        if (mClosed || mRemoteLoading) return;
        mRemoteLoading = true; mRemoteFailed = false; updateStatus();
        mRequests.add(mAccount.playlists(mVideo.videoId)
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(infos -> {
                    if (mClosed) return;
                    mRemoteLoading = false;
                    mTargets.removeIf(target -> !target.local);
                    if (infos != null) for (PlaylistInfo info : infos) {
                        if (info.getPlaylistId() != null) mTargets.add(new Target(info.getPlaylistId(),
                                info.getTitle(), null, info.getSize(), false, info.isSelected()));
                    }
                    mAdapter.notifyDataSetChanged(); updateStatus(); dispatchMembership();
                }, error -> {
                    if (mClosed) return;
                    mRemoteLoading = false; mRemoteFailed = true; updateStatus();
                }));
    }

    private void toggle(Target target) {
        if (mClosed || target.saving) return;
        boolean add = !target.selected;
        target.saving = true; mSaving++;
        updateStatus(); mAdapter.notifyDataSetChanged();
        if (target.local) {
            mStore.setMembership(mEntry, Collections.singletonList(target.id),
                    add ? Collections.singletonList(target.id) : Collections.emptyList(),
                    new LocalPlaylistStore.Result<Void>() {
                        @Override public void onSuccess(Void ignored) { finish(target, add, true); }
                        @Override public void onError(Exception error) { finish(target, add, false); }
                    });
        } else {
            Observable<Void> mutation = mAccount.membership(target.id, mVideo.videoId, add);
            mRequests.add(mutation.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                    .subscribe(ignored -> {}, error -> finish(target, add, false),
                            () -> { mAccount.changed(); finish(target, add, true); }));
        }
    }

    private void finish(Target target, boolean selected, boolean success) {
        target.saving = false; mSaving--;
        if (mClosed) return;
        if (success) {
            target.selected = selected;
            dispatchMembership();
        } else MobileSnackbar.show(mActivity, R.string.dreampipe_playlist_update_failed);
        refreshLocal();
    }

    private void create() {
        LocalPlaylistActions.promptName(mActivity, R.string.dreampipe_create, "", name -> {
            mStore.createWithVideos(name, Collections.singletonList(mEntry),
                    LocalPlaylistActions.result(mActivity, playlist -> {
                        dispatchMembership();
                        MobileSnackbar.show(mActivity, mActivity.getString(R.string.dreampipe_add_done, playlist.name));
                    }));
        });
    }

    private void updateStatus() {
        if (mClosed) return;
        mDialog.setCancelable(mSaving == 0);
        if (mDialog.isShowing()) {
            mDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(mSaving == 0);
            mDialog.getButton(DialogInterface.BUTTON_NEUTRAL).setEnabled(mSaving == 0 && mStore.error() == null);
        }
        int message = mStore.error() != null ? R.string.dreampipe_library_error
                : mSaving > 0 ? R.string.dreampipe_saving
                : mRemoteFailed ? R.string.dreampipe_account_playlists_retry
                : mRemoteLoading ? R.string.dreampipe_account_playlists_loading
                : mTargets.isEmpty() ? R.string.dreampipe_empty_playlists_body : R.string.dreampipe_picker_hint;
        mStatus.setText(message);
        mStatus.setClickable(mRemoteFailed);
    }

    @Override public void onDestroy(@NonNull LifecycleOwner owner) { mDialog.dismiss(); release(); }
    private void release() {
        if (mClosed) return;
        mClosed = true; mStore.removeListener(this); mRequests.dispose();
        if (mActivity instanceof LifecycleOwner) ((LifecycleOwner) mActivity).getLifecycle().removeObserver(this);
    }

    private final class TargetAdapter extends RecyclerView.Adapter<TargetHolder> {
        @NonNull @Override public TargetHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new TargetHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_playlist_target, parent, false));
        }
        @Override public int getItemCount() { return mTargets.size(); }
        @Override public void onBindViewHolder(@NonNull TargetHolder holder, int position) { holder.bind(mTargets.get(position)); }
        @Override public void onViewRecycled(@NonNull TargetHolder holder) { Glide.with(mActivity).clear(holder.image); }
    }

    private final class TargetHolder extends RecyclerView.ViewHolder {
        final ImageView image;
        final CheckBox check;
        TargetHolder(View view) {
            super(view); image = view.findViewById(R.id.target_thumbnail); check = view.findViewById(R.id.target_checked);
        }
        void bind(Target target) {
            ((TextView) itemView.findViewById(R.id.target_title)).setText(target.title);
            String location = mActivity.getString(target.local ? R.string.dreampipe_on_device : R.string.dreampipe_youtube);
            ((TextView) itemView.findViewById(R.id.target_subtitle)).setText(target.saving
                    ? mActivity.getString(R.string.dreampipe_saving) : location + (target.count < 0 ? "" : " · "
                    + mActivity.getResources().getQuantityString(R.plurals.dreampipe_video_count, target.count, target.count)));
            check.setChecked(target.selected);
            ViewCompat.setAccessibilityDelegate(itemView, new AccessibilityDelegateCompat() {
                @Override public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull AccessibilityNodeInfoCompat info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setClassName(CheckBox.class.getName());
                    info.setCheckable(true); info.setChecked(target.selected);
                }
            });
            itemView.setEnabled(!target.saving);
            itemView.setOnClickListener(v -> toggle(target));
            itemView.setContentDescription(target.title + ", " + location + ", " + mActivity.getString(
                    target.selected ? R.string.mobile_watch_saved : R.string.mobile_watch_save));
            Glide.with(mActivity).load(target.image).placeholder(R.drawable.ic_dreampipe_playlists)
                    .centerCrop().into(image);
        }
    }
}
