package com.newtube.mobile.downloads;

import static org.junit.Assert.*;
import android.app.Application;
import androidx.activity.ComponentActivity;
import androidx.appcompat.app.AlertDialog;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.newtube.mobile.library.LocalVideo;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.shadows.ShadowDialog;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, manifest = Config.NONE, sdk = 28)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class PlaylistDownloadDialogUiTest {
    @Test public void qualityChoicesRemainVisibleAlongsideTheExplanation() {
        var lifecycle = Robolectric.buildActivity(ComponentActivity.class);
        ComponentActivity activity = lifecycle.get(); activity.setTheme(R.style.Theme_NewTube); lifecycle.setup();
        try {
            PlaylistDownloadDialog.show(activity, Collections.singletonList(new LocalVideo("aaaaaaaaaaa", "Track", "", "", 0)));
            AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
            assertNotNull(dialog.getListView().getParent()); assertEquals(4, dialog.getListView().getCount());
            assertEquals(2, dialog.getListView().getCheckedItemPosition());
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        } finally { lifecycle.pause().stop().destroy(); }
    }
}
