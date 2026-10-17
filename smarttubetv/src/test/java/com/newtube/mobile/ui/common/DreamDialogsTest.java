package com.newtube.mobile.ui.common;

import static org.junit.Assert.*;
import android.app.Activity;
import android.app.Application;
import android.view.View;
import android.view.ViewGroup;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.liskovsoft.smartyoutubetv2.tv.R;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
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
public class DreamDialogsTest {
    private static View row(View view, String label) {
        if (label.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View found = row(((ViewGroup) view).getChildAt(i), label); if (found != null) return found;
        }
        return null;
    }
    @Test public void menuHasTheFourLabeledRoutesAndDismissesBeforeDispatch() {
        var lifecycle = Robolectric.buildActivity(Activity.class);
        Activity activity = lifecycle.get(); activity.setTheme(R.style.Theme_NewTube); lifecycle.setup();
        AtomicInteger chosen = new AtomicInteger(-1);
        try {
            DreamDialogs.navigation(activity, activity.getString(R.string.dreampipe_menu),
                    new int[] {R.string.header_subscriptions, R.string.header_downloads, R.string.header_settings, R.string.mobile_nav_you},
                    new int[] {R.drawable.ic_nav_subscriptions, R.drawable.ic_nav_downloads, R.drawable.ic_mobile_settings, R.drawable.ic_nav_you},
                    Arrays.asList(() -> chosen.set(0), () -> chosen.set(1), () -> chosen.set(2), () -> chosen.set(3)));
            BottomSheetDialog dialog = (BottomSheetDialog) ShadowDialog.getLatestDialog();
            for (int label : new int[] {R.string.header_subscriptions, R.string.header_downloads, R.string.header_settings, R.string.mobile_nav_you})
                assertNotNull(row(dialog.getWindow().getDecorView(), activity.getString(label)));
            row(dialog.getWindow().getDecorView(), activity.getString(R.string.header_downloads)).performClick();
            assertEquals(1, chosen.get()); assertFalse(dialog.isShowing());
        } finally {
            if (ShadowDialog.getLatestDialog() != null) ShadowDialog.getLatestDialog().dismiss();
            lifecycle.pause().stop().destroy();
        }
    }

    @Test @Config(qualifiers = "night")
    public void navigationMenuMatchesDarkBarWhileGeneralSheetsStayLavender() { checkMenuPalette(); }

    @Test @Config(qualifiers = "notnight")
    public void navigationMenuMatchesLightBarWhileGeneralSheetsKeepTheirSurface() { checkMenuPalette(); }

    private void checkMenuPalette() {
        var lifecycle = Robolectric.buildActivity(Activity.class);
        Activity activity = lifecycle.get(); activity.setTheme(R.style.Theme_NewTube); lifecycle.setup();
        try {
            int[] labels = {R.string.header_downloads}; int[] icons = {R.drawable.ic_nav_downloads};
            DreamDialogs.navigation(activity, "Menu", labels, icons, Arrays.asList(() -> {}));
            BottomSheetDialog dialog = (BottomSheetDialog) ShadowDialog.getLatestDialog();
            View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            assertEquals(activity.getColor(R.color.mobile_color_background), background(sheet));
            ViewGroup action = (ViewGroup) row(dialog.getWindow().getDecorView(), activity.getString(labels[0]));
            int inactive = activity.getColor(R.color.mobile_color_on_surface_secondary);
            assertEquals(inactive, ((android.widget.TextView) action.getChildAt(1)).getCurrentTextColor());
            assertEquals(inactive, ((android.widget.ImageView) action.getChildAt(0)).getImageTintList().getDefaultColor());
            dialog.dismiss();
            DreamDialogs.actions(activity, "Actions", labels, icons, Arrays.asList(() -> {}));
            dialog = (BottomSheetDialog) ShadowDialog.getLatestDialog();
            sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            assertEquals(activity.getColor(R.color.mobile_color_surface), background(sheet));
        } finally {
            if (ShadowDialog.getLatestDialog() != null) ShadowDialog.getLatestDialog().dismiss();
            lifecycle.pause().stop().destroy();
        }
    }

    private int background(View view) {
        if (view.getBackground() instanceof android.graphics.drawable.ColorDrawable)
            return ((android.graphics.drawable.ColorDrawable) view.getBackground()).getColor();
        return ((com.google.android.material.shape.MaterialShapeDrawable) view.getBackground()).getFillColor().getDefaultColor();
    }
}
