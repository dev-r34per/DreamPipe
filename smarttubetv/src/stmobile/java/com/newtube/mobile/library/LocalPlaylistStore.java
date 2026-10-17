package com.newtube.mobile.library;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AtomicFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * One serial transaction queue owns disk and mutation order. A new immutable snapshot is published
 * only AFTER AtomicFile has committed it. A damaged store is reported, never replaced with emptiness.
 */
public final class LocalPlaylistStore {
    public interface Listener { void onPlaylistsChanged(); }
    public interface Result<T> {
        void onSuccess(T value);
        void onError(Exception error);
    }
    private interface Transaction<T> { T apply(List<LocalPlaylist> next) throws Exception; }
    private static volatile LocalPlaylistStore sInstance;
    private final AtomicFile mFile;
    private final ExecutorService mWorker = Executors.newSingleThreadExecutor(r -> new Thread(r, "DreamPipe-library"));
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final List<Listener> mListeners = new CopyOnWriteArrayList<>();
    private volatile List<LocalPlaylist> mSnapshot = Collections.emptyList();
    private volatile Exception mError;
    private volatile boolean mLoaded;

    public static LocalPlaylistStore instance(Context context) {
        if (sInstance == null) {
            synchronized (LocalPlaylistStore.class) {
                if (sInstance == null) sInstance = new LocalPlaylistStore(context.getApplicationContext());
            }
        }
        return sInstance;
    }

    private LocalPlaylistStore(Context context) {
        File directory = new File(context.getFilesDir(), "local_playlists");
        mFile = new AtomicFile(new File(directory, "library.json"));
        mWorker.execute(() -> {
            try {
                if (mFile.getBaseFile().exists() || new File(mFile.getBaseFile() + ".bak").exists()) {
                    try (FileInputStream input = mFile.openRead()) {
                        mSnapshot = PlaylistArchive.decode(PlaylistArchive.read(input), "", System.currentTimeMillis());
                    }
                }
            } catch (Exception error) { mError = error; }
            mLoaded = true;
            mMain.post(this::notifyListeners);
        });
    }

    public List<LocalPlaylist> snapshot() { return mSnapshot; }
    public boolean isLoaded() { return mLoaded; }
    public Exception error() { return mError; }
    public void addListener(Listener listener) { mListeners.add(listener); }
    public void removeListener(Listener listener) { mListeners.remove(listener); }

    public LocalPlaylist find(String id) {
        for (LocalPlaylist playlist : mSnapshot) if (playlist.id.equals(id)) return playlist;
        return null;
    }

    public void read(Result<List<LocalPlaylist>> result) {
        mWorker.execute(() -> mMain.post(() -> {
            if (mError != null) result.onError(mError); else result.onSuccess(mSnapshot);
        }));
    }

    /** The watch page asks this on metadata refresh; scan large libraries off the UI thread. */
    public void containsVideo(String id, Result<Boolean> result) {
        mWorker.execute(() -> {
            boolean present = false;
            for (LocalPlaylist playlist : mSnapshot) {
                for (LocalVideo video : playlist.videos) if (video.id.equals(id)) { present = true; break; }
                if (present) break;
            }
            final boolean found = present;
            mMain.post(() -> { if (mError != null) result.onError(mError); else result.onSuccess(found); });
        });
    }

    /** Apply a save dialog's membership changes as one transaction, without touching new playlists. */
    public void setMembership(LocalVideo video, List<String> knownIds, List<String> selectedIds, Result<Void> result) {
        List<String> known = new ArrayList<>(knownIds);
        List<String> selected = new ArrayList<>(selectedIds);
        transact(items -> {
            for (int i = 0; i < items.size(); i++) {
                LocalPlaylist playlist = items.get(i);
                if (!known.contains(playlist.id)) continue;
                List<LocalVideo> videos = new ArrayList<>(playlist.videos);
                boolean had = videos.contains(video);
                boolean wants = selected.contains(playlist.id);
                if (had == wants) continue;
                if (wants) videos.add(video); else videos.remove(video);
                items.set(i, playlist.withVideos(videos, System.currentTimeMillis()));
            }
            return null;
        }, result);
    }

