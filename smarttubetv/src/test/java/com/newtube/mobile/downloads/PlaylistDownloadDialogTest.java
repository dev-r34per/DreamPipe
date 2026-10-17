package com.newtube.mobile.downloads;

import static org.junit.Assert.*;
import java.util.Arrays;
import org.junit.Test;

public class PlaylistDownloadDialogTest {
    private static DownloadOption video(int height) { return new DownloadOption(DownloadOption.KIND_VIDEO, height + "p", height, null, null, -1); }
    @Test public void qualityCapChoosesTheBestAvailableLowerRungWithoutDependingOnOrder() {
        DownloadOption low = video(360), hd = video(720), full = video(1080);
        assertSame(low, PlaylistDownloadDialog.choose(Arrays.asList(full, low, hd), 480));
        assertSame(hd, PlaylistDownloadDialog.choose(Arrays.asList(low, full, hd), 720));
        assertNull(PlaylistDownloadDialog.choose(Arrays.asList(full, hd), 480));
    }
    @Test public void audioChoiceNeverFallsBackToAnUnrequestedVideoDownload() {
        DownloadOption audio = new DownloadOption(DownloadOption.KIND_AUDIO, null, 0, null, null, -1);
        assertSame(audio, PlaylistDownloadDialog.choose(Arrays.asList(video(360), audio), 0));
        assertNull(PlaylistDownloadDialog.choose(Arrays.asList(video(720)), 0));
        assertNull(PlaylistDownloadDialog.choose(Arrays.asList(audio), 720));
    }
}
