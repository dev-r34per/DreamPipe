package com.newtube.mobile.ui.playback;

import android.util.SparseIntArray;

import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.ChatReceiver;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.SeekBarSegment;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.newtube.mobile.library.LocalHistoryStore;
import com.newtube.mobile.library.LocalVideos;
import com.newtube.mobile.player.Media3PlayerController;

import androidx.media3.exoplayer.ExoPlayer;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Service-owned PlaybackView after task dismissal. It delegates to the SAME live controller and
 * keeps the shared queue/recovery/SponsorBlock controllers running without retaining a window.
 */
final class BackgroundPlaybackView implements PlaybackView {
    private final MobilePlaybackService mService;
    private final Media3PlayerController mController;
    private ExoPlayer mPlayer;
    private final List<VideoGroup> mSuggestions = new ArrayList<>();
    private final SparseIntArray mButtons = new SparseIntArray();
    private boolean mBlocked;
    private int mResizeMode;

    BackgroundPlaybackView(MobilePlaybackService service, Media3PlayerController controller, ExoPlayer player, PlaybackView previous) {
        mService = service;
        mController = controller;
        mPlayer = player;
        if (previous != null) {
            mResizeMode = previous.getResizeMode();
            for (int index = 0; index < 100; index++) {
                VideoGroup group = previous.getSuggestionsByIndex(index);
                if (group == null) break;
                mSuggestions.add(group);
            }
        }
    }

    void setPlayer(ExoPlayer player) { mPlayer = player; }

    void restoreTo(PlaybackView view) {
        view.setVideo(getVideo());
        for (VideoGroup group : mSuggestions) view.updateSuggestions(group);
        for (int i = 0; i < mButtons.size(); i++) view.setButtonState(mButtons.keyAt(i), mButtons.valueAt(i));
        view.setResizeMode(mResizeMode);
    }

    @Override public boolean isHeadless() { return true; }
    @Override public void setVideo(Video item) {
        mController.setVideo(item);
        if (item != null && !item.incognito && item.videoId != null && item.getTitle() != null) {
            try { LocalHistoryStore.instance(mService).record(LocalVideos.from(item)); }
            catch (IllegalArgumentException ignored) { }
        }
        mService.refreshMetadata();
    }
    @Override public Video getVideo() { return mController.getVideo(); }
    @Override public void finish() { mService.stopDetachedPlayback(); }
    @Override public void finishReally() { mService.stopDetachedPlayback(); }
    @Override public void showBackground(String url) {}
    @Override public void showBackgroundColor(int colorResId) {}
    @Override public void resetPlayerState() { mController.resetPlayerState(); }
    @Override public boolean isEmbed() { return false; }
    @Override public void updateSuggestions(VideoGroup group) {
        if (group == null || group.isEmpty()) return;
        int index = getSuggestionsIndex(group);
        if (index < 0) mSuggestions.add(group); else mSuggestions.set(index, group);
    }
    @Override public void removeSuggestions(VideoGroup group) {
        if (group == null) return;
        int index = getSuggestionsIndex(group);
        if (index >= 0) mSuggestions.remove(index);
    }
    @Override public int getSuggestionsIndex(VideoGroup group) {
        if (group == null) return -1;
        for (int i = 0; i < mSuggestions.size(); i++) if (mSuggestions.get(i).getId() == group.getId()) return i;
        return -1;
    }
    @Override public VideoGroup getSuggestionsByIndex(int index) { return index >= 0 && index < mSuggestions.size() ? mSuggestions.get(index) : null; }
    @Override public void focusSuggestedItem(int index) {}
    @Override public void focusSuggestedItem(Video video) {}
    @Override public void resetSuggestedPosition() {}
    @Override public boolean isSuggestionsEmpty() { return mSuggestions.isEmpty(); }
    @Override public void clearSuggestions() { mSuggestions.clear(); }
    @Override public void showOverlay(boolean show) {}
    @Override public boolean isOverlayShown() { return false; }
    @Override public void showSuggestions(boolean show) {}
    @Override public boolean isSuggestionsShown() { return false; }
    @Override public void showControls(boolean show) {}
    @Override public boolean isControlsShown() { return false; }
    @Override public int getButtonState(int buttonId) { return mButtons.get(buttonId, BUTTON_OFF); }
    @Override public void setButtonState(int buttonId, int buttonState) { mButtons.put(buttonId, buttonState); }
    @Override public void setChannelIcon(String iconUrl) {}
    @Override public void setSeekPreviewTitle(String title) {}
    @Override public void setNextTitle(Video nextVideo) {}
    @Override public void showDebugInfo(boolean show) {}
    @Override public void showSubtitles(boolean show) {}
    @Override public void loadStoryboard() {}
    @Override public void setTitle(String title) { mService.refreshMetadata(); }
    @Override public void showProgressBar(boolean show) {}
    @Override public void setSeekBarSegments(List<SeekBarSegment> segments) {}
    @Override public void updateEndingTime() {}
    @Override public void setChatReceiver(ChatReceiver receiver) {}

