package com.newtube.mobile.library;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;

import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.mediaserviceinterfaces.data.PlaylistInfo;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.Schedulers;

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
public class PlaylistSaveDialogTest {
    private ActivityController<Activity> mLifecycle;
    private Activity mActivity;
    private LocalPlaylistStore mStore;
    private ExecutorService mWorker;
    private LocalPlaylist mPlaylist;
    private AccountPlaylistClient mAccount;
    private PlaylistSaveDialog.Listener mMembership;

    @Before public void setUp() throws Exception {
        RxJavaPlugins.setIoSchedulerHandler(scheduler -> Schedulers.trampoline());
        RxAndroidPlugins.setMainThreadSchedulerHandler(scheduler -> Schedulers.trampoline());
        mLifecycle = Robolectric.buildActivity(Activity.class);
        mActivity = mLifecycle.get();
        mActivity.setTheme(R.style.Theme_NewTube);
        mLifecycle.setup();
        File files = Files.createTempDirectory("picker-test").toFile();
        Context context = new ContextWrapper(mActivity) {
            @Override public File getFilesDir() { return files; }
        };
        var constructor = LocalPlaylistStore.class.getDeclaredConstructor(Context.class);
        constructor.setAccessible(true); mStore = constructor.newInstance(context);
        Field worker = LocalPlaylistStore.class.getDeclaredField("mWorker"); worker.setAccessible(true);
        mWorker = (ExecutorService) worker.get(mStore);
        CompletableFuture<LocalPlaylist> created = new CompletableFuture<>();
        mStore.create("My playlist", new LocalPlaylistStore.Result<LocalPlaylist>() {
            @Override public void onSuccess(LocalPlaylist value) { created.complete(value); }
            @Override public void onError(Exception error) { created.completeExceptionally(error); }
        });
        drain(); mPlaylist = created.get(3, TimeUnit.SECONDS);
        mMembership = mock(PlaylistSaveDialog.Listener.class);
        mAccount = mock(AccountPlaylistClient.class);
        when(mAccount.signedIn()).thenReturn(false);
    }

    @After public void tearDown() throws Exception {
        Dialog dialog = ShadowDialog.getLatestDialog();
        if (dialog != null) dialog.dismiss();
        if (mWorker != null) { mWorker.shutdown(); mWorker.awaitTermination(3, TimeUnit.SECONDS); }
        if (mLifecycle != null) mLifecycle.pause().stop().destroy();
        RxAndroidPlugins.reset(); RxJavaPlugins.reset();
    }

    private void drain() throws Exception {
        mWorker.submit(() -> {}).get(3, TimeUnit.SECONDS);
        shadowOf(Looper.getMainLooper()).idle();
    }

    private RecyclerView show() {
        Video video = Video.from("aaaaaaaaaaa"); video.title = "A video";
        new PlaylistSaveDialog(mActivity, video, mMembership, mStore, mAccount).show();
        shadowOf(Looper.getMainLooper()).idle();
        return listIn(ShadowDialog.getLatestDialog().getWindow().getDecorView());
    }

    private RecyclerView listIn(View view) {
        if (view instanceof RecyclerView) return (RecyclerView) view;
        if (view instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                RecyclerView found = listIn(((ViewGroup) view).getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private View row(RecyclerView list, int index) {
        RecyclerView.Adapter adapter = list.getAdapter();
        RecyclerView.ViewHolder holder = adapter.createViewHolder(list, adapter.getItemViewType(index));
        adapter.bindViewHolder(holder, index);
        return holder.itemView;
    }

    @Test public void signedOutCanSelectAndDeselectExistingLocalPlaylist() throws Exception {
        RecyclerView list = show(); drain();
        assertNotNull(list);
        assertEquals(1, list.getAdapter().getItemCount());
        View first = row(list, 0);
        assertFalse(((CheckBox) first.findViewById(R.id.target_checked)).isChecked());
        assertTrue(first.performClick()); drain();
        assertEquals("aaaaaaaaaaa", mStore.find(mPlaylist.id).videos.get(0).id);
        verify(mMembership, atLeastOnce()).onMembershipChanged(true, Boolean.FALSE);
        View selected = row(list, 0);
        assertTrue(((CheckBox) selected.findViewById(R.id.target_checked)).isChecked());
        assertFalse(selected.findViewById(R.id.target_checked).isClickable());
        selected.performClick(); drain();
        assertTrue(mStore.find(mPlaylist.id).videos.isEmpty());
        verify(mAccount, never()).playlists(anyString());
        verify(mAccount, never()).membership(anyString(), anyString(), anyBoolean());
    }

    @Test public void accountDestinationUsesAccountApiAndKeepsLocalDestinationUntouched() throws Exception {
        when(mAccount.signedIn()).thenReturn(true);
        PlaylistInfo remote = mock(PlaylistInfo.class);
        // Deliberately the same string: the destination type, not a guessed ID prefix, routes writes.
        when(remote.getPlaylistId()).thenReturn(mPlaylist.id);
        when(remote.getTitle()).thenReturn("My playlist");
        when(mAccount.playlists("aaaaaaaaaaa")).thenReturn(Observable.just(Collections.singletonList(remote)));
        when(mAccount.membership(mPlaylist.id, "aaaaaaaaaaa", true)).thenReturn(Observable.empty());
        RecyclerView list = show(); drain();
        assertEquals(2, list.getAdapter().getItemCount());
        row(list, 1).performClick();
        verify(mAccount).membership(mPlaylist.id, "aaaaaaaaaaa", true);
        verify(mAccount).changed();
        verify(mMembership, atLeastOnce()).onMembershipChanged(false, Boolean.TRUE);
        assertTrue(mStore.find(mPlaylist.id).videos.isEmpty());
        assertTrue(((CheckBox) row(list, 1).findViewById(R.id.target_checked)).isChecked());
    }

    @Test public void failedAccountWriteDoesNotShowSuccessfulMembership() throws Exception {
        when(mAccount.signedIn()).thenReturn(true);
        PlaylistInfo remote = mock(PlaylistInfo.class);
        when(remote.getPlaylistId()).thenReturn("PLremote");
        when(remote.getTitle()).thenReturn("Account playlist");
        when(mAccount.playlists("aaaaaaaaaaa")).thenReturn(Observable.just(Collections.singletonList(remote)));
        when(mAccount.membership("PLremote", "aaaaaaaaaaa", true)).thenReturn(Observable.error(new java.io.IOException("offline")));
        RecyclerView list = show(); drain();
        row(list, 1).performClick();
        assertFalse(((CheckBox) row(list, 1).findViewById(R.id.target_checked)).isChecked());
        verify(mAccount, never()).changed();
        verify(mMembership, never()).onMembershipChanged(anyBoolean(), eq(Boolean.TRUE));
        assertTrue(mStore.find(mPlaylist.id).videos.isEmpty());
    }
}
