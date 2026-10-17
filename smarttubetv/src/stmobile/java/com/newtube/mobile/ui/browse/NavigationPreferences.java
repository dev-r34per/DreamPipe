package com.newtube.mobile.ui.browse;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.newtube.mobile.ui.common.MobileSnackbar;

import java.util.ArrayList;
import java.util.List;

/** The bar is an ordered preference; Menu always occupies its own permanent slot. */
public final class NavigationPreferences {
    public static final String PREFS = "dreampipe_navigation";
    public static final String ACTION_EDIT = "app.dreampipe.EDIT_NAVIGATION";
    private static List<Destination> sAvailable = new ArrayList<>();

    private NavigationPreferences() {}

    public static List<Integer> selected(Context context) {
        return NavigationConfig.parse(prefs(context).getString("destinations", null));
    }

    public static int rowLimit(Context context) {
        int rows = prefs(context).getInt("row_limit", 0);
        return rows == 5 || rows == 10 || rows == 20 ? rows : 0;
    }

    public static void setRowLimit(Context context, int rows) {
        prefs(context).edit().putInt("row_limit", rows).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static final class Destination {
        public final int id;
        public final CharSequence title;
        public final int icon;
        public Destination(int id, CharSequence title, int icon) {
            this.id = id; this.title = title; this.icon = icon;
        }
    }

    public static void rememberDestinations(List<Destination> destinations) {
        sAvailable = new ArrayList<>(destinations);
    }

    public static void edit(Activity activity, Runnable onSaved) {
        List<Destination> available = new ArrayList<>(sAvailable);
        if (available.isEmpty()) {
            available.add(new Destination(0, activity.getString(R.string.header_home), R.drawable.ic_nav_home));
            available.add(new Destination(NavigationConfig.LOCAL_PLAYLISTS, activity.getString(R.string.dreampipe_local_playlists), R.drawable.ic_dreampipe_playlists));
            available.add(new Destination(NavigationConfig.LOCAL_HISTORY, activity.getString(R.string.dreampipe_history), R.drawable.ic_nav_history));
            available.add(new Destination(NavigationConfig.YOU, activity.getString(R.string.mobile_nav_you), R.drawable.ic_nav_you));
        }
        edit(activity, available, onSaved);
    }

    public static void edit(Activity activity, List<Destination> available, Runnable onSaved) {
        List<Integer> chosen = new ArrayList<>(selected(activity));
        java.util.Set<Integer> valid = new java.util.HashSet<>();
        for (Destination destination : available) valid.add(destination.id);
        chosen.retainAll(valid);
        List<Destination> ordered = new ArrayList<>();
        for (int id : chosen) for (Destination destination : available) if (destination.id == id) ordered.add(destination);
        for (Destination destination : available) if (!ordered.contains(destination)) ordered.add(destination);

        int padding = Math.round(16 * activity.getResources().getDisplayMetrics().density);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, 0, padding, padding);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(content);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.dreampipe_navigation)
                .setMessage(R.string.dreampipe_navigation_summary)
                .setView(scroll)
                .setNegativeButton(R.string.dreampipe_cancel, null)
                .setNeutralButton(R.string.dreampipe_reset, null)
                .setPositiveButton(R.string.dreampipe_save, null).create();

        Runnable[] render = new Runnable[1];
        render[0] = () -> {
            ordered.clear();
            for (int id : chosen) for (Destination option : available) if (option.id == id) ordered.add(option);
            for (Destination option : available) if (!ordered.contains(option)) ordered.add(option);
            content.removeAllViews();
            for (Destination destination : ordered) {
                LinearLayout row = new LinearLayout(activity);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                CheckBox check = new CheckBox(activity);
                check.setText(destination.title);
                check.setChecked(chosen.contains(destination.id));
                check.setMinHeight(padding * 3);
                row.addView(check, new LinearLayout.LayoutParams(0, -2, 1));
                check.setOnCheckedChangeListener((button, checked) -> {
                    if (checked && chosen.size() >= NavigationConfig.MAX_TABS) {
                        button.setChecked(false);
                        MobileSnackbar.show(activity, activity.getString(R.string.dreampipe_navigation_limit));
                        return;
                    }
                    if (checked) chosen.add(destination.id); else chosen.remove(Integer.valueOf(destination.id));
                    render[0].run();
                });
                for (int direction : new int[] {-1, 1}) {
                    ImageButton move = new ImageButton(activity);
                    move.setImageResource(direction < 0 ? R.drawable.ic_dreampipe_up : R.drawable.ic_dreampipe_down);
                    move.setContentDescription(activity.getString(direction < 0
                            ? R.string.dreampipe_move_up : R.string.dreampipe_move_down) + ": " + destination.title);
                    move.setBackgroundResource(android.R.color.transparent);
                    int index = chosen.indexOf(destination.id);
                    move.setEnabled(index >= 0 && index + direction >= 0 && index + direction < chosen.size());
                    move.setAlpha(move.isEnabled() ? 1f : 0.25f);
                    row.addView(move, new LinearLayout.LayoutParams(padding * 3, padding * 3));
                    move.setOnClickListener(v -> {
                        int from = chosen.indexOf(destination.id);
                        if (from < 0 || from + direction < 0 || from + direction >= chosen.size()) return;
                        java.util.Collections.swap(chosen, from, from + direction);
                        render[0].run();
                    });
                }
                content.addView(row);
            }
        };
        render[0].run();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                chosen.clear();
                chosen.addAll(NavigationConfig.DEFAULTS);
                chosen.retainAll(valid);
                render[0].run();
            });
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (chosen.isEmpty()) {
                    MobileSnackbar.show(activity, activity.getString(R.string.dreampipe_navigation_limit));
                    return;
                }
                prefs(activity).edit().putString("destinations", NavigationConfig.encode(chosen)).apply();
                dialog.dismiss();
                onSaved.run();
            });
        });
        dialog.show();
    }
}
