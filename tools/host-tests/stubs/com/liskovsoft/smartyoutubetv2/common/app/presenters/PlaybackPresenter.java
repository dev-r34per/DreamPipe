package com.liskovsoft.smartyoutubetv2.common.app.presenters;
import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
/** Records the actual launcher's output instead of starting an Android engine on the host. */
public final class PlaybackPresenter {
    public static Video opening;
    private static final PlaybackPresenter INSTANCE = new PlaybackPresenter();
    public static PlaybackPresenter instance(Context context) { return INSTANCE; }
    public void openVideo(Video video) { opening = video; }
}
