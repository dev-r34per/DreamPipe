package com.newtube.mobile.downloads;

import androidx.activity.ComponentActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.youtubeapi.service.YouTubeMediaItemService;
import com.newtube.mobile.library.LocalVideo;
import com.newtube.mobile.ui.common.MobileSnackbar;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;

/** UI-owned preparation of existing download jobs; no extractor, service or media-path changes. */
public final class PlaylistDownloadDialog {
    private PlaylistDownloadDialog() {}

    public static void show(ComponentActivity activity, List<LocalVideo> supplied) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        LinkedHashMap<String, LocalVideo> unique = new LinkedHashMap<>();
        for (LocalVideo video : supplied) unique.putIfAbsent(video.id, video);
        List<LocalVideo> videos = new ArrayList<>(unique.values());
        if (videos.isEmpty()) { MobileSnackbar.show(activity, R.string.dreampipe_no_videos_download); return; }
        int[] cap = {0, 480, 720, 1080}; int[] selected = {2};
        CharSequence[] labels = {activity.getString(R.string.mobile_download_audio_only),
                activity.getString(R.string.dreampipe_download_video, 480),
                activity.getString(R.string.dreampipe_download_video, 720),
                activity.getString(R.string.dreampipe_download_video, 1080)};
        new MaterialAlertDialogBuilder(activity).setTitle(R.string.dreampipe_download_selection)
                .setView(summary(activity, videos.size()))
                .setSingleChoiceItems(labels, 2, (dialog, which) -> selected[0] = which)
                .setNegativeButton(R.string.dreampipe_cancel, null)
                .setPositiveButton(R.string.dialog_download, (dialog, which) ->
                        new Preparation(activity, videos, cap[selected[0]]).start()).show();
    }

    private static android.widget.TextView summary(ComponentActivity activity, int count) {
        android.widget.TextView note = new android.widget.TextView(activity);
        int padding = Math.round(20 * activity.getResources().getDisplayMetrics().density);
        note.setPadding(padding, padding / 2, padding, padding / 2);
        note.setText(activity.getString(R.string.dreampipe_download_batch_body, count));
        note.setTextColor(activity.getColor(R.color.mobile_color_on_surface_secondary));
        return note;
    }

    @Nullable static DownloadOption choose(List<DownloadOption> options, int cap) {
        DownloadOption best = null;
        for (DownloadOption option : options) {
            if (cap == 0) { if (option.isAudioOnly()) return option; }
            else if (!option.isAudioOnly() && option.height <= cap && (best == null || option.height > best.height)) best = option;
        }
        return best;
    }

    private static final class Preparation implements LifecycleEventObserver {
        final ComponentActivity activity;
        final List<LocalVideo> videos;
        final int cap;
        AlertDialog dialog;
        Disposable work;
        int processed, queued, skipped, failed;
        boolean finished;

        Preparation(ComponentActivity activity, List<LocalVideo> videos, int cap) {
            this.activity = activity; this.videos = videos; this.cap = cap;
        }
        void start() {
            dialog = new MaterialAlertDialogBuilder(activity).setTitle(R.string.dreampipe_download_selection)
                    .setMessage(progress()).setCancelable(false)
                    .setNegativeButton(R.string.dreampipe_download_stop, (d, w) -> {
                        if (!finished) MobileSnackbar.show(activity, R.string.dreampipe_download_batch_stopped);
                    }).create();
            dialog.setOnDismissListener(d -> release());
            dialog.show();
            activity.getLifecycle().addObserver(this);
            work = Observable.fromIterable(videos).concatMap(video -> YouTubeMediaItemService.instance()
                    .getFormatInfoObserve(video.id).subscribeOn(Schedulers.io()).take(1)
                    .map(info -> prepare(video, info)).defaultIfEmpty(new Prepared(video, null))
                    .onErrorReturn(error -> new Prepared(video, null)))
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(this::accept, error -> { failed += videos.size() - processed; complete(); }, this::complete);
        }
        Prepared prepare(LocalVideo video, MediaItemFormatInfo info) {
            if (info == null || info.isUnplayable() || info.isLive()) return new Prepared(video, null);
            DownloadOption option = choose(DownloadOptions.build(info.getAdaptiveFormats(), info.getUrlFormats()), cap);
            return new Prepared(video, option);
        }
        void accept(Prepared prepared) {
            if (finished || activity.isDestroyed()) return;
            processed++;
            DownloadOption option = prepared.option; LocalVideo video = prepared.video;
            try {
                DownloadRegistry registry = DownloadRegistry.instance(activity);
                DownloadItem existing = option == null ? null : registry.findDone(video.id, option.kind);
                if (option == null) failed++;
                else if (registry.findActive(video.id) != null || existing != null &&
                        (option.isAudioOnly() || java.util.Objects.equals(existing.qualityLabel, option.qualityLabel))) skipped++;
                else {
                    String thumbnail = video.thumbnail.startsWith("https://") ? video.thumbnail
                            : "https://i.ytimg.com/vi/" + video.id + "/hqdefault.jpg";
                    DownloadItem item = DownloadItem.create(video.id, video.title.isEmpty() ? video.id : video.title, video.author, thumbnail, option);
                    try { MobileDownloadService.enqueue(activity, item); queued++; }
                    catch (RuntimeException error) {
                        // enqueue records the item before starting the service; never leave a stranded queued job.
                        MobileDownloadService.cancel(activity, item); failed++;
                    }
                }
            } catch (RuntimeException error) { failed++; }
            dialog.setMessage(progress());
        }
        String progress() { return activity.getString(R.string.dreampipe_download_batch_progress, processed, videos.size(), queued, skipped, failed); }
        void complete() {
            if (!dialog.isShowing() || activity.isDestroyed()) return;
            finished = true;
            dialog.setMessage(activity.getString(R.string.dreampipe_download_batch_done, queued, skipped, failed));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setText(R.string.dreampipe_done);
        }
        void release() {
            finished = true;
            if (work != null) work.dispose();
            activity.getLifecycle().removeObserver(this);
        }
        @Override public void onStateChanged(@NonNull LifecycleOwner source, @NonNull Lifecycle.Event event) {
            if (event == Lifecycle.Event.ON_DESTROY) { release(); dialog.dismiss(); }
        }
    }
    private static final class Prepared {
        final LocalVideo video; @Nullable final DownloadOption option;
        Prepared(LocalVideo video, @Nullable DownloadOption option) { this.video = video; this.option = option; }
    }
}
