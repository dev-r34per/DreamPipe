import com.newtube.mobile.library.LocalPlaylist;
import com.newtube.mobile.library.LocalLibraryQuery;
import com.newtube.mobile.library.LocalVideo;
import com.newtube.mobile.library.PlaylistArchive;
import com.newtube.mobile.ui.browse.NavigationConfig;
import com.newtube.mobile.ui.playback.WatchSwipeGesture;
import com.newtube.mobile.ui.common.RowLimitPolicy;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.DevicePlaylistQueue;

import java.io.ByteArrayInputStream;
import java.util.*;

/** Runs the real portable model/codec/policy code; no Android emulator or APK is needed. */
public final class DreamPipeRegression {
    private static int checks;
    private static final String A = "aaaaaaaaaaa", B = "bbbbbbbbbbb", C = "ccccccccccc";
    private static final String ID1 = "a23f6164-b2cf-4923-b992-639d9d2ee835";
    private static final String ID2 = "1df326ef-f859-4926-b46e-d4b75185ea35";
    interface Throwing { void run() throws Exception; }
    private static void check(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
    private static void rejects(Throwing task, String message) throws Exception {
        checks++;
        try { task.run(); } catch (Exception expected) { return; }
        throw new AssertionError(message);
    }
    private static LocalVideo video(String id) { return new LocalVideo(id, "Video " + id, "Artist", "", 123_000); }
    private static List<String> ids(LocalPlaylist playlist) {
        List<String> result = new ArrayList<>();
        for (LocalVideo video : playlist.videos) result.add(video.id);
        return result;
    }

    public static void main(String[] args) throws Exception {
        check(LocalVideo.idFromUrl("https://youtu.be/" + A + "?t=30").equals(A), "short links");
        check(LocalVideo.idFromUrl("https://www.youtube.com/watch?list=PLfake&v=" + B).equals(B), "watch links");
        check(LocalVideo.idFromUrl("https://m.youtube.com/shorts/" + C).equals(C), "shorts links");
        check(LocalVideo.idFromUrl("https://youtube.com.evil.test/watch?v=" + A) == null, "host suffix spoof");
        check(LocalVideo.idFromUrl("file:///etc/passwd") == null, "local paths cannot be imported as videos");
        check(LocalVideo.idFromUrl("https://www.youtube.com@evil.test/watch?v=" + A) == null, "userinfo spoof");
        check(LocalVideo.idFromUrl("javascript:alert(1)") == null, "active content rejected");
        rejects(() -> new LocalVideo("../../x", "Bad", "", "", 0), "invalid video IDs");
        check(new LocalVideo(A, "A\nB", "", "https://127.0.0.1/private", 0).thumbnail.isEmpty(), "imported arbitrary image URLs discarded");
        check(new LocalVideo(A, "A\nB", "", "", 0).title.equals("A B"), "M3U newline injection removed");

        LocalPlaylist first = new LocalPlaylist(ID1, "日本語 · Night", 10, 20, Arrays.asList(video(A), video(B), video(A)));
        LocalPlaylist second = new LocalPlaylist(ID2, "Second", 15, 30, Arrays.asList(video(B), video(C)));
        check(ids(first).equals(Arrays.asList(A, B)), "stable deduplication");
        LocalPlaylist merged = first.merge(Collections.singletonList(second), 40);
        check(ids(merged).equals(Arrays.asList(A, B, C)), "merge target order then new source entries");
        check(merged.id.equals(first.id) && merged.name.equals(first.name), "merge preserves target identity");
        check(merged.createdAt == 10 && merged.updatedAt == 40, "merge timestamps");
        check(ids(first).equals(Arrays.asList(A, B)) && ids(second).equals(Arrays.asList(B, C)), "merge leaves both sources unchanged");
        check(first.merge(Collections.singletonList(first), 50).videos.size() == 2, "self-merge is idempotent");

        LocalPlaylist cover = first.withThumbnail(B, 55);
        check(cover.thumbnail().id.equals(B), "explicit thumbnail chooses the selected entry");
        check(cover.renamed("Renamed", 56).thumbnailVideoId.equals(B), "rename preserves custom thumbnail");
        check(cover.merge(Collections.singletonList(second), 57).thumbnailVideoId.equals(B), "merge preserves target thumbnail");
        check(cover.copy(58).thumbnailVideoId.equals(B), "copy preserves thumbnail independently of playlist ID");
        check(cover.withVideos(Collections.singletonList(video(A)), 59).thumbnail().id.equals(A), "removing the cover falls back safely");
        LocalPlaylist restoredCover = PlaylistArchive.decode(PlaylistArchive.encode(Collections.singletonList(cover)), "", 60).get(0);
        check(restoredCover.thumbnailVideoId.equals(B), "JSON round trip preserves explicit thumbnail");
        org.json.JSONObject oldArchive = new org.json.JSONObject(PlaylistArchive.encode(Collections.singletonList(cover)));
        oldArchive.getJSONArray("playlists").getJSONObject(0).remove("thumbnailVideoId");
        check(PlaylistArchive.decode(oldArchive.toString(), "", 60).get(0).thumbnailVideoId.isEmpty(), "version-one archives without thumbnail remain readable");
        check(LocalLibraryQuery.playlists(Arrays.asList(second, first), "", 3, 10).items.get(0) == second, "manual playlist projection preserves stored order");
        check(first.copy(50).id.equals(first.id) == false, "copy has independent identity");
        rejects(() -> first.videos.add(video(C)), "immutable snapshots");
        rejects(() -> LocalPlaylist.create(" \n ", 0), "empty names");

        var playlistSearch = LocalLibraryQuery.playlists(Arrays.asList(first, second), " NIGHT ", 0, 10);
        check(playlistSearch.total == 1 && playlistSearch.items.get(0) == first, "playlist search ignores case and boundary whitespace");
        var limited = LocalLibraryQuery.playlists(Arrays.asList(first, second), "", 2, 1);
        check(limited.total == 2 && limited.items.equals(Collections.singletonList(second)), "updated sort before visible row projection");
        check(LocalLibraryQuery.videos(merged.videos, "ARTIST", 0, 2).items.equals(Arrays.asList(video(A), video(B))), "video query includes author and preserves manual order");
        List<LocalVideo> timed = Arrays.asList(new LocalVideo(A, "Zulu", "", "", 1), new LocalVideo(B, "Alpha", "", "", 10));
        check(LocalLibraryQuery.videos(timed, "", 2, 10).items.get(0).id.equals(B), "duration sort descending");
        check(LocalLibraryQuery.videos(timed, "", 1, 10).items.get(0).id.equals(B), "title sort ascending");
        check(timed.get(0).id.equals(A) && first.videos.get(0).id.equals(A), "queries never mutate stored order");
        check(LocalLibraryQuery.videos(timed, "no match", 0, 10).total == 0, "query no matches");

        String json = PlaylistArchive.encode(Arrays.asList(first, second));
        List<LocalPlaylist> restored = PlaylistArchive.decode(json, "ignored", 1);
        check(restored.size() == 2 && restored.get(0).name.equals(first.name), "Unicode JSON round trip");
        check(restored.get(0).id.equals(first.id) && restored.get(0).createdAt == 10, "JSON keeps identities and dates");
        check(ids(restored.get(1)).equals(Arrays.asList(B, C)), "JSON keeps video order");
        check(PlaylistArchive.encode(restored).equals(json), "lossless deterministic JSON re-export");
        check(PlaylistArchive.decode(PlaylistArchive.encode(Collections.emptyList()), "", 0).isEmpty(), "empty library round trip");
        String m3u = PlaylistArchive.encodeM3u(first);
        LocalPlaylist portable = PlaylistArchive.decode(m3u, "fallback", 55).get(0);
        check(portable.name.equals(first.name) && ids(portable).equals(ids(first)), "M3U name and order");
        check(portable.videos.get(0).durationMs == 123_000, "M3U duration");
        check(PlaylistArchive.decode("\uFEFF" + m3u, "", 0).get(0).videos.size() == 2, "UTF-8 BOM");
        check(PlaylistArchive.decode(PlaylistArchive.encodeM3u(LocalPlaylist.create("Empty", 1)), "", 2).get(0).videos.isEmpty(), "empty M3U playlist round trip");
        rejects(() -> PlaylistArchive.decode(json.replace("\"version\": 1", "\"version\": 999"), "", 0), "future schema rejected");
        rejects(() -> PlaylistArchive.decode(json.replace(A, "INVALID"), "", 0), "malformed entries reject entire import");
        rejects(() -> PlaylistArchive.decode(PlaylistArchive.encode(Arrays.asList(first, first)), "", 0), "duplicate archive playlist identities");
        rejects(() -> PlaylistArchive.decode("#EXTM3U\nhttps://evil.test/watch?v=" + A, "", 0), "unsupported M3U provider rejected");
        rejects(() -> PlaylistArchive.decode("[".repeat(20) + "]".repeat(20), "", 0), "hostile deep JSON rejected");
        rejects(() -> PlaylistArchive.read(new ByteArrayInputStream(new byte[PlaylistArchive.MAX_BYTES + 1])), "oversized stream rejected before parsing");

        check(NavigationConfig.parse(null).equals(Arrays.asList(0, NavigationConfig.LOCAL_PLAYLISTS, NavigationConfig.LOCAL_HISTORY)), "three default tabs, no You");
        check(NavigationConfig.parse("4,4,invalid,-1,3").equals(Arrays.asList(4, 3)), "sanitise saved navigation");
        check(NavigationConfig.parse("1,2,3,4,5,6").size() == 4, "four destinations leave room for the permanent Menu");
        check(NavigationConfig.encode(Arrays.asList(3, 0)).equals("3,0"), "custom order preserved");
        rejects(() -> NavigationConfig.encode(Collections.emptyList()), "empty bar rejected");
        rejects(() -> NavigationConfig.encode(Arrays.asList(0, 0)), "duplicate tabs rejected");
        check(NavigationConfig.available(Arrays.asList(999, 0), new HashSet<>(Arrays.asList(0, NavigationConfig.LOCAL_HISTORY))).equals(Collections.singletonList(0)), "missing dynamic destination ignored");
        check(RowLimitPolicy.visibleCount(100, 1, 5, i -> false) == 5, "list row limit");
        check(RowLimitPolicy.visibleCount(100, 2, 5, i -> false) == 10, "grid row limit");
        check(RowLimitPolicy.visibleCount(100, 2, 2, i -> i == 1) == 2, "full-span entry consumes a row after partial row");
        check(RowLimitPolicy.visibleCount(100, 3, 1, i -> i == 0) == 1, "full-span first row");
        check(RowLimitPolicy.visibleCount(100, 1, 0, i -> false) == 100, "unlimited rows");

        rejects(() -> NavigationConfig.encode(Arrays.asList(0, 1, 2, 3, 4)), "Menu capacity cannot be filled by a fifth destination");
        WatchSwipeGesture swipe = new WatchSwipeGesture(12, 56);
        swipe.start(100, 100, 0, false);
        check(!swipe.move(102, 160, 20), "vertical scroll stays with content");
        check(!swipe.move(300, 180, 40) && swipe.finish(300) == 0, "a vertical scroll cannot turn into a page change");
        swipe.start(100, 100, 0, false);
        check(swipe.move(45, 104, 30), "deliberate horizontal motion is recognized");
        check(swipe.finish(30) == 1, "left swipe advances one tab");
        swipe.start(100, 100, 0, false); swipe.move(170, 104, 30);
        check(swipe.finish(170) == -1, "right swipe goes back one tab");
        swipe.start(100, 100, 0, false); swipe.move(145, 100, 30);
        check(swipe.finish(145) == 0, "short movement never changes a tab");
        swipe.start(100, 100, 0, true);
        check(!swipe.move(250, 100, 30) && swipe.finish(250) == 0, "edge and protected-child gestures are untouched");
        swipe.start(100, 100, 0, false);
        check(!swipe.move(250, 100, 400), "long press cannot become a tab swipe");
        swipe.start(100, 100, 0, false); swipe.move(200, 100, 30); swipe.cancel();
        check(swipe.finish(200) == 0, "multitouch and cancellation never select a page");
        swipe.start(100, 100, 0, false); swipe.move(200, 100, 30);
        check(swipe.finish(110) == 0, "returning the drag to its start cancels the page change");

        List<Video> queue = new ArrayList<>();
        for (int i = 0; i < 800; i++) { Video v = new Video(); v.videoId = "video" + i; v.playlistId = "remote"; queue.add(v); }
        Video opening = DevicePlaylistQueue.start(ID1, "Long local playlist", queue, 299, false);
        check(DevicePlaylistQueue.videos().size() == 800, "local queue is not truncated to the legacy 300-item queue");
        check(opening.playlistId == null && opening.localPlaylistId.equals(ID1), "local identity does not become an extractor playlist ID");
        DevicePlaylistQueue.onNewVideo(opening);
        check(DevicePlaylistQueue.applyPlaybackMode(opening, 0) == 5, "Play all overrides global pause-after-video for this session");
        DevicePlaylistQueue.onPlaybackModeSelected(3);
        check(DevicePlaylistQueue.applyPlaybackMode(opening, 0) == 3, "an explicit repeat-one choice overrides the session default");
        DevicePlaylistQueue.onPlaybackModeSelected(5);
        Video next = DevicePlaylistQueue.next(opening);
        check(next.videoId.equals("video300"), "cross legacy queue cap");
        DevicePlaylistQueue.onNewVideo(next);
        check(DevicePlaylistQueue.previous(next).videoId.equals("video299"), "previous follows selected local order");
        check(DevicePlaylistQueue.position() == 300, "queue tracks current index");
        Video last = DevicePlaylistQueue.videos().get(799);
        DevicePlaylistQueue.onNewVideo(last);
        check(DevicePlaylistQueue.next(last) == null, "end of local queue does not become a YouTube recommendation");
        DevicePlaylistQueue.start(ID1, "Shuffled", queue, 0, true);
        check(DevicePlaylistQueue.videos().size() == 800 && new HashSet<>(DevicePlaylistQueue.videos().stream().map(v -> v.videoId).toList()).size() == 800, "shuffle keeps every item exactly once");
        Video shuffledCurrent = DevicePlaylistQueue.videos().get(0);
        DevicePlaylistQueue.onNewVideo(shuffledCurrent);
        String currentId = shuffledCurrent.videoId;
        DevicePlaylistQueue.onPlaybackModeSelected(2);
        check(DevicePlaylistQueue.videos().get(0).videoId.equals("video0"), "turning shuffle off restores original order");
        check(DevicePlaylistQueue.videos().get(DevicePlaylistQueue.position()).videoId.equals(currentId), "turning shuffle off preserves current video");
        Video external = new Video(); external.videoId = "external";
        DevicePlaylistQueue.onNewVideo(external);
        check(DevicePlaylistQueue.videos().isEmpty(), "explicit external open ends local queue session");
        check(DevicePlaylistQueue.applyPlaybackMode(external, 0) == 0, "leaving local playback restores the saved global mode");
        android.content.Context launchContext = new android.content.Context(new java.io.File(System.getProperty("java.io.tmpdir")));
        com.newtube.mobile.library.LocalPlaybackLauncher.play(launchContext, first, 0, false, false);
        Video playAll = com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter.opening;
        check(playAll.videoId.equals(A) && DevicePlaylistQueue.videos().size() == 2, "Play all opens first entry with the entire local queue");
        check(playAll.pendingPosMs == 0 && !playAll.finishOnEnded, "Play all starts from the beginning and continues the queue");
        check(com.newtube.mobile.ui.playback.MobilePlaybackActivity.backgroundVideo == null, "Play all cancels stale background requests");
        com.newtube.mobile.library.LocalPlaybackLauncher.play(launchContext, first, 0, false, true);
        check(com.newtube.mobile.ui.playback.MobilePlaybackActivity.backgroundVideo.equals(A), "background playlist opens audio for the first queued entry");
        Video sourceEntry = new Video(); sourceEntry.videoId = B; sourceEntry.localPlaylistId = ID1; sourceEntry.playlistId = "PLremote";
        com.newtube.mobile.library.LocalPlaybackLauncher.play(launchContext, sourceEntry, true);
        Video single = com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter.opening;
        check(single.videoId.equals(B) && single.localPlaylistId == null && single.playlistId == null, "background row action detaches the entry from either playlist type");
        check(single.finishOnEnded && DevicePlaylistQueue.videos().isEmpty(), "background row action stops after one video");
        check(sourceEntry.localPlaylistId.equals(ID1) && sourceEntry.playlistId.equals("PLremote"), "single-video launch does not mutate its source row");
        check(com.newtube.mobile.ui.playback.MobilePlaybackActivity.backgroundVideo.equals(B), "single-video audio request names the selected entry");
        System.out.println("DreamPipe portable regression checks passed: " + checks);
        LocalStoreRegression.run();
    }
}
