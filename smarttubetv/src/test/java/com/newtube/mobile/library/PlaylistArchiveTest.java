package com.newtube.mobile.library;

import static org.junit.Assert.*;

import android.app.Application;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, manifest = Config.NONE, sdk = 28)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class PlaylistArchiveTest {
    @Test public void jsonRoundTripPreservesUnicodeIdentityOrderAndTimestamps() throws Exception {
        LocalPlaylist original = LocalPlaylist.create("日本語 · Night", 100).withVideos(Arrays.asList(
                new LocalVideo("aaaaaaaaaaa", "A", "Artist", "", 30_000),
                new LocalVideo("bbbbbbbbbbb", "B", "Artist", "", 60_000)), 200);
        String encoded = PlaylistArchive.encode(Collections.singletonList(original));
        LocalPlaylist restored = PlaylistArchive.decode(encoded, "", 300).get(0);
        assertEquals(original.id, restored.id);
        assertEquals(original.name, restored.name);
        assertEquals(100, restored.createdAt);
        assertEquals(200, restored.updatedAt);
        assertEquals("bbbbbbbbbbb", restored.videos.get(1).id);
        assertEquals(encoded, PlaylistArchive.encode(Collections.singletonList(restored)));
    }

    @Test public void m3uIsPortableAndUsesPublicLinks() throws Exception {
        LocalPlaylist playlist = LocalPlaylist.create("Music", 0).withVideos(Collections.singletonList(
                new LocalVideo("aaaaaaaaaaa", "A", "", "", 62_000)), 1);
        String text = PlaylistArchive.encodeM3u(playlist);
        assertTrue(text.contains("https://www.youtube.com/watch?v=aaaaaaaaaaa"));
        LocalPlaylist restored = PlaylistArchive.decode(text, "fallback", 2).get(0);
        assertEquals("Music", restored.name);
        assertEquals(62_000, restored.videos.get(0).durationMs);
    }

    @Test(expected = IOException.class) public void rejectsUnsupportedM3uEntriesAsWholeImport() throws Exception {
        PlaylistArchive.decode("#EXTM3U\nhttps://youtu.be/aaaaaaaaaaa\nhttps://evil.test/video\n", "", 0);
    }

    @Test(expected = IOException.class) public void rejectsDuplicatePlaylistIdentities() throws Exception {
        LocalPlaylist playlist = LocalPlaylist.create("A", 0);
        PlaylistArchive.decode(PlaylistArchive.encode(Arrays.asList(playlist, playlist)), "", 0);
    }
}
