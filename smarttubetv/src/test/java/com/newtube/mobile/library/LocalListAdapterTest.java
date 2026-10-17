package com.newtube.mobile.library;

import static org.junit.Assert.*;
import android.app.Activity;
import android.app.Application;
import android.view.View;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import androidx.recyclerview.widget.RecyclerView;
import com.liskovsoft.smartyoutubetv2.tv.R;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import java.util.Arrays;
import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, manifest = Config.NONE, sdk = 28)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LocalListAdapterTest {
    private ActivityController<Activity> lifecycle;
    private LocalListAdapter adapter;
    private LocalListAdapter.Holder row;
    private LocalPlaylist first, second;
    private Object opened;
    @Before public void setup() {
        lifecycle = Robolectric.buildActivity(Activity.class);
        Activity activity = lifecycle.get(); activity.setTheme(R.style.Theme_NewTube); lifecycle.setup();
        adapter = new LocalListAdapter(new LocalListAdapter.Host() {
            public void open(Object item) { opened = item; }
            public void actions(Object item) {}
            public void drag(RecyclerView.ViewHolder holder) {}
            public void reorderAccessibly(Object item) {}
        });
        first = LocalPlaylist.create("One", 1); second = LocalPlaylist.create("Two", 2);
        adapter.submit(Arrays.asList(first, second), true);
        row = adapter.onCreateViewHolder(new FrameLayout(activity), 0);
    }
    @After public void teardown() { adapter.onViewRecycled(row); lifecycle.pause().stop().destroy(); }
    @Test public void selectionShowsCheckmarksWithoutStealingTheRowClickOrOfferingDrag() {
        adapter.selection(true, Collections.singleton(first.id)); adapter.onBindViewHolder(row, 0);
        CheckBox check = row.itemView.findViewById(R.id.playlist_selected);
        assertEquals(View.VISIBLE, check.getVisibility()); assertTrue(check.isChecked());
        assertFalse(check.isClickable()); assertFalse(adapter.isReorderable());
        assertEquals(View.GONE, row.itemView.findViewById(R.id.playlist_drag).getVisibility());
        row.itemView.performClick(); assertSame(first, opened);
    }
    @Test public void recycledRowsNeverInheritAnotherPlaylistsSelection() {
        adapter.selection(true, Collections.singleton(first.id)); adapter.onBindViewHolder(row, 0);
        adapter.onBindViewHolder(row, 1);
        assertFalse(((CheckBox) row.itemView.findViewById(R.id.playlist_selected)).isChecked());
        assertFalse(row.itemView.isActivated());
        adapter.selection(false, Collections.emptySet()); adapter.onBindViewHolder(row, 0);
        assertEquals(View.GONE, row.itemView.findViewById(R.id.playlist_selected).getVisibility());
        assertTrue(adapter.isReorderable());
    }
}
