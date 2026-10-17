package com.liskovsoft.smartyoutubetv2.common.app.models.data;
/** Host-only boundary fixture; the Android app uses its upstream playlist. */
public final class Playlist {
    private static final Playlist INSTANCE = new Playlist();
    public static Playlist instance() { return INSTANCE; }
    public void clear() {}
}
