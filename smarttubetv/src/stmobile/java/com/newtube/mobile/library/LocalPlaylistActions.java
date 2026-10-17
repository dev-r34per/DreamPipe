package com.newtube.mobile.library;

import android.app.Activity;
import android.text.InputFilter;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.newtube.mobile.ui.common.MobileSnackbar;

import java.util.function.Consumer;

/** One save picker shared by feed menus, playlist entries and the watch page. */
public final class LocalPlaylistActions {
    private LocalPlaylistActions() {}

    public static void showSave(Activity activity, Video video) {
        PlaylistSaveDialog.show(activity, video, null);
    }

    public static void showSave(Activity activity, LocalVideo entry) {
        PlaylistSaveDialog.show(activity, LocalVideos.toVideo(entry), null);
    }

    public static void promptName(Activity activity, int title, String initial, Consumer<String> onName) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint(R.string.dreampipe_name);
        input.setText(initial);
        input.setSelectAllOnFocus(true);
        input.setFilters(new InputFilter[] {new InputFilter.LengthFilter(200)});
        int inset = Math.round(24 * activity.getResources().getDisplayMetrics().density);
        android.widget.FrameLayout frame = new android.widget.FrameLayout(activity);
        frame.setPadding(inset, 0, inset, 0);
        frame.addView(input);
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity).setTitle(title).setView(frame)
                .setNegativeButton(R.string.dreampipe_cancel, null)
                .setPositiveButton(R.string.dreampipe_save, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) { input.setError(activity.getString(R.string.dreampipe_name_required)); return; }
            dialog.dismiss();
            onName.accept(name);
        }));
        dialog.show();
    }

    public static <T> LocalPlaylistStore.Result<T> result(Activity activity, Consumer<T> success) {
        return new LocalPlaylistStore.Result<T>() {
            @Override public void onSuccess(T value) {
                if (!activity.isFinishing() && !activity.isDestroyed() && success != null) success.accept(value);
            }
            @Override public void onError(Exception error) {
                if (!activity.isFinishing() && !activity.isDestroyed()) MobileSnackbar.show(activity, R.string.dreampipe_change_failed);
            }
        };
    }
}