    @Override public void prebuildNextSource(MediaItemFormatInfo formatInfo) { mController.prebuildNextSource(formatInfo); }
    @Override public boolean allowsAutomaticSourceRecovery() { return mController.allowsAutomaticSourceRecovery(); }
    @Override public long getMediaReadinessHoldMs() { return mController.getMediaReadinessHoldMs(); }
    @Override public void openSabr(MediaItemFormatInfo formatInfo) { mController.openSabr(formatInfo); }
    @Override public void openDash(MediaItemFormatInfo formatInfo) { mController.openDash(formatInfo); }
    @Override public void openDash(InputStream dashManifest) { mController.openDash(dashManifest); }
    @Override public void openDashUrl(String dashManifestUrl) { mController.openDashUrl(dashManifestUrl); }
    @Override public void openHlsUrl(String hlsPlaylistUrl) { mController.openHlsUrl(hlsPlaylistUrl); }
    @Override public void openUrlList(List<String> urlList) { mController.openUrlList(urlList); }
    @Override public void openHlsVod(MediaItemFormatInfo formatInfo) { mController.openHlsVod(formatInfo); }
    @Override public void openProgressive(MediaItemFormatInfo formatInfo) { mController.openProgressive(formatInfo); }
    @Override public void openMerged(MediaItemFormatInfo formatInfo, String hlsPlaylistUrl) { mController.openMerged(formatInfo, hlsPlaylistUrl); }
    @Override public void openMerged(InputStream dashManifest, String hlsPlaylistUrl) { mController.openMerged(dashManifest, hlsPlaylistUrl); }
    @Override public long getPositionMs() { return mController.getPositionMs(); }
    @Override public void setPositionMs(long positionMs) { mController.setPositionMs(positionMs); }
    @Override public void setResumePositionMs(long positionMs) { mController.seekToResumePosition(positionMs); }
    @Override public long getHistoryPositionMs() { return mController.getHistoryPositionMs(); }
    @Override public long getForbiddenMediaStartMs() { return mController.getMediaRequests().forbiddenStartMs(); }
    @Override public long getLowestServedMediaStartMs() { return mController.getMediaRequests().lowestServedStartMs(); }
    @Override public long getHighestServedMediaStartMs() { return mController.getMediaRequests().highestServedStartMs(); }
    @Override public long getDurationMs() { return mController.getDurationMs(); }
    @Override public void setPlayWhenReady(boolean play) { mController.setPlayWhenReady(play); }
    @Override public boolean getPlayWhenReady() { return mController.getPlayWhenReady(); }
    @Override public boolean isPlaying() { return mController.isPlaying(); }
    @Override public boolean isLoading() { return mController.isLoading(); }
    @Override public List<FormatItem> getVideoFormats() { return mController.getVideoFormats(); }
    @Override public List<FormatItem> getAudioFormats() { return mController.getAudioFormats(); }
    @Override public List<FormatItem> getSubtitleFormats() { return mController.getSubtitleFormats(); }
    @Override public FormatItem getVideoFormat() { return mController.getVideoFormat(); }
    @Override public FormatItem getAudioFormat() { return mController.getAudioFormat(); }
    @Override public FormatItem getSubtitleFormat() { return mController.getSubtitleFormat(); }
    @Override public void setFormat(FormatItem option) { mController.selectFormat(option); }
    @Override public boolean isEngineInitialized() { return mPlayer != null; }
    @Override public void restartEngine() { mService.restartDetachedPlayback(); }
    @Override public void reloadPlayback() { mService.reloadDetachedPlayback(); }
    @Override public void blockEngine(boolean block) { mBlocked = block; }
    @Override public boolean isEngineBlocked() { return mBlocked; }
    @Override public boolean isInPIPMode() { return false; }
    @Override public boolean containsMedia() { return mController.containsMedia(); }
    @Override public void setSpeed(float speed) { mController.setSpeed(speed); }
    @Override public float getSpeed() { return mController.getSpeed(); }
    @Override public float getEffectiveSpeed() { return mController.getEffectiveSpeed(); }
    @Override public void setPitch(float pitch) { mController.setPitch(pitch); }
    @Override public float getPitch() { return mController.getPitch(); }
    @Override public void setVolume(float volume) { mController.setVolume(volume); }
    @Override public float getVolume() { return mController.getVolume(); }
    @Override public void setResizeMode(int mode) { mResizeMode = mode; }
    @Override public int getResizeMode() { return mResizeMode; }
    @Override public void setZoomPercents(int percents) { /* Window-only presentation has no background surface. */ }
    @Override public void setAspectRatio(float ratio) { /* Window-only presentation has no background surface. */ }
    @Override public void setRotationAngle(int angle) { /* Window-only presentation has no background surface. */ }
    @Override public void setVideoFlipEnabled(boolean enabled) { /* Window-only presentation has no background surface. */ }
    @Override public void setVideoGravity(int gravity) { /* Window-only presentation has no background surface. */ }
}
