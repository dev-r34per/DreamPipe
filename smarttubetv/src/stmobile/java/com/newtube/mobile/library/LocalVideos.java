package com.newtube.mobile.library;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

/** The only conversion boundary between portable local metadata and the existing engine model. */
public final class LocalVideos {
    private LocalVideos() {}

    public static LocalVideo from(Video video) {
        return new LocalVideo(video.videoId, video.getTitle(), video.getAuthor(),
                video.getCardImageUrl(), video.getDurationMs());
    }

    public static Video toVideo(LocalVideo entry) {
        Video video = Video.from(entry.id);
        video.title = entry.title;
        video.author = entry.author;
        long seconds = entry.durationMs / 1000;
        if (seconds > 0) video.badge = seconds >= 3600
                ? String.format(java.util.Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
                : String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
        video.cardImageUrl = entry.thumbnail.isEmpty()
                ? "https://i.ytimg.com/vi/" + entry.id + "/hqdefault.jpg" : entry.thumbnail;
        return video;
    }
}
