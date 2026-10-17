package com.newtube.mobile.ui.playback;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.shape.MaterialShapeDrawable;
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

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, manifest = Config.NONE, sdk = 28)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class DreamWatchPageTest {
    private ActivityController<Activity> mLifecycle;
    private Activity mActivity;
    private View mRoot;
    private CommentsPanel mComments;
    private DreamWatchPage mPage;

    @Before public void setUp() {
        mLifecycle = Robolectric.buildActivity(Activity.class);
        mActivity = mLifecycle.get(); mActivity.setTheme(R.style.Theme_NewTube);
        mLifecycle.setup();
        mActivity.getSharedPreferences("dreampipe_watch_page", android.content.Context.MODE_PRIVATE).edit().clear().commit();
        createPage(null);
    }

    private void createPage(Bundle state) {
        mRoot = LayoutInflater.from(mActivity).inflate(R.layout.activity_mobile_playback, null);
        mActivity.setContentView(mRoot);
        View video = mRoot.findViewById(R.id.mobile_video_area);
        ViewGroup.LayoutParams dimensions = video.getLayoutParams(); dimensions.height = 400;
        video.setLayoutParams(dimensions);
        mComments = mock(CommentsPanel.class);
        mPage = new DreamWatchPage(mActivity, mComments, state);
    }

    @After public void tearDown() { mLifecycle.pause().stop().destroy(); }
    private BottomNavigationView tabs() { return mRoot.findViewById(R.id.dreampipe_watch_tabs); }

    @Test public void tabsSelectIndependentPagesAndBackFromCommentsReturnsToNext() {
        assertEquals(3, tabs().getMenu().size());
        assertEquals(R.id.dreampipe_page_next, tabs().getSelectedItemId());
        tabs().setSelectedItemId(R.id.dreampipe_page_description);
        assertEquals(View.VISIBLE, mRoot.findViewById(R.id.dreampipe_description_page).getVisibility());
        assertEquals(View.GONE, mRoot.findViewById(R.id.mobile_watch_scroll).getVisibility());
        tabs().setSelectedItemId(R.id.dreampipe_page_comments);
        verify(mComments).open();
        assertEquals(View.GONE, mRoot.findViewById(R.id.dreampipe_description_page).getVisibility());
        mPage.commentsClosed();
        assertEquals(R.id.dreampipe_page_next, tabs().getSelectedItemId());
        assertEquals(View.VISIBLE, mRoot.findViewById(R.id.mobile_watch_scroll).getVisibility());
    }

    @Test public void rotationHidesTabsAndPreservesTheChosenPage() {
        mPage.select(DreamWatchPage.COMMENTS);
        mPage.fullscreen(true);
        mPage.commentsClosed(); // Suspension is not a user closing comments.
        assertEquals(R.id.dreampipe_page_comments, tabs().getSelectedItemId());
        assertEquals(View.GONE, tabs().getVisibility());
        assertEquals(View.GONE, mRoot.findViewById(R.id.mobile_watch_area).getVisibility());
        mPage.fullscreen(false);
        assertEquals(View.VISIBLE, tabs().getVisibility());
        assertEquals(R.id.dreampipe_page_comments, tabs().getSelectedItemId());
        AppBarLayout.LayoutParams video = (AppBarLayout.LayoutParams) mRoot.findViewById(R.id.mobile_video_area).getLayoutParams();
        assertTrue((video.getScrollFlags() & AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL) != 0);
    }

    @Test public void savedPageSurvivesActivityRecreation() {
        mPage.select(DreamWatchPage.DESCRIPTION);
        Bundle state = new Bundle(); mPage.save(state);
        createPage(state);
        assertEquals(R.id.dreampipe_page_description, tabs().getSelectedItemId());
        assertEquals(View.VISIBLE, mRoot.findViewById(R.id.dreampipe_description_page).getVisibility());
        createPage(null); // Reopening a retained audio session starts a fresh window, not a saved instance.
        assertEquals(R.id.dreampipe_page_description, tabs().getSelectedItemId());
    }

    @Test @Config(qualifiers = "notnight")
    public void watchBarMatchesHomeHeightInsetsAndColorsInLightMode() {
        assertMatchesHome();
    }

    @Test @Config(qualifiers = "night")
    public void watchBarMatchesHomeHeightInsetsAndColorsInDarkMode() {
        assertMatchesHome();
    }

    private void assertMatchesHome() {
        // Inflate both standalone so neither inherits the test window's real system insets.
        View homeLayout = LayoutInflater.from(mActivity).inflate(R.layout.activity_mobile_browse, null);
        BottomNavigationView home = homeLayout.findViewById(R.id.mobile_bottom_nav);
        BottomNavigationView watch = LayoutInflater.from(mActivity)
                .inflate(R.layout.activity_mobile_playback, null).findViewById(R.id.dreampipe_watch_tabs);
        int[] labels = {R.string.header_home, R.string.dreampipe_local_playlists, R.string.dreampipe_history, R.string.dreampipe_menu};
        int[] icons = {R.drawable.ic_nav_home, R.drawable.ic_dreampipe_playlists, R.drawable.ic_nav_history, R.drawable.ic_dreampipe_menu};
        for (int i = 0; i < labels.length; i++) home.getMenu().add(0, i + 1, i, labels[i]).setIcon(icons[i]);
        int baseline = 0;
        for (int bottom : new int[] {0, 24, 48, 0}) {
            WindowInsetsCompat insets = new WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, bottom)).build();
            for (BottomNavigationView bar : new BottomNavigationView[] {home, watch}) {
                ViewCompat.dispatchApplyWindowInsets(bar, insets);
                bar.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.AT_MOST));
            }
            if (baseline == 0) baseline = home.getMeasuredHeight();
            assertTrue(baseline > 0);
            assertEquals("same native height and system navigation inset", home.getMeasuredHeight(), watch.getMeasuredHeight());
            assertEquals("navigation inset is added once and resets without accumulating", baseline + bottom, watch.getMeasuredHeight());
        }
        assertEquals(background(home), background(watch));
        assertEquals(mActivity.getColor(R.color.mobile_color_background), background(watch));
        for (int[] state : new int[][] {{}, {android.R.attr.state_checked}}) {
            assertEquals(home.getItemTextColor().getColorForState(state, 0), watch.getItemTextColor().getColorForState(state, 0));
            assertEquals(home.getItemIconTintList().getColorForState(state, 0), watch.getItemIconTintList().getColorForState(state, 0));
        }
        mPage.refreshTheme(mActivity);
        assertEquals(background(home), background(tabs()));
        assertEquals(R.id.dreampipe_page_next, tabs().getSelectedItemId());
    }

    private int background(View view) {
        if (view.getBackground() instanceof ColorDrawable) return ((ColorDrawable) view.getBackground()).getColor();
        return ((MaterialShapeDrawable) view.getBackground()).getFillColor().getDefaultColor();
    }

    @Test public void scrollingContentCanMoveTheVideoOutOfViewWithoutResizingIt() {
        mRoot.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
        mRoot.layout(0, 0, 1080, 1920);
        CoordinatorLayout coordinator = mRoot.findViewById(R.id.dreampipe_watch_coordinator);
        int tabsTop = tabs().getTop();
        assertTrue("scrolling content must be clipped before the fixed tabs", coordinator.getClipChildren());
        assertFalse("tabs are outside the scrolling coordinator", tabs().getParent() == coordinator);
        AppBarLayout header = mRoot.findViewById(R.id.dreampipe_video_header);
        View scrolling = mRoot.findViewById(R.id.mobile_watch_scroll);
        View body = mRoot.findViewById(R.id.mobile_watch_area);
        AppBarLayout.Behavior behavior = (AppBarLayout.Behavior)
                ((CoordinatorLayout.LayoutParams) header.getLayoutParams()).getBehavior();
        assertNotNull(behavior);
        assertTrue(behavior.onStartNestedScroll(coordinator, header, body, scrolling,
                ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH));
        int[] consumed = new int[2];
        behavior.onNestedPreScroll(coordinator, header, scrolling, 0, header.getTotalScrollRange(),
                consumed, ViewCompat.TYPE_TOUCH);
        assertTrue(consumed[1] > 0);
        assertEquals("tabs stay fixed as the player scrolls away", tabsTop, tabs().getTop());
        assertEquals(-header.getTotalScrollRange(), behavior.getTopAndBottomOffset());
        assertEquals(400, mRoot.findViewById(R.id.mobile_video_area).getHeight());
    }
}
