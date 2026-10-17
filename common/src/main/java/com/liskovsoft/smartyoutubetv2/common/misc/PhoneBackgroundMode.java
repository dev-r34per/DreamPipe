package com.liskovsoft.smartyoutubetv2.common.misc;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerConstants;

/** DreamPipe defaults to audio. Only an explicit PiP preference opens a floating window. */
public final class PhoneBackgroundMode {
    private PhoneBackgroundMode() {
    }

    /** Legacy default/play-behind values also resolve to audio; stored preferences are not rewritten. */
    public static boolean isOnlyAudio(int backgroundMode) {
        return backgroundMode != PlayerConstants.BACKGROUND_MODE_PIP;
    }
}
