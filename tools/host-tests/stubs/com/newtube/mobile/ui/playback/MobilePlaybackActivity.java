package com.newtube.mobile.ui.playback;
/** Host-only request boundary; real window/service lifecycle is tested with Robolectric. */
public final class MobilePlaybackActivity {
    public static String backgroundVideo;
    public static void requestBackground(String id) { backgroundVideo = id; }
}
