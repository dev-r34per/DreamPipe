package com.newtube.mobile.ui.common;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.newtube.mobile.ui.browse.NavigationPreferences;

import java.util.ArrayList;
import java.util.List;

/** A view projection: the presenter's full items and continuation-bearing Video objects survive. */
public final class VideoRowLimit {
    private final Context mContext;
    private int mSetting = -1;
    private int mRows;

    public VideoRowLimit(Context context) { mContext = context.getApplicationContext(); }
    public void reset() { mSetting = -1; }
    private int rows() {
        int setting = NavigationPreferences.rowLimit(mContext);
        if (setting != mSetting) { mSetting = setting; mRows = setting; }
        return mRows;
    }
    public void revealMore() {
        int current = rows();
        mRows = Math.min(1_000_000, current + Math.max(1, mSetting));
    }
    public List<Video> visible(List<Video> all, int columns) {
        int count = count(all, columns);
        return new ArrayList<>(all.subList(0, count));
    }
    public boolean hasHidden(List<Video> all, int columns) { return count(all, columns) < all.size(); }
    private int count(List<Video> all, int columns) {
        return RowLimitPolicy.visibleCount(all.size(), columns, rows(), index -> {
            Video item = all.get(index);
            return item.isChannel() && !item.isPlaylistAsChannel();
        });
    }
}