    public void create(String name, Result<LocalPlaylist> result) {
        createWithVideos(name, Collections.emptyList(), result);
    }

    public void createWithVideos(String name, List<LocalVideo> videos, Result<LocalPlaylist> result) {
        List<LocalVideo> supplied = new ArrayList<>(videos);
        transact(items -> {
            long now = System.currentTimeMillis();
            LocalPlaylist playlist = LocalPlaylist.create(name, now).withVideos(supplied, now);
            items.add(playlist);
            return playlist;
        }, result);
    }

    public void rename(String id, String name, Result<Void> result) {
        transact(items -> {
            int index = indexOf(items, id);
            items.set(index, items.get(index).renamed(name, System.currentTimeMillis()));
            return null;
        }, result);
    }

    public void remove(String id, Result<Void> result) {
        removePlaylists(Collections.singletonList(id), result);
    }

    /** All selected identities are validated before a single commit removes any playlist. */
    public void removePlaylists(List<String> playlistIds, Result<Void> result) {
        java.util.Set<String> ids = new java.util.LinkedHashSet<>(playlistIds);
        transact(items -> {
            for (String id : ids) indexOf(items, id);
            items.removeIf(item -> ids.contains(item.id));
            return null;
        }, result);
    }

    public void addVideos(String id, List<LocalVideo> videos, Result<Void> result) {
        List<LocalVideo> supplied = new ArrayList<>(videos);
        transact(items -> {
            int index = indexOf(items, id);
            LocalPlaylist playlist = items.get(index);
            List<LocalVideo> merged = new ArrayList<>(playlist.videos);
            merged.addAll(supplied);
            items.set(index, playlist.withVideos(merged, System.currentTimeMillis()));
            return null;
        }, result);
    }

    public void removeVideo(String id, String videoId, Result<Void> result) {
        transact(items -> {
            int index = indexOf(items, id);
            LocalPlaylist playlist = items.get(index);
            List<LocalVideo> videos = new ArrayList<>(playlist.videos);
            videos.removeIf(video -> video.id.equals(videoId));
            items.set(index, playlist.withVideos(videos, System.currentTimeMillis()));
            return null;
        }, result);
    }

    /** A position operation by stable identity stays correct while the screen is filtered. */
    public void moveVideo(String id, String videoId, int offset, Result<Void> result) {
        transact(items -> {
            int index = indexOf(items, id);
            LocalPlaylist playlist = items.get(index);
            List<LocalVideo> videos = new ArrayList<>(playlist.videos);
            int from = -1;
            for (int i = 0; i < videos.size(); i++) if (videos.get(i).id.equals(videoId)) { from = i; break; }
            int to = Math.max(0, Math.min(videos.size() - 1, from + offset));
            if (from >= 0 && from != to) videos.add(to, videos.remove(from));
            items.set(index, playlist.withVideos(videos, System.currentTimeMillis()));
            return null;
        }, result);
    }

    public void merge(String targetId, List<String> sourceIds, Result<LocalPlaylist> result) {
        merge(targetId, sourceIds, false, result);
    }

    /** Merge and optional source removal share one write; failure cannot lose a source playlist. */
    public void merge(String targetId, List<String> sourceIds, boolean removeSources, Result<LocalPlaylist> result) {
        java.util.Set<String> ids = new java.util.LinkedHashSet<>(sourceIds);
        ids.remove(targetId);
        transact(items -> {
            if (ids.isEmpty()) throw new IllegalArgumentException("Choose a source playlist");
            int targetIndex = indexOf(items, targetId);
            List<LocalPlaylist> sources = new ArrayList<>();
            for (LocalPlaylist item : items) if (ids.contains(item.id)) sources.add(item);
            for (String id : ids) indexOf(items, id);
            LocalPlaylist merged = items.get(targetIndex).merge(sources, System.currentTimeMillis());
            items.set(targetIndex, merged);
            if (removeSources) items.removeIf(item -> ids.contains(item.id));
            return merged;
        }, result);
    }

