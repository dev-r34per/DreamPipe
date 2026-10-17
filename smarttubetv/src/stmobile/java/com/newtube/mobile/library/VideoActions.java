package com.newtube.mobile.library;

import android.app.Activity;
import android.content.Intent;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.R;

/** The feed menu has three actions in the same order on every phone list. */
public final class VideoActions {
    private VideoActions() {}

    public static void show(Activity activity, Video video) {
        CharSequence[] labels = {activity.getString(R.string.dreampipe_background),
                activity.getString(R.string.dreampipe_save_to_playlist), activity.getString(R.string.mobile_watch_share)};
        new MaterialAlertDialogBuilder(activity).setTitle(video.getTitle()).setItems(labels, (dialog, which) -> {
            if (which == 0) LocalPlaybackLauncher.play(activity, video, true);
            else if (which == 1) LocalPlaylistActions.showSave(activity, video);
            else share(activity, video);
        }).show();
    }
    public static void share(Activity activity, Video video) {
        Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "https://www.youtube.com/watch?v=" + video.videoId);
        activity.startActivity(Intent.createChooser(share, activity.getString(R.string.mobile_watch_share)));
    }
}
