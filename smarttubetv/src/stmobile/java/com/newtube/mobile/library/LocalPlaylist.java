package com.newtube.mobile.library;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/** Immutable playlist snapshot. Its identity is independent of its name and of YouTube playlists. */
public final class LocalPlaylist {
    public final String id;
    public final String name;
    public final long createdAt;
    public final long updatedAt;
    public final List<LocalVideo> videos;
    /** An entry identity, never an arbitrary file or remote URL. Empty means use the first entry. */
    public final String thumbnailVideoId;

    public LocalPlaylist(String id, String name, long createdAt, long updatedAt, List<LocalVideo> videos) {
        this(id, name, createdAt, updatedAt, videos, "");
    }

    public LocalPlaylist(String id, String name, long createdAt, long updatedAt, List<LocalVideo> videos,
                         String thumbnailVideoId) {
        this.id = UUID.fromString(id).toString();
        this.name = LocalVideo.clean(name, 200);
        if (this.name.isEmpty()) throw new IllegalArgumentException("Playlist name is empty");
        this.createdAt = Math.max(0, createdAt);
        this.updatedAt = Math.max(this.createdAt, updatedAt);
        LinkedHashMap<String, LocalVideo> unique = new LinkedHashMap<>();
        for (LocalVideo video : videos) {
            if (video == null) throw new IllegalArgumentException("Playlist contains an empty entry");
            unique.putIfAbsent(video.id, video);
        }
        this.videos = Collections.unmodifiableList(new ArrayList<>(unique.values()));
        this.thumbnailVideoId = unique.containsKey(thumbnailVideoId) ? thumbnailVideoId : "";
    }

    public static LocalPlaylist create(String name, long now) {
        return new LocalPlaylist(UUID.randomUUID().toString(), name, now, now, Collections.emptyList());
    }

    public LocalPlaylist renamed(String name, long now) {
        return new LocalPlaylist(id, name, createdAt, now, videos, thumbnailVideoId);
    }

    public LocalPlaylist withVideos(List<LocalVideo> items, long now) {
        return new LocalPlaylist(id, name, createdAt, now, items, thumbnailVideoId);
    }

    public LocalPlaylist withThumbnail(String videoId, long now) {
        return new LocalPlaylist(id, name, createdAt, now, videos, videoId);
    }

    public LocalVideo thumbnail() {
        for (LocalVideo video : videos) if (video.id.equals(thumbnailVideoId)) return video;
        return videos.isEmpty() ? null : videos.get(0);
    }

    /** Target order wins; previously unseen videos from each source are appended in source order. */
    public LocalPlaylist merge(List<LocalPlaylist> sources, long now) {
        List<LocalVideo> merged = new ArrayList<>(videos);
        for (LocalPlaylist source : sources) {
            merged.addAll(source.videos);
        }
        return withVideos(merged, now);
    }

    public LocalPlaylist copy(long now) {
        return new LocalPlaylist(UUID.randomUUID().toString(), name, now, now, videos, thumbnailVideoId);
    }
}
