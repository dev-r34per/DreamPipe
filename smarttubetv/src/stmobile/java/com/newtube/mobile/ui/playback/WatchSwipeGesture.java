package com.newtube.mobile.ui.playback;

/** Direction locks once, so a vertical scroll cannot become a tab swipe halfway through. */
public final class WatchSwipeGesture {
    private final float mSlop;
    private final float mDistance;
    private float mX, mY;
    private long mStarted;
    private int mAxis; // 0 undecided, 1 horizontal, 2 leave to the child/vertical parent

    public WatchSwipeGesture(float slop, float distance) { mSlop = slop; mDistance = distance; }
    public void start(float x, float y, long time, boolean blocked) {
        mX = x; mY = y; mStarted = time; mAxis = blocked ? 2 : 0;
    }
    public void cancel() { mAxis = 2; }
    public boolean move(float x, float y, long time) {
        if (mAxis == 0) {
            float dx = Math.abs(x - mX), dy = Math.abs(y - mY);
            if (time - mStarted >= 350 || dy > mSlop && dy >= dx) mAxis = 2;
            else if (dx > mSlop && dx > dy * 1.7f) mAxis = 1;
        }
        return mAxis == 1;
    }
    public int direction(float x) { return x < mX ? 1 : -1; }
    public int finish(float x) { return mAxis == 1 && Math.abs(x - mX) >= mDistance ? direction(x) : 0; }
}
