package com.newtube.mobile.ui.playback;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;

import androidx.activity.BackEventCompat;

import com.liskovsoft.smartyoutubetv2.tv.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.LooperMode;

import java.time.Duration;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, manifest = Config.NONE, sdk = 28)
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.PAUSED)
public class CommentsPanelLayoutTest {
    private ActivityController<Activity> mLifecycle;
    private CommentsPanelLayout mPanel;
    private CommentsPanelLayout.Callback mCallback;

    @Before public void setUp() {
        mLifecycle = Robolectric.buildActivity(Activity.class);
        Activity activity = mLifecycle.get();
        activity.setTheme(R.style.Theme_NewTube);
        mLifecycle.setup();
        mPanel = (CommentsPanelLayout) LayoutInflater.from(activity).inflate(R.layout.mobile_comments_panel, null);
        activity.setContentView(mPanel);
        mPanel.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY));
        mPanel.layout(0, 0, 1080, 1000);
        mCallback = mock(CommentsPanelLayout.Callback.class);
        mPanel.setCallback(mCallback);
        mPanel.open();
        settle();
    }

    @After public void tearDown() {
        mPanel.closeImmediately();
        mLifecycle.pause().stop().destroy();
    }

    private static BackEventCompat event(float progress) {
        BackEventCompat event = mock(BackEventCompat.class);
        when(event.getProgress()).thenReturn(progress);
        return event;
    }

    private static void settle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
    }

    private void assertResting() {
        assertTrue(mPanel.isOpen());
        assertEquals(View.VISIBLE, mPanel.getVisibility());
        assertEquals(0f, mPanel.getSheet().getTranslationY(), 0.01f);
        assertEquals(1f, mPanel.getSheet().getScaleX(), 0.001f);
        assertEquals(1f, mPanel.getSheet().getScaleY(), 0.001f);
    }

    @Test public void cancelledBackRestoresTheOpenSheetWithoutClosingIt() {
        mPanel.startBackProgress(event(0f));
        mPanel.updateBackProgress(event(0.8f));
        assertTrue(mPanel.getSheet().getScaleX() < 1f);
        assertTrue(mPanel.getSheet().getTranslationY() > 0f);
        mPanel.cancelBackProgress();
        settle();
        assertResting();
        verify(mCallback, never()).onPanelClosed();
    }

    @Test public void committedBackClosesOnceAndHonorsAQueuedReopen() {
        mPanel.startBackProgress(event(0.5f));
        mPanel.handleBack(event(0.5f));
        mPanel.open();
        settle();
        assertResting();
        verify(mCallback).onPanelClosed();
        verify(mCallback, times(2)).onPanelOpened();
    }

    @Test public void immediateCloseCancelsTheOldFinishBeforeANewOpen() {
        mPanel.startBackProgress(event(0.6f));
        mPanel.handleBack(event(0.6f));
        mPanel.closeImmediately();
        mPanel.open();
        settle();
        assertResting();
        verify(mCallback).onPanelClosed();
        verify(mCallback, times(2)).onPanelOpened();
    }

    @Test public void suspensionDuringACancelRestoresTheSheetOnResume() {
        mPanel.startBackProgress(event(0.5f));
        mPanel.cancelBackProgress();
        mPanel.suspend();
        settle();
        assertEquals(View.INVISIBLE, mPanel.getVisibility());
        mPanel.resume();
        assertResting();
        verify(mCallback, never()).onPanelClosed();
    }

    @Test public void embeddedCommentsIgnoreGestureTransformsAndCloseImmediately() {
        mPanel.closeImmediately();
        reset(mCallback);
        mPanel.setEmbedded();
        mPanel.open();
        mPanel.startBackProgress(event(0.8f));
        mPanel.updateBackProgress(event(0.8f));
        assertResting();
        mPanel.handleBack(event(0.8f));
        assertFalse(mPanel.isOpen());
        assertEquals(View.INVISIBLE, mPanel.getVisibility());
        verify(mCallback).onPanelClosed();
    }
}
