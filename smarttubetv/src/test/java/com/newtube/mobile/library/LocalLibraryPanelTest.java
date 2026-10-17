package com.newtube.mobile.library;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import androidx.activity.ComponentActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import com.liskovsoft.smartyoutubetv2.tv.R;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.shadows.ShadowDialog;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, manifest = Config.NONE, sdk = 28)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LocalLibraryPanelTest {
    private ActivityController<ComponentActivity> lifecycle;
    private ComponentActivity activity;
    private FrameLayout root;
    private LocalLibraryPanel panel;
    private LocalPlaylistStore store;
    private LocalHistoryStore history;
    private LocalPlaylist first, second;
    private ExecutorService worker(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true);
        return (ExecutorService) field.get(owner);
    }
    private <T> T construct(Class<T> type, Context context) throws Exception {
        var constructor = type.getDeclaredConstructor(Context.class); constructor.setAccessible(true);
        return constructor.newInstance(context);
    }
    @Before public void setup() throws Exception {
        lifecycle = Robolectric.buildActivity(ComponentActivity.class); activity = lifecycle.get();
        activity.setTheme(R.style.Theme_NewTube); lifecycle.create();
        activity.getSharedPreferences("dreampipe_library", Context.MODE_PRIVATE).edit().clear().commit();
        File files = Files.createTempDirectory("library-ui-test").toFile();
        Context context = new ContextWrapper(activity) { @Override public File getFilesDir() { return files; } };
        store = construct(LocalPlaylistStore.class, context); history = construct(LocalHistoryStore.class, context);
        root = new FrameLayout(activity); activity.setContentView(root);
        panel = new LocalLibraryPanel(activity, root, () -> {}, store, history);
        lifecycle.start().resume();
        first = create("One"); second = create("Two"); drain();
    }
    private LocalPlaylist create(String name) throws Exception {
        CompletableFuture<LocalPlaylist> result = new CompletableFuture<>();
        store.create(name, new LocalPlaylistStore.Result<LocalPlaylist>() {
            public void onSuccess(LocalPlaylist value) { result.complete(value); }
            public void onError(Exception error) { result.completeExceptionally(error); }
        });
        drain(); return result.get(3, TimeUnit.SECONDS);
    }
    private void drain() throws Exception {
        // Dialog button callbacks run on the main looper and may enqueue store work.
        shadowOf(Looper.getMainLooper()).idle();
        worker(store, "mWorker").submit(() -> {}).get(3, TimeUnit.SECONDS);
        worker(history, "mWorker").submit(() -> {}).get(3, TimeUnit.SECONDS);
        shadowOf(Looper.getMainLooper()).idle();
        worker(panel, "mQueries").submit(() -> {}).get(3, TimeUnit.SECONDS);
        shadowOf(Looper.getMainLooper()).idle();
    }
    @After public void teardown() throws Exception {
        if (ShadowDialog.getLatestDialog() != null) ShadowDialog.getLatestDialog().dismiss();
        panel.release();
        for (Object owner : new Object[] {store, history}) {
            worker(owner, "mWorker").shutdown(); worker(owner, "mWorker").awaitTermination(3, TimeUnit.SECONDS);
        }
        lifecycle.pause().stop().destroy();
    }
    private LocalListAdapter adapter() { return (LocalListAdapter) ((RecyclerView) root.findViewById(R.id.local_list)).getAdapter(); }
    private LocalListAdapter.Holder row(int position) {
        LocalListAdapter.Holder row = adapter().onCreateViewHolder(root, 0);
        adapter().onBindViewHolder(row, position); return row;
    }
    private AlertDialog dialog() { return (AlertDialog) ShadowDialog.getLatestDialog(); }
    private static CheckBox removeSources(View view) {
        if (view instanceof CheckBox && ((CheckBox) view).getText().toString().contains("source playlists")) return (CheckBox) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            CheckBox found = removeSources(((ViewGroup) view).getChildAt(i)); if (found != null) return found;
        }
        return null;
    }
    @Test public void longPressAndTapSelectPlaylistsAndMergeCanKeepSources() throws Exception {
        row(0).itemView.performLongClick();
        assertEquals(activity.getString(R.string.dreampipe_rename), ((android.widget.TextView) root.findViewById(R.id.local_bulk_merge)).getText().toString());
        row(1).itemView.performClick();
        assertEquals(activity.getString(R.string.dreampipe_merge), ((android.widget.TextView) root.findViewById(R.id.local_bulk_merge)).getText().toString());
        assertFalse(panel.isDetail());
        assertTrue(root.findViewById(R.id.local_bulk_merge).isEnabled());
        assertTrue(((CheckBox) row(0).itemView.findViewById(R.id.playlist_selected)).isChecked());
        root.findViewById(R.id.local_bulk_merge).performClick();
        assertEquals(2, dialog().getListView().getCount());
        AlertDialog destination = dialog();
        destination.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        drain();
        assertFalse(destination.isShowing());
        assertNotSame(destination, dialog());
        assertTrue(dialog().isShowing());
        assertEquals(1, dialog().getListView().getCount());
        CheckBox remove = removeSources(dialog().getWindow().getDecorView());
        assertNotNull(remove); assertTrue(remove.isChecked()); remove.performClick();
        dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick(); drain();
        assertNotNull(store.find(first.id)); assertNotNull(store.find(second.id));
        assertEquals(View.GONE, root.findViewById(R.id.local_selection).getVisibility());
    }
    @Test public void selectedDeletionRequiresConfirmationAndPreservesUnselectedPlaylists() throws Exception {
        row(0).itemView.performLongClick(); root.findViewById(R.id.local_bulk_delete).performClick();
        dialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        assertNotNull(store.find(first.id)); assertNotNull(store.find(second.id));
        root.findViewById(R.id.local_bulk_delete).performClick();
        dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick(); drain();
        assertNull(store.find(first.id)); assertNotNull(store.find(second.id));
        assertEquals(View.GONE, root.findViewById(R.id.local_selection).getVisibility());
    }

    @Test public void playlistHeaderScrollsAwayWhileSearchRemainsAvailable() throws Exception {
        row(0).itemView.performClick(); drain();
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1080, 1600);
        androidx.coordinatorlayout.widget.CoordinatorLayout coordinator = root.findViewById(R.id.local_playlist_coordinator);
        com.google.android.material.appbar.AppBarLayout appbar = root.findViewById(R.id.local_playlist_appbar);
        View body = root.findViewById(R.id.local_playlist_body);
        View header = root.findViewById(R.id.local_playlist_header);
        int range = appbar.getTotalScrollRange();
        assertTrue(range > 0);
        com.google.android.material.appbar.AppBarLayout.Behavior behavior =
                (com.google.android.material.appbar.AppBarLayout.Behavior)
                        ((androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams) appbar.getLayoutParams()).getBehavior();
        assertNotNull(behavior);
        assertTrue(behavior.onStartNestedScroll(coordinator, appbar, body, root.findViewById(R.id.local_list),
                androidx.core.view.ViewCompat.SCROLL_AXIS_VERTICAL, androidx.core.view.ViewCompat.TYPE_TOUCH));
        behavior.onNestedPreScroll(coordinator, appbar, body, 0, range, new int[2], androidx.core.view.ViewCompat.TYPE_TOUCH);
        assertEquals(-range, behavior.getTopAndBottomOffset());
        assertTrue("title and playback controls leave the viewport", header.getBottom() + appbar.getTop() <= 0);
        assertEquals(View.VISIBLE, root.findViewById(R.id.local_search).getVisibility());
        assertTrue(panel.isDetail());
    }
}
