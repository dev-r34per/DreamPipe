package com.newtube.mobile.ui.playback;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.AbsSeekBar;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;

import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

/** Only the detail pages can change tabs; the player, seek bar and system edges stay outside. */
public final class WatchPagesLayout extends FrameLayout {
    private final WatchSwipeGesture mGesture;
    private final float mEdge;
    private IntPredicate mCanSwipe = delta -> false;
    private IntConsumer mSwipe = delta -> {};
    private boolean mClaimed;

    public WatchPagesLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        mGesture = new WatchSwipeGesture(ViewConfiguration.get(context).getScaledTouchSlop() * 1.5f, 56 * density);
        mEdge = 28 * density;
    }
    public void setSwipeListener(IntPredicate canSwipe, IntConsumer swipe) { mCanSwipe = canSwipe; mSwipe = swipe; }
    private int direction(int delta) { return ViewCompat.getLayoutDirection(this) == ViewCompat.LAYOUT_DIRECTION_RTL ? -delta : delta; }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            mClaimed = false;
            mGesture.start(event.getX(), event.getY(), event.getEventTime(),
                    event.getX() < mEdge || event.getX() > getWidth() - mEdge
                            || protectedTarget(this, event.getRawX(), event.getRawY()));
        } else if (event.getPointerCount() != 1 || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            mGesture.cancel();
        } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE
                && mGesture.move(event.getX(), event.getY(), event.getEventTime())) {
            if (!mCanSwipe.test(direction(mGesture.direction(event.getX())))) { mGesture.cancel(); return false; }
            mClaimed = true;
            return true;
        }
        return false;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!mClaimed) return super.onTouchEvent(event);
        if (event.getPointerCount() != 1 || event.getActionMasked() == MotionEvent.ACTION_CANCEL) mGesture.cancel();
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            int delta = direction(mGesture.finish(event.getX()));
            mClaimed = false;
            if (delta != 0 && mCanSwipe.test(delta)) { performClick(); mSwipe.accept(delta); }
        }
        return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }

    private static boolean protectedTarget(View view, float x, float y) {
        if (view.getVisibility() != VISIBLE) return false;
        Rect bounds = new Rect();
        if (!view.getGlobalVisibleRect(bounds) || !bounds.contains((int) x, (int) y)) return false;
        if (view instanceof AbsSeekBar || view instanceof EditText
                || view instanceof TextView && ((TextView) view).isTextSelectable()
                && ((TextView) view).getSelectionStart() >= 0
                && ((TextView) view).getSelectionEnd() != ((TextView) view).getSelectionStart()
                || view.canScrollHorizontally(-1) || view.canScrollHorizontally(1)) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--) if (protectedTarget(group.getChildAt(i), x, y)) return true;
        }
        return false;
    }
}
