package com.newtube.mobile.ui.playback;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.widget.NestedScrollView;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.liskovsoft.smartyoutubetv2.tv.R;

/** Watch-page composition only. Playback, extraction, comment paging and queueing stay upstream. */
final class DreamWatchPage {
    static final int COMMENTS = 0;
    static final int NEXT = 1;
    static final int DESCRIPTION = 2;
    private static final int[] PAGE_IDS = {R.id.dreampipe_page_comments, R.id.dreampipe_page_next, R.id.dreampipe_page_description};
    private final android.content.SharedPreferences mPreferences;
    private final AppBarLayout mHeader;
    private final View mBody;
    private final View mVideo;
    private final NestedScrollView mNext;
    private final NestedScrollView mDescription;
    private final BottomNavigationView mTabs;
    private final CommentsPanel mComments;
    private final TextView mTitle;
    private final TextView mMeta;
    private final TextView mAuthor;
    private final TextView mDescriptionTitle;
    private final TextView mDescriptionMeta;
    private int mSelected = NEXT;
    private boolean mSwitching;
    private boolean mInitialized;
    private boolean mFullscreen;
    private int mNextScroll;
    private int mDescriptionScroll;

    DreamWatchPage(Activity activity, CommentsPanel comments, Bundle state) {
        mPreferences = activity.getSharedPreferences("dreampipe_watch_page", android.content.Context.MODE_PRIVATE);
        mHeader = activity.findViewById(R.id.dreampipe_video_header);
        mBody = activity.findViewById(R.id.mobile_watch_area);
        ((WatchPagesLayout) mBody).setSwipeListener(delta -> !mFullscreen && mSelected + delta >= COMMENTS
                && mSelected + delta <= DESCRIPTION, delta -> select(mSelected + delta));
        mVideo = activity.findViewById(R.id.mobile_video_area);
        mNext = activity.findViewById(R.id.mobile_watch_scroll);
        mDescription = activity.findViewById(R.id.dreampipe_description_page);
        mTabs = activity.findViewById(R.id.dreampipe_watch_tabs);
        mComments = comments;
        // Keep the seek-bar thumb above the body without adding a sticky/elevated player.
        mHeader.bringToFront();
        mHeader.addOnOffsetChangedListener((AppBarLayout.OnOffsetChangedListener) (header, offset) ->
                header.setClipChildren(header.getTotalScrollRange() > 0 && -offset >= header.getTotalScrollRange()));
        mComments.setEmbedded();
        activity.findViewById(R.id.mobile_watch_expand).setRotation(270f);
        mTitle = activity.findViewById(R.id.mobile_watch_title);
        mMeta = activity.findViewById(R.id.mobile_watch_meta);
        mAuthor = activity.findViewById(R.id.mobile_watch_channel_name);
        mDescriptionTitle = activity.findViewById(R.id.dreampipe_description_title);
        mDescriptionMeta = activity.findViewById(R.id.dreampipe_description_meta);
        mTabs.setOnItemSelectedListener(item -> {
            for (int page = COMMENTS; page <= DESCRIPTION; page++) {
                if (item.getItemId() == PAGE_IDS[page]) { select(page); return true; }
            }
            return false;
        });
        mTabs.setOnItemReselectedListener(item -> {});
        // The header scrolls in response to the pages, never steals a seek or player gesture.
        AppBarLayout.Behavior behavior = new AppBarLayout.Behavior();
        behavior.setDragCallback(new AppBarLayout.Behavior.DragCallback() {
            @Override public boolean canDrag(AppBarLayout appBarLayout) { return false; }
        });
        ((androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams) mHeader.getLayoutParams()).setBehavior(behavior);
        TextWatcher text = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { syncDescriptionHeading(); }
            @Override public void afterTextChanged(Editable s) {}
        };
        mTitle.addTextChangedListener(text); mMeta.addTextChangedListener(text); mAuthor.addTextChangedListener(text);
        syncDescriptionHeading();
        if (state != null) {
            mNextScroll = state.getInt("dreampipe:next_scroll", 0);
            mDescriptionScroll = state.getInt("dreampipe:description_scroll", 0);
        }
        select(state == null ? mPreferences.getInt("tab", NEXT) : state.getInt("dreampipe:watch_tab", NEXT));
    }

    void select(int page) {
        if (mSwitching) return;
        mSwitching = true;
        if (mInitialized && mNext.getVisibility() == View.VISIBLE) mNextScroll = mNext.getScrollY();
        if (mInitialized && mDescription.getVisibility() == View.VISIBLE) mDescriptionScroll = mDescription.getScrollY();
        mSelected = page >= COMMENTS && page <= DESCRIPTION ? page : NEXT;
        mPreferences.edit().putInt("tab", mSelected).apply();
        if (mTabs.getSelectedItemId() != PAGE_IDS[mSelected]) mTabs.setSelectedItemId(PAGE_IDS[mSelected]);
        mNext.setVisibility(mSelected == NEXT ? View.VISIBLE : View.GONE);
        mDescription.setVisibility(mSelected == DESCRIPTION ? View.VISIBLE : View.GONE);
        if (mSelected == COMMENTS) mComments.open(); else mComments.closeImmediately();
        if (mSelected == NEXT) mNext.post(() -> mNext.scrollTo(0, mNextScroll));
        if (mSelected == DESCRIPTION) mDescription.post(() -> mDescription.scrollTo(0, mDescriptionScroll));
        mInitialized = true;
        mSwitching = false;
    }

    void commentsClosed() {
        if (!mSwitching && !mFullscreen && mSelected == COMMENTS) select(NEXT);
    }

    void commentsSourceChanged() {
        if (mSelected == COMMENTS && !mFullscreen) mComments.open();
    }

    void onNewVideo() {
        mNextScroll = 0; mDescriptionScroll = 0;
        mNext.scrollTo(0, 0); mDescription.scrollTo(0, 0);
        mHeader.setExpanded(true, false);
        if (mSelected == COMMENTS && !mFullscreen) mComments.open();
    }

    void save(Bundle state) {
        state.putInt("dreampipe:watch_tab", mSelected);
        state.putInt("dreampipe:next_scroll", mNext.getVisibility() == View.VISIBLE ? mNext.getScrollY() : mNextScroll);
        state.putInt("dreampipe:description_scroll", mDescription.getVisibility() == View.VISIBLE ? mDescription.getScrollY() : mDescriptionScroll);
    }

    void refreshTheme(Activity activity) {
        mTabs.setBackgroundColor(activity.getColor(R.color.mobile_color_background));
        mTabs.setItemTextColor(ContextCompat.getColorStateList(activity, R.color.mobile_bottom_nav_item));
        mTabs.setItemIconTintList(ContextCompat.getColorStateList(activity, R.color.mobile_bottom_nav_item));
    }

    void fullscreen(boolean fullscreen) {
        mFullscreen = fullscreen;
        mTabs.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        mBody.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        ViewGroup.LayoutParams header = mHeader.getLayoutParams();
        header.height = fullscreen ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT;
        mHeader.setLayoutParams(header);
        AppBarLayout.LayoutParams video = (AppBarLayout.LayoutParams) mVideo.getLayoutParams();
        video.setScrollFlags(fullscreen ? 0 : AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL | AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS);
        mVideo.setLayoutParams(video);
        if (fullscreen) mHeader.setExpanded(true, false);
        else select(mSelected);
    }

    private void syncDescriptionHeading() {
        mDescriptionTitle.setText(mTitle.getText());
        mDescriptionMeta.setText(mAuthor.getText() + "\n" + mMeta.getText());
    }
}
