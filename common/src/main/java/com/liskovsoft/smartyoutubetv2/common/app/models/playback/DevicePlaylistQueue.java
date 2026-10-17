package com.liskovsoft.smartyoutubetv2.common.app.models.playback;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Playback session for a device-local playlist. It never invents a YouTube playlist ID or sends
 * a local identity to the extractor. All state lives on the playback/main thread.
 */
public final class DevicePlaylistQueue {
    private static String sId;
    private static String sTitle;
    private static List<Video> sVideos = Collections.emptyList();
    private static int sIndex;
    private static List<Video> sOriginal = Collections.emptyList();
    private static int sMode = PlayerConstants.PLAYBACK_MODE_LIST;

    private DevicePlaylistQueue() {}

    public static Video start(String id, String title, List<Video> videos, int index, boolean shuffle) {
        if (videos.isEmpty()) throw new IllegalArgumentException("Cannot play an empty playlist");
        List<Video> queue = new ArrayList<>();
        for (Video video : videos) {
            Video copy = Video.from(video);
            copy.localPlaylistId = id;
            copy.pendingPosMs = 0;
            copy.finishOnEnded = false;
            // A local entry represents the video, never the remote playlist it was saved from.
            copy.playlistId = null;
            copy.playlistParams = null;
            queue.add(copy);
        }
        sOriginal = Collections.unmodifiableList(new ArrayList<>(queue));
        sMode = shuffle ? PlayerConstants.PLAYBACK_MODE_SHUFFLE : PlayerConstants.PLAYBACK_MODE_LIST;
        if (shuffle) { Collections.shuffle(queue, new Random()); index = 0; }
        sId = id;
        sTitle = title;
        sVideos = Collections.unmodifiableList(queue);
        sIndex = Math.max(0, Math.min(index, queue.size() - 1));
        return sVideos.get(sIndex);
    }

    /** A list button starts a list session, independently of the saved single-video mode. */
    public static int applyPlaybackMode(Video video, int storedMode) { return covers(video) ? sMode : storedMode; }

    /** An explicit player choice takes precedence, and never has to restart the current video. */
    public static void onPlaybackModeSelected(int mode) {
        if (sId == null) return;
        Video current = sVideos.get(sIndex);
        boolean wasShuffled = sMode == PlayerConstants.PLAYBACK_MODE_SHUFFLE;
        sMode = mode;
        if (mode == PlayerConstants.PLAYBACK_MODE_SHUFFLE && !wasShuffled) {
            List<Video> shuffled = new ArrayList<>(sOriginal);
            shuffled.removeIf(video -> video.videoId.equals(current.videoId));
            Collections.shuffle(shuffled, new Random());
            shuffled.add(0, current);
            sVideos = Collections.unmodifiableList(shuffled);
            sIndex = 0;
        } else if (wasShuffled && mode != PlayerConstants.PLAYBACK_MODE_SHUFFLE) {
            sVideos = sOriginal;
            onNewVideo(current);
        }
    }

    public static boolean covers(Video video) {
        return sId != null && video != null && sId.equals(video.localPlaylistId);
    }
    public static void onNewVideo(Video video) {
        if (video == null) return;
        if (!covers(video)) { clear(); return; }
        for (int i = 0; i < sVideos.size(); i++) if (sVideos.get(i).videoId.equals(video.videoId)) { sIndex = i; return; }
        clear();
    }
    public static Video next(Video video) { return covers(video) && sIndex + 1 < sVideos.size() ? sVideos.get(sIndex + 1) : null; }
    public static Video previous(Video video) { return covers(video) && sIndex > 0 ? sVideos.get(sIndex - 1) : null; }
    public static List<Video> videos() { return sVideos; }
    public static String title() { return sTitle; }
    public static int position() { return sIndex; }
    public static void clear() { sId = null; sTitle = null; sVideos = Collections.emptyList(); sOriginal = Collections.emptyList(); sIndex = 0; sMode = PlayerConstants.PLAYBACK_MODE_LIST; }
}
