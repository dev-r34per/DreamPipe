package com.newtube.mobile.ui.playback;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.app.Application;
import android.content.Intent;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerNotificationManager;
import static org.robolectric.Shadows.shadowOf;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.newtube.mobile.player.Media3PlayerController;
import com.newtube.mobile.player.Media3PlayerInitializer;
import java.lang.reflect.Field;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, manifest = Config.NONE, sdk = 28)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class BackgroundPlaybackServiceOwnershipTest {
    private void field(Object owner, String name, Object value) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); field.set(owner, value);
    }

    @Test public void automaticNotificationCancellationPreservesStartedForegroundPlayback() throws Exception {
        var lifecycle = Robolectric.buildService(MobilePlaybackService.class).create();
        MobilePlaybackService service = lifecycle.get();
        try {
            service.onStartCommand(new Intent(), 0, 1);
            ExoPlayer player = mock(ExoPlayer.class);
            when(player.getPlayWhenReady()).thenReturn(true);
            field(service, "mPlayer", player);
            field(service, "mNotificationManager", mock(PlayerNotificationManager.class));
            for (int state : new int[] {Player.STATE_IDLE, Player.STATE_BUFFERING, Player.STATE_READY}) {
                when(player.getPlaybackState()).thenReturn(state);
                service.notificationCancelled(false);
                assertFalse(shadowOf(service).isStoppedBySelf());
                assertNotNull(shadowOf(service).getLastForegroundNotification());
                assertFalse(shadowOf(service).isForegroundStopped());
            }
            verify(player, never()).pause();
        } finally { lifecycle.destroy(); }
    }

    @Test public void taskRemovalTakesOwnershipBeforeWindowDestructionAndIsIdempotent() throws Exception {
        var lifecycle = Robolectric.buildService(MobilePlaybackService.class).create();
        MobilePlaybackService service = lifecycle.get();
        try {
            service.onStartCommand(new Intent(), 0, 1);
            ExoPlayer player = mock(ExoPlayer.class);
            when(player.getPlayWhenReady()).thenReturn(true);
            when(player.getPlaybackState()).thenReturn(Player.STATE_BUFFERING);
            Media3PlayerController engine = mock(Media3PlayerController.class);
            Media3PlayerInitializer initializer = mock(Media3PlayerInitializer.class);
            PlaybackPresenter presenter = mock(PlaybackPresenter.class);
            when(presenter.getPlayer()).thenReturn(mock(PlaybackView.class));
            field(service, "mPlayer", player); field(service, "mController", engine);
            field(service, "mInitializer", initializer); field(service, "mPresenter", presenter);
            service.onTaskRemoved(new Intent());
            assertNotNull(MobilePlaybackService.retainedPlayer());
            service.onUnbind(new Intent());
            assertTrue(service.retainForBackground(player, engine, initializer, presenter));
            verify(presenter, times(1)).setView(any(PlaybackView.class));
            verify(engine, never()).release();
            assertFalse(shadowOf(service).isStoppedBySelf());
        } finally { lifecycle.destroy(); }
    }

    @Test public void notificationDismissalStopsInsteadOfRestartingPlayback() throws Exception {
        var lifecycle = Robolectric.buildService(MobilePlaybackService.class).create();
        MobilePlaybackService service = lifecycle.get();
        try {
            ExoPlayer player = mock(ExoPlayer.class);
            PlaybackPresenter presenter = mock(PlaybackPresenter.class);
            field(service, "mPlayer", player); field(service, "mPresenter", presenter);
            field(service, "mNotificationManager", mock(PlayerNotificationManager.class));
            service.notificationCancelled(true);
            verify(player).pause(); verify(presenter).onFinish(); verify(presenter).forceFinish();
            assertTrue(shadowOf(service).isStoppedBySelf());
        } finally { lifecycle.destroy(); }
    }

    @Test public void preparingNotificationHasAWorkingStopCommandBeforeAPlayerExists() {
        var lifecycle = Robolectric.buildService(MobilePlaybackService.class).create();
        MobilePlaybackService service = lifecycle.get();
        try {
            service.onStartCommand(new Intent(), 0, 1);
            android.app.Notification notification = shadowOf(service).getLastForegroundNotification();
            assertNotNull(notification);
            assertEquals(1, notification.actions.length);
            Intent stop = shadowOf(notification.actions[0].actionIntent).getSavedIntent();
            assertEquals(MobilePlaybackService.ACTION_STOP, stop.getAction());
            service.onStartCommand(stop, 0, 2);
            assertTrue(shadowOf(service).isStoppedBySelf());
            assertTrue(shadowOf(service).isForegroundStopped());
        } finally { lifecycle.destroy(); }
    }

    @Test public void oldNotificationCallbacksCannotDemoteOrStopANewerSession() throws Exception {
        var lifecycle = Robolectric.buildService(MobilePlaybackService.class).create();
        MobilePlaybackService service = lifecycle.get();
        try {
            service.onStartCommand(new Intent(), 0, 1);
            field(service, "mNotificationManager", mock(PlayerNotificationManager.class));
            PlayerNotificationManager.NotificationListener old = service.notificationListener();
            field(service, "mNotificationGeneration", 1);
            old.onNotificationPosted(41337, new android.app.Notification(), false);
            old.onNotificationCancelled(41337, true);
            assertFalse(shadowOf(service).isForegroundStopped());
            assertFalse(shadowOf(service).isStoppedBySelf());
        } finally { lifecycle.destroy(); }
    }

    @Test public void taskDismissalKeepsTheSameEngineAndPresenterWithoutAnActivity() throws Exception {
        var lifecycle = Robolectric.buildService(MobilePlaybackService.class).create();
        MobilePlaybackService service = lifecycle.get();
        ExoPlayer player = mock(ExoPlayer.class);
        when(player.getPlayWhenReady()).thenReturn(true);
        when(player.getPlaybackState()).thenReturn(Player.STATE_READY);
        PlaybackPresenter presenter = mock(PlaybackPresenter.class);
        when(presenter.getPlayer()).thenReturn(mock(PlaybackView.class));
        Media3PlayerController engine = mock(Media3PlayerController.class);
        Media3PlayerInitializer initializer = mock(Media3PlayerInitializer.class);
        Field playerField = MobilePlaybackService.class.getDeclaredField("mPlayer");
        playerField.setAccessible(true);
        playerField.set(service, player);

        assertTrue(service.retainForBackground(player, engine, initializer, presenter));
        ArgumentCaptor<PlaybackView> view = ArgumentCaptor.forClass(PlaybackView.class);
        verify(presenter).setView(view.capture());
        assertTrue(view.getValue().isHeadless());
        verify(engine).setVideoTrackDisabled(true);
        service.onTaskRemoved(new Intent());
        verify(engine, never()).release();
        verify(presenter, never()).onEngineReleased();
        MobilePlaybackService.RetainedPlayer retained = MobilePlaybackService.retainedPlayer();
        assertNotNull(retained);
        assertSame(player, retained.player);
        assertSame(engine, retained.controller);
        view.getValue().getVideoFormat();
        view.getValue().getAudioFormat();
        view.getValue().getSubtitleFormat();
        verify(engine).getVideoFormat();
        verify(engine).getAudioFormat();
        verify(engine).getSubtitleFormat();

        service.stopDetachedPlayback();
        verify(engine).release();
        verify(presenter).onEngineReleased();
        verify(presenter).setView(null);
        assertNull(MobilePlaybackService.retainedPlayer());
        lifecycle.destroy();
    }

    @Test public void presentersAcceptHeadlessViewsAndCanClearThem() {
        BasePresenter<PlaybackView> presenter = new BasePresenter<PlaybackView>(RuntimeEnvironment.getApplication()) {};
        PlaybackView view = mock(PlaybackView.class);
        when(view.isHeadless()).thenReturn(true);
        presenter.setView(view);
        assertSame(view, presenter.getView());
        assertSame(RuntimeEnvironment.getApplication(), presenter.getContext());
        presenter.setView(null);
        assertNull(presenter.getView());
    }
}
