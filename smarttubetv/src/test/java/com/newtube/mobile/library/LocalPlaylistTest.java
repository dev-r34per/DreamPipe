package com.newtube.mobile.library;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class LocalPlaylistTest {
    private LocalVideo video(String id) { return new LocalVideo(id, id, "", "", 0); }

    @Test public void mergeKeepsTargetOrderAndDoesNotChangeEitherSource() {
        LocalPlaylist target = LocalPlaylist.create("Target", 1).withVideos(
                Arrays.asList(video("aaaaaaaaaaa"), video("bbbbbbbbbbb")), 2);
        LocalPlaylist source = LocalPlaylist.create("Source", 3).withVideos(
                Arrays.asList(video("bbbbbbbbbbb"), video("ccccccccccc")), 4);
        LocalPlaylist merged = target.merge(Collections.singletonList(source), 5);
        assertEquals(target.id, merged.id);
        assertEquals("Target", merged.name);
        assertEquals(3, merged.videos.size());
        assertEquals("aaaaaaaaaaa", merged.videos.get(0).id);
        assertEquals("bbbbbbbbbbb", merged.videos.get(1).id);
        assertEquals("ccccccccccc", merged.videos.get(2).id);
        assertEquals(2, target.videos.size());
        assertEquals(2, source.videos.size());
    }

    @Test public void importingNewEntriesFromSameIdentityStillMerges() {
        LocalPlaylist target = LocalPlaylist.create("Target", 1).withVideos(Collections.singletonList(video("aaaaaaaaaaa")), 2);
        LocalPlaylist updated = target.withVideos(Arrays.asList(video("aaaaaaaaaaa"), video("bbbbbbbbbbb")), 3);
        assertEquals(2, target.merge(Collections.singletonList(updated), 4).videos.size());
    }

    @Test public void copyHasNewIdentity() {
        LocalPlaylist original = LocalPlaylist.create("A", 1);
        assertNotEquals(original.id, original.copy(2).id);
    }

    @Test public void importedLinksCannotSpoofYouTubeOrLoadArbitraryThumbnails() {
        assertNull(LocalVideo.idFromUrl("https://youtube.com.evil.test/watch?v=aaaaaaaaaaa"));
        assertNull(LocalVideo.idFromUrl("file:///private/file"));
        assertEquals("aaaaaaaaaaa", LocalVideo.idFromUrl("https://youtu.be/aaaaaaaaaaa?t=12"));
        assertEquals("", new LocalVideo("aaaaaaaaaaa", "A", "", "https://localhost/image", 0).thumbnail);
    }
}
