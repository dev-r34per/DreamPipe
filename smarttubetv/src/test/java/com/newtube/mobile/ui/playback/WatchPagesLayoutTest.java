package com.newtube.mobile.ui.playback;

import static org.junit.Assert.*;
import android.app.Activity;
import android.app.Application;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.SeekBar;
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
import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, manifest = Config.NONE, sdk = 28)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class WatchPagesLayoutTest {
    private ActivityController<Activity> lifecycle;
    private WatchPagesLayout pages;
    private final List<Integer> changes = new ArrayList<>();
    private long time;
    @Before public void setup() {
        lifecycle = Robolectric.buildActivity(Activity.class);
        Activity activity = lifecycle.get(); activity.setTheme(R.style.Theme_NewTube); lifecycle.setup();
        pages = new WatchPagesLayout(activity, null);
        View child = new View(activity); child.setClickable(true);
        pages.addView(child, new FrameLayout.LayoutParams(-1, -1));
        pages.setSwipeListener(delta -> true, changes::add);
        activity.setContentView(pages);
        pages.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY));
        pages.layout(0, 0, 320, 480); time = SystemClock.uptimeMillis();
    }
    @After public void teardown() { lifecycle.pause().stop().destroy(); }
    private void touch(int action, float x, float y, int elapsed) {
        int[] location = new int[2]; pages.getLocationOnScreen(location);
        MotionEvent event = MotionEvent.obtain(time, time + elapsed, action, x + location[0], y + location[1], 0);
        event.offsetLocation(-location[0], -location[1]);
        pages.dispatchTouchEvent(event); event.recycle();
    }
    @Test public void aPageSwipeSelectsOnceButVerticalScrollingDoesNotSelect() {
        touch(MotionEvent.ACTION_DOWN, 240, 200, 0); touch(MotionEvent.ACTION_MOVE, 130, 204, 20); touch(MotionEvent.ACTION_UP, 80, 204, 40);
        assertEquals(java.util.Collections.singletonList(1), changes); changes.clear();
        touch(MotionEvent.ACTION_DOWN, 240, 120, 60); touch(MotionEvent.ACTION_MOVE, 238, 220, 80);
        touch(MotionEvent.ACTION_MOVE, 80, 240, 100); touch(MotionEvent.ACTION_UP, 80, 240, 120);
        assertTrue(changes.isEmpty());
    }
    @Test public void screenEdgesAndSeekControlsAreNotUsedForPageNavigation() {
        touch(MotionEvent.ACTION_DOWN, 2, 200, 0); touch(MotionEvent.ACTION_MOVE, 180, 200, 20); touch(MotionEvent.ACTION_UP, 240, 200, 40);
        assertTrue(changes.isEmpty());
        SeekBar seek = new SeekBar(pages.getContext()); pages.addView(seek, new FrameLayout.LayoutParams(-1, 64));
        pages.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY));
        pages.layout(0, 0, 320, 480);
        touch(MotionEvent.ACTION_DOWN, 220, 32, 60); touch(MotionEvent.ACTION_MOVE, 100, 32, 80); touch(MotionEvent.ACTION_UP, 80, 32, 100);
        assertTrue(changes.isEmpty());
    }
}
