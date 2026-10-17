package com.newtube.mobile.ui.common;

import android.app.Activity;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.liskovsoft.smartyoutubetv2.tv.R;
import java.util.List;

/** Theme-owned bottom action sheet. The screen retains responsibility for each action. */
public final class DreamDialogs {
    private DreamDialogs() {}

    public static void actions(Activity activity, CharSequence title, int[] labels, int[] icons, List<Runnable> actions) {
        show(activity, title, labels, icons, actions, false);
    }

    public static void navigation(Activity activity, CharSequence title, int[] labels, int[] icons, List<Runnable> actions) {
        show(activity, title, labels, icons, actions, true);
    }

    private static void show(Activity activity, CharSequence title, int[] labels, int[] icons, List<Runnable> actions, boolean navigation) {
        if (labels.length != actions.size() || icons.length != labels.length) throw new IllegalArgumentException("Action rows differ");
        float density = activity.getResources().getDisplayMetrics().density;
        int padding = Math.round(20 * density);
        BottomSheetDialog dialog = new BottomSheetDialog(activity,
                navigation ? R.style.ThemeOverlay_DreamPipe_NavigationMenu : 0);
        android.content.res.ColorStateList navigationColors = androidx.core.content.ContextCompat.getColorStateList(activity, R.color.mobile_bottom_nav_item);
        dialog.setTitle(title);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, Math.round(16 * density));
        TextView heading = new TextView(activity);
        heading.setText(title); heading.setTextSize(20); heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setTextColor(activity.getColor(R.color.mobile_color_on_surface));
        heading.setPadding(padding, padding, padding, Math.round(12 * density));
        ViewCompat.setAccessibilityHeading(heading, true); content.addView(heading);
        TypedValue background = new TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, background, true);
        for (int i = 0; i < labels.length; i++) {
            final int position = i;
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(padding, Math.round(12 * density), padding, Math.round(12 * density));
            row.setMinimumHeight(Math.round(56 * density)); row.setFocusable(true);
            if (background.resourceId != 0) row.setBackgroundResource(background.resourceId);
            row.setContentDescription(activity.getString(labels[i]));
            ImageView icon = new ImageView(activity); icon.setImageResource(icons[i]);
            icon.setImageTintList(navigation ? navigationColors
                    : android.content.res.ColorStateList.valueOf(activity.getColor(R.color.mobile_color_primary)));
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(icon, new LinearLayout.LayoutParams(Math.round(24 * density), Math.round(24 * density)));
            TextView text = new TextView(activity); text.setText(labels[i]); text.setTextSize(16);
            if (navigation) text.setTextColor(navigationColors);
            else text.setTextColor(activity.getColor(R.color.mobile_color_on_surface));
            text.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
            params.setMarginStart(padding); row.addView(text, params);
            row.setOnClickListener(v -> { dialog.dismiss(); actions.get(position).run(); });
            content.addView(row);
        }
        ScrollView scroll = new ScrollView(activity);
        scroll.setNestedScrollingEnabled(true);
        scroll.setLayoutParams(new ViewGroup.LayoutParams(-1, -2));
        scroll.addView(content, new ViewGroup.LayoutParams(-1, -2));
        ViewCompat.setAccessibilityPaneTitle(scroll, title);
        dialog.setContentView(scroll);
        dialog.show();
        dialog.getBehavior().setSkipCollapsed(true);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
    }
}