    /** Move by identity relative to an anchor, so concurrent additions are never lost. */
    public void reorderPlaylist(String id, String anchorId, boolean after, Result<Void> result) {
        transact(items -> {
            if (id.equals(anchorId)) return null;
            LocalPlaylist moving = items.remove(indexOf(items, id));
            int to = indexOf(items, anchorId) + (after ? 1 : 0);
            items.add(to, moving);
            return null;
        }, result);
    }

    public void reorderVideo(String playlistId, String id, String anchorId, boolean after, Result<Void> result) {
        transact(items -> {
            int index = indexOf(items, playlistId);
            LocalPlaylist playlist = items.get(index);
            List<LocalVideo> videos = new ArrayList<>(playlist.videos);
            int from = videoIndex(videos, id);
            if (id.equals(anchorId)) return null;
            LocalVideo moving = videos.remove(from);
            videos.add(videoIndex(videos, anchorId) + (after ? 1 : 0), moving);
            items.set(index, playlist.withVideos(videos, System.currentTimeMillis()));
            return null;
        }, result);
    }

    /** Both sides commit together. A failed write leaves the source and target unchanged. */
    public void moveToPlaylist(String sourceId, String targetId, String videoId, Result<Void> result) {
        moveVideos(sourceId, targetId, Collections.singletonList(videoId), result);
    }

    public void moveVideos(String sourceId, String targetId, List<String> videoIds, Result<Void> result) {
        java.util.Set<String> ids = new java.util.LinkedHashSet<>(videoIds);
        transact(items -> {
            if (sourceId.equals(targetId)) return null;
            int sourceIndex = indexOf(items, sourceId), targetIndex = indexOf(items, targetId);
            LocalPlaylist source = items.get(sourceIndex), target = items.get(targetIndex);
            for (String id : ids) videoIndex(source.videos, id);
            List<LocalVideo> from = new ArrayList<>(), to = new ArrayList<>(target.videos);
            for (LocalVideo video : source.videos) {
                if (ids.contains(video.id)) to.add(video); else from.add(video);
            }
            long now = System.currentTimeMillis();
            items.set(sourceIndex, source.withVideos(from, now));
            items.set(targetIndex, target.withVideos(to, now));
            return null;
        }, result);
    }

    public void setThumbnail(String playlistId, String videoId, Result<Void> result) {
        transact(items -> {
            int index = indexOf(items, playlistId);
            LocalPlaylist playlist = items.get(index);
            videoIndex(playlist.videos, videoId);
            items.set(index, playlist.withThumbnail(videoId, System.currentTimeMillis()));
            return null;
        }, result);
    }

    public void removeVideos(String playlistId, List<String> videoIds, Result<Void> result) {
        java.util.Set<String> ids = new java.util.HashSet<>(videoIds);
        transact(items -> {
            int index = indexOf(items, playlistId);
            LocalPlaylist playlist = items.get(index);
            List<LocalVideo> videos = new ArrayList<>(playlist.videos);
            videos.removeIf(video -> ids.contains(video.id));
            items.set(index, playlist.withVideos(videos, System.currentTimeMillis()));
            return null;
        }, result);
    }

    private static int videoIndex(List<LocalVideo> videos, String id) {
        for (int i = 0; i < videos.size(); i++) if (videos.get(i).id.equals(id)) return i;
        throw new IllegalArgumentException("Video no longer exists in this playlist");
    }

