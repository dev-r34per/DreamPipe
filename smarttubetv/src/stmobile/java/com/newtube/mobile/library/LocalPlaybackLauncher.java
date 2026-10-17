package com.newtube.mobile.library;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.DevicePlaylistQueue;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.newtube.mobile.ui.playback.MobilePlaybackActivity;

import java.util.ArrayList;
import java.util.List;

/** Opens the existing player. Local queue IDs stay outside the YouTube API contract. */
public final class LocalPlaybackLauncher {
    private LocalPlaybackLauncher() {}

    public static void play(Context context, LocalPlaylist playlist, int index, boolean shuffle, boolean background) {
        List<Video> videos = new ArrayList<>();
        for (LocalVideo video : playlist.videos) {
            Video entry = LocalVideos.toVideo(video);
            entry.pendingPosMs = 0; // A playlist action starts entries from the beginning.
            videos.add(entry);
        }
        if (videos.isEmpty()) return;
        Playlist.instance().clear();
        Video opening = DevicePlaylistQueue.start(playlist.id, playlist.name, videos, index, shuffle);
        MobilePlaybackActivity.requestBackground(background ? opening.videoId : null);
        PlaybackPresenter.instance(context).openVideo(opening);
    }

    public static void play(Context context, Video video, boolean background) {
        if (video == null || video.videoId == null) return;
        DevicePlaylistQueue.clear();
        Playlist.instance().clear();
        Video opening = Video.from(video);
        opening.localPlaylistId = null;
        if (background) {
            // This action is for ONE video, regardless of which playlist the card came from.
            opening.playlistId = null;
            opening.playlistParams = null;
            opening.finishOnEnded = true;
        }
        MobilePlaybackActivity.requestBackground(background ? opening.videoId : null);
        PlaybackPresenter.instance(context).openVideo(opening);
    }
}
