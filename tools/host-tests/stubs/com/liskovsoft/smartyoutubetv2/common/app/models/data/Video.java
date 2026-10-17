package com.liskovsoft.smartyoutubetv2.common.app.models.data;

/** Host-only type fixture: this directory is never an Android source set. */
public class Video {
    public String videoId;
    public String localPlaylistId;
    public String playlistId;
    public String playlistParams;
    public String title, author, cardImageUrl, badge;
    public long pendingPosMs = -1;
    public boolean finishOnEnded;
    public static Video from(String id) { Video video = new Video(); video.videoId = id; return video; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getCardImageUrl() { return cardImageUrl; }
    public long getDurationMs() { return 0; }
    public static Video from(Video original) {
        Video copy = new Video();
        copy.videoId = original.videoId;
        copy.localPlaylistId = original.localPlaylistId;
        copy.playlistId = original.playlistId;
        copy.playlistParams = original.playlistParams;
        copy.title = original.title; copy.author = original.author; copy.cardImageUrl = original.cardImageUrl;
        copy.badge = original.badge;
        // Like the upstream copy helper, transient pending-position/finish flags are not copied.
        return copy;
    }
}
