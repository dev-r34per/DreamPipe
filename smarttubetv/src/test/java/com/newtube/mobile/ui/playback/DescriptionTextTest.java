package com.newtube.mobile.ui.playback;

import static org.junit.Assert.*;

import android.app.Application;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, manifest = Config.NONE, sdk = 28)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class DescriptionTextTest {
    @Test public void chapterTimestampsSeekWhileWebLinksRemainLinks() {
        TextView view = new TextView(RuntimeEnvironment.getApplication());
        List<Long> seeks = new ArrayList<>();
        DescriptionText.bind(view, "0:05 Intro\n1:20:03 Main\nhttps://example.com/watch/1:23", seeks::add);
        Spanned text = (Spanned) view.getText();
        ClickableSpan[] links = text.getSpans(0, text.length(), ClickableSpan.class);
        int urls = 0;
        for (ClickableSpan link : links) {
            if (link instanceof URLSpan) urls++;
            else link.onClick(view);
        }
        java.util.Collections.sort(seeks);
        assertEquals(1, urls);
        assertEquals(java.util.Arrays.asList(5000L, 4803000L), seeks);
    }
}