    /** Imports are additive. Same archive identity is merged only after the user chooses that mode. */
    public void importPlaylists(List<LocalPlaylist> imported, boolean mergeMatching, Result<Integer> result) {
        List<LocalPlaylist> supplied = new ArrayList<>(imported);
        transact(items -> {
            long now = System.currentTimeMillis();
            for (LocalPlaylist source : supplied) {
                int match = -1;
                for (int i = 0; i < items.size(); i++) {
                    if (items.get(i).id.equals(source.id)) { match = i; break; }
                }
                if (mergeMatching && match < 0) {
                    // Names are only a fallback for formats such as M3U, which have no stable IDs.
                    int matches = 0;
                    for (int i = 0; i < items.size(); i++) {
                        if (items.get(i).name.equalsIgnoreCase(source.name)) { match = i; matches++; }
                    }
                    if (matches > 1) match = -1; // Ambiguous names become a new playlist, never a guessed merge.
                }
                if (mergeMatching && match >= 0) {
                    items.set(match, items.get(match).merge(Collections.singletonList(source), now));
                } else {
                    items.add(match >= 0 ? source.copy(now) : source);
                }
            }
            return supplied.size();
        }, result);
    }

    /** Explicit recovery only: retain the unreadable bytes before replacing the damaged store. */
    public void restoreBackup(List<LocalPlaylist> imported, Result<Integer> result) {
        List<LocalPlaylist> supplied = new ArrayList<>(imported);
        transact(items -> { items.clear(); items.addAll(supplied); return supplied.size(); }, result, true);
    }

    private <T> void transact(Transaction<T> transaction, Result<T> result) {
        transact(transaction, result, false);
    }

    private <T> void transact(Transaction<T> transaction, Result<T> result, boolean recovery) {
        mWorker.execute(() -> {
            try {
                if (mError != null && !recovery) throw new java.io.IOException("Local playlist storage needs recovery", mError);
                List<LocalPlaylist> next = new ArrayList<>(mSnapshot);
                T value = transaction.apply(next);
                if (next.size() > PlaylistArchive.MAX_PLAYLISTS) throw new java.io.IOException("Playlist limit reached");
                int count = 0;
                for (LocalPlaylist playlist : next) count += playlist.videos.size();
                if (count > PlaylistArchive.MAX_VIDEOS) throw new java.io.IOException("Video entry limit reached");
                byte[] bytes = PlaylistArchive.encode(next).getBytes(StandardCharsets.UTF_8);
                if (bytes.length > PlaylistArchive.MAX_BYTES) throw new java.io.IOException("Playlist library exceeds 8 MiB");
                if (recovery && mError != null && mFile.getBaseFile().isFile()) {
                    File preserved = new File(mFile.getBaseFile().getParentFile(),
                            "library.unreadable-" + java.util.UUID.randomUUID() + ".json");
                    try (FileInputStream input = new FileInputStream(mFile.getBaseFile());
                         FileOutputStream output = new FileOutputStream(preserved)) {
                        byte[] buffer = new byte[8192];
                        int copied;
                        while ((copied = input.read(buffer)) != -1) output.write(buffer, 0, copied);
                        output.getFD().sync();
                    }
                }
                FileOutputStream output = null;
                try {
                    output = mFile.startWrite();
                    output.write(bytes);
                    output.getFD().sync();
                    mFile.finishWrite(output);
                } catch (Exception error) {
                    if (output != null) mFile.failWrite(output);
                    throw error;
                }
                mSnapshot = Collections.unmodifiableList(next);
                mError = null;
                mMain.post(() -> { notifyListeners(); if (result != null) result.onSuccess(value); });
            } catch (Exception error) {
                mMain.post(() -> { if (result != null) result.onError(error); });
            }
        });
    }

    private static int indexOf(List<LocalPlaylist> items, String id) {
        for (int i = 0; i < items.size(); i++) if (items.get(i).id.equals(id)) return i;
        throw new IllegalArgumentException("Playlist no longer exists");
    }

    private void notifyListeners() { for (Listener listener : mListeners) listener.onPlaylistsChanged(); }
}
