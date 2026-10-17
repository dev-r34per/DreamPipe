package com.newtube.mobile.ui.playback;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.core.app.ServiceCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.media3.common.ForwardingPlayer;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerNotificationManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.utils.ClickbaitRemover;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.newtube.mobile.player.Media3PlayerController;
import com.newtube.mobile.player.Media3PlayerInitializer;

/**
 * Lightweight foreground service that keeps audio playing when {@link MobilePlaybackActivity} is
 * backgrounded or the screen turns off, and drives the lock-screen / media notification.
 *
 * <p>It shares the single media3 {@link ExoPlayer} created by {@link MobilePlaybackActivity}
 * while that window exists, then owns that same player/controller after task dismissal.
 * A headless PlaybackView keeps the existing presenter pipeline alive. Media3 has no
 * {@code MediaSessionConnector}, so this wires the live player to:
 * <ul>
 *   <li>a {@link MediaSessionCompat} synced by a small inline connector (transport callbacks
 *       routed to the player, prev/next to the reused {@link PlaybackPresenter} queue, playback
 *       state pushed on every player event), and</li>
 *   <li>a media3 {@link PlayerNotificationManager} whose notification listener promotes/demotes
 *       this service to/from the foreground so audio survives on API 34+. The player handed to it
 *       is wrapped in a {@link ForwardingPlayer} that always advertises prev/next and forwards
 *       them to the presenter's queue (our player only ever holds one MediaItem).</li>
 * </ul>
 *
 * <p>The service is bound + started by the Activity while it is in the foreground (the player is
 * created on a visible screen), so the FIRST {@code startForeground} is always reached from the
 * foreground. A RE-attach (engine restart while backgrounded, e.g. a network flap) must not forfeit
 * that grant: {@link #attachPlayer} suppresses the teardown that swapping players would otherwise
 * trigger, and {@code startForeground} is additionally guarded against
 * {@code ForegroundServiceStartNotAllowedException} (API 31+) - see the notification listener.
 * On real finish the Activity calls {@link #detachPlayer()} which clears the player (cancelling
 * the notification -> stopForeground) before the player itself is released.
 */
public class MobilePlaybackService extends Service {

    private static final String TAG = MobilePlaybackService.class.getSimpleName();

    public static final String CHANNEL_ID = "dreampipe_playback_channel";
    static final String ACTION_STOP = "app.dreampipe.STOP_PLAYBACK";
    private static final int NOTIFICATION_ID = 41337;
    /** Cap the notification/lock-screen art at a sane size instead of decoding the full-res image. */
    // hqdefault is 480px wide; asking for 512 forced a much larger CDN rendition for a tiny icon.
    private static final int ART_SIZE_PX = 480;

    private static final long SESSION_ACTIONS = PlaybackStateCompat.ACTION_PLAY
            | PlaybackStateCompat.ACTION_PAUSE
            | PlaybackStateCompat.ACTION_PLAY_PAUSE
            | PlaybackStateCompat.ACTION_SEEK_TO
            | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
            | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            | PlaybackStateCompat.ACTION_STOP;

    private final IBinder mBinder = new LocalBinder();

    private PlayerNotificationManager mNotificationManager;
    private MediaSessionCompat mMediaSession;
    private PlaybackPresenter mPresenter;
    // One live player; ownership transfers to this service when the window is destroyed.
    private ExoPlayer mPlayer;
    private Player mNotificationPlayer;
    private Player.Listener mSessionSyncListener;
    private boolean mIsForeground;
    /**
     * A new player is being attached over an old one: the old notification's cancel callback must
     * not stopForeground/stopSelf, or the new player's first ongoing notification would call
     * startForeground from the background (API 31+ crash).
     */
    private boolean mReattaching;
    private int mNotificationGeneration;
    private static java.lang.ref.WeakReference<MobilePlaybackService> sRunning = new java.lang.ref.WeakReference<>(null);
    private Media3PlayerController mController;
    private Media3PlayerInitializer mInitializer;
    private BackgroundPlaybackView mDetachedView;
    private PendingIntent mContentIntent;
    private boolean mStoppingDetached;

    /** Read on the main thread when a new window is opened over an ongoing audio session. */
    public static final class RetainedPlayer {
        final MobilePlaybackService service;
        final ExoPlayer player;
        final Media3PlayerController controller;
        final Media3PlayerInitializer initializer;
        final BackgroundPlaybackView view;
        RetainedPlayer(MobilePlaybackService service) {
            this.service = service;
            player = service.mPlayer;
            controller = service.mController;
            initializer = service.mInitializer;
            view = service.mDetachedView;
        }
    }

    public static RetainedPlayer retainedPlayer() {
        MobilePlaybackService service = sRunning.get();
        return service != null && service.mDetachedView != null && service.mPlayer != null
                && service.mController != null ? new RetainedPlayer(service) : null;
    }

    @Override public void onCreate() {
        super.onCreate();
        sRunning = new java.lang.ref.WeakReference<>(this);
    }

    /** The service takes ownership before the Activity releases its views and surfaces. */
    public boolean retainForBackground(ExoPlayer player, Media3PlayerController controller,
            Media3PlayerInitializer initializer, PlaybackPresenter presenter) {
        if (player == null || player != mPlayer || !isPlaybackRequested()
                || controller == null || initializer == null || presenter == null) return false;
        if (mDetachedView != null) return true;
        mController = controller;
        mInitializer = initializer;
        mDetachedView = new BackgroundPlaybackView(this, controller, player, presenter.getPlayer());
        mPresenter = presenter;
        mPresenter.setContext(getApplicationContext());
        mPresenter.setView(mDetachedView);
        player.clearVideoSurface();
        controller.setVideoTrackDisabled(true);
        controller.onBackgroundAudio(true);
        return true;
    }

    public void attachPlayer(ExoPlayer player, PlaybackPresenter presenter, PendingIntent contentIntent,
            Media3PlayerController controller, Media3PlayerInitializer initializer) {
        mController = controller;
        mInitializer = initializer;
        mDetachedView = null;
        attachPlayer(player, presenter, contentIntent);
    }

    void refreshMetadata() {
        if (mMediaSession != null) syncSession();
        if (mNotificationManager != null) mNotificationManager.invalidate();
    }

    void reloadDetachedPlayback() {
        if (mDetachedView == null || mPresenter == null) return;
        mPresenter.onEngineReleased();
        mPresenter.onEngineInitialized();
    }

    void restartDetachedPlayback() {
        if (mDetachedView == null || mPresenter == null || mController == null) return;
        BackgroundPlaybackView view = mDetachedView;
        PlaybackPresenter presenter = mPresenter;
        mReattaching = true;
        try {
            presenter.onEngineReleased();
            mController.release();
            androidx.media3.exoplayer.trackselection.DefaultTrackSelector selector = mInitializer.createTrackSelector();
            mController.setTrackSelector(selector);
            mController.setVideoTrackDisabled(true);
            ExoPlayer player = mInitializer.createPlayer(selector, mController.getMediaSourceFactory().getBandwidthMeter());
            player.setPlayWhenReady(true);
            mController.setPlayer(player);
            mController.attachPreloader(mInitializer.getPreloadManagerBuilder(), mInitializer.getPreloadTrackSelector());
            view.setPlayer(player);
            attachPlayer(player, presenter, mContentIntent);
            mDetachedView = view;
            presenter.onEngineInitialized();
        } finally { mReattaching = false; }
    }

    void stopDetachedPlayback() {
        if (mDetachedView == null || mStoppingDetached) return;
        mStoppingDetached = true;
        PlaybackPresenter presenter = mPresenter;
        Media3PlayerController controller = mController;
        if (presenter != null) {
            presenter.onFinish();
            presenter.onEngineReleased();
            presenter.setView(null);
        }
        mDetachedView = null;
        releaseInternal(true);
        if (controller != null) controller.release();
        mController = null;
        mInitializer = null;
        mStoppingDetached = false;
        stopSelf();
    }

    private void stopSession() {
        if (mDetachedView != null) stopDetachedPlayback();
        else if (mPresenter != null) {
            mPresenter.onFinish();
            mPresenter.forceFinish();
        }
    }

    @Override public void onTaskRemoved(Intent rootIntent) {
        // Own the presenter before depending on Activity.onDestroy: task callbacks can arrive first.
        if (isPlaybackRequested()) {
            retainForBackground(mPlayer, mController, mInitializer, mPresenter);
            ensureForeground();
        } else if (mDetachedView != null) stopDetachedPlayback();
        else stopRequested();
        traceLifecycle("task-removed");
        super.onTaskRemoved(rootIntent);
    }

    private boolean isPlaybackRequested() {
        return mPlayer != null && mPlayer.getPlayWhenReady() && mPlayer.getPlaybackState() != Player.STATE_ENDED;
    }

    private void traceLifecycle(String event) {
        if (com.liskovsoft.smartyoutubetv2.tv.BuildConfig.DEBUG) android.util.Log.i("DreamPipePlayback", event
                + " foreground=" + mIsForeground + " detached=" + (mDetachedView != null)
                + " play-requested=" + isPlaybackRequested());
    }

    private final NotificationArtwork<Bitmap> mArtwork = new NotificationArtwork<>();
    // Held so it can be cleared from Glide on release (avoids the leaked SIZE_ORIGINAL target).
    private CustomTarget<Bitmap> mArtTarget;

    public class LocalBinder extends Binder {
        public MobilePlaybackService getService() {
            return MobilePlaybackService.this;
        }
    }

    /**
     * Context that forces {@link Context#RECEIVER_NOT_EXPORTED} on any 2-arg
     * {@code registerReceiver} call {@link PlayerNotificationManager} makes internally. media3
     * 1.4 is targetSdk-34 aware on its own, but the wrapper is proven on this codebase and
     * harmless, so it stays as belt-and-braces (see the legacy service for the full history).
     */
    private static class NotificationContextWrapper extends ContextWrapper {
        NotificationContextWrapper(Context base) {
            super(base);
        }

        @Override
        public Context getApplicationContext() {
            Context appContext = super.getApplicationContext();
            return appContext == getBaseContext() ? this : new NotificationContextWrapper(appContext);
        }

        @Override
        public Intent registerReceiver(@Nullable BroadcastReceiver receiver, IntentFilter filter) {
            return ContextCompat.registerReceiver(
                    getBaseContext(), receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        }
    }

    /**
     * The single-MediaItem player can't advertise a queue, so prev/next would never show.
     * Advertise them unconditionally and forward to the shared suggestions queue.
     */
    private class QueueForwardingPlayer extends ForwardingPlayer {
        QueueForwardingPlayer(Player player) {
            super(player);
        }

        @Override public void stop() { stopRequested(); }

        @Override
        public boolean isCommandAvailable(int command) {
            if (command == COMMAND_SEEK_TO_NEXT || command == COMMAND_SEEK_TO_PREVIOUS
                    || command == COMMAND_SEEK_TO_NEXT_MEDIA_ITEM || command == COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) {
                return true;
            }
            return super.isCommandAvailable(command);
        }

        @Override
        public Commands getAvailableCommands() {
            return super.getAvailableCommands().buildUpon()
                    .addAll(COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_PREVIOUS,
                            COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build();
        }

        @Override
        public void seekToNext() {
            skipToNext();
        }

        @Override
        public void seekToNextMediaItem() {
            skipToNext();
        }

        @Override
        public void seekToPrevious() {
            skipToPrevious();
        }

        @Override
        public void seekToPreviousMediaItem() {
            skipToPrevious();
        }

        @Override
        public void play() {
            super.play();
            notifyPlayClicked();
        }

        @Override
        public void pause() {
            super.pause();
            notifyPauseClicked();
        }

        /**
         * media3's play action calls {@code prepare()} first when the player is IDLE, which after a
         * fatal error would replay the very media source that just died (stale signed URLs, and on
         * the offline path an immediate re-error). The app owns error recovery - {@link
         * #notifyPlayClicked} reaches ErrorFixerController, which remints the URLs and reloads - so
         * hand IDLE over to it instead. A non-IDLE prepare is a normal one and passes through.
         */
        @Override
        public void prepare() {
            if (getPlaybackState() == Player.STATE_IDLE && mPresenter != null) {
                notifyPlayClicked();
                return;
            }
            super.prepare();
        }
    }

    /**
     * Route a transport-control play into the presenter, not just the player. The player itself is
     * IDLE after a fatal error (a lost connection in a tunnel, a lift, the metro), so
     * {@code setPlayWhenReady(true)} is a no-op there and the notification's play button used to be
     * dead - the only recovery was opening the app and tapping the in-player button.
     * ErrorFixerController turns this into a manual retry; it is a no-op during healthy playback.
     */
    private void notifyPlayClicked() {
        if (mPresenter != null) {
            Utils.post(() -> mPresenter.onPlayClicked());
        }
    }

    private void notifyPauseClicked() {
        if (mPresenter != null) {
            Utils.post(() -> mPresenter.onPauseClicked());
        }
    }

    private void skipToNext() {
        if (mPresenter != null) {
            Utils.post(() -> mPresenter.onNextClicked());
        }
    }

    private void skipToPrevious() {
        if (mPresenter != null) {
            Utils.post(() -> mPresenter.onPreviousClicked());
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopRequested();
            return START_NOT_STICKY;
        }
        // Satisfy startForegroundService immediately, before metadata/artwork/network work.
        showPreparingNotification();
        traceLifecycle("started");
        return START_NOT_STICKY;
    }

    @Override public boolean onUnbind(Intent intent) {
        if (isPlaybackRequested()) retainForBackground(mPlayer, mController, mInitializer, mPresenter);
        else if (mPlayer == null) stopSelf();
        traceLifecycle("last-window-unbound");
        return false;
    }

    private void showPreparingNotification() {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.mobile_playback_channel_name), NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.mobile_playback_channel_desc));
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_play).setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.dreampipe_preparing_playback))
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT).setOngoing(true).setOnlyAlertOnce(true);
        PendingIntent stop = PendingIntent.getService(this, NOTIFICATION_ID,
                new Intent(this, MobilePlaybackService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.dreampipe_stop_playback), stop);
        if (mContentIntent != null) builder.setContentIntent(mContentIntent);
        promote(NOTIFICATION_ID, builder.build());
    }

    private void promote(int id, Notification notification) {
        try {
            startForeground(id, notification);
            mIsForeground = true;
        } catch (IllegalStateException error) {
            mIsForeground = false;
            Log.e(TAG, "Playback foreground promotion rejected: " + error);
            traceLifecycle("foreground-rejected");
        }
    }

    void notificationCancelled(boolean dismissedByUser) {
        // Teardown invalidates the manager before detaching its player; replacement is not Stop.
        if (mReattaching || mNotificationManager == null) return;
        traceLifecycle(dismissedByUser ? "notification-dismissed" : "notification-cancelled-automatic");
        if (dismissedByUser) {
            stopRequested();
        } else if (isPlaybackRequested()) {
            // A transient IDLE/source switch must not erase the started-service lifetime.
            showPreparingNotification();
        } else {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
            mIsForeground = false;
            if (mPlayer == null) stopSelf();
        }
    }

    private void stopRequested() {
        if (mPlayer != null) mPlayer.pause();
        stopSession();
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        mIsForeground = false;
        stopSelf();
        traceLifecycle("explicit-stop");
    }

    /**
     * Attach the reused player. Sets up the media session + notification. Safe to call once per
     * player instance; a second call re-attaches (used when the engine is restarted).
     */
    public void attachPlayer(ExoPlayer player, PlaybackPresenter presenter, PendingIntent contentIntent) {
        if (player == null) {
            return;
        }

        // Re-attach cleanly if something was already wired. mReattaching keeps the foreground
        // grant alive across the swap: setPlayer(null) inside releaseInternal synchronously fires
        // onNotificationCancelled, whose usual stopForeground+stopSelf would demote the service
        // right before the new player re-posts the (same-id) notification.
        boolean wasReattaching = mReattaching;
        mReattaching = true;
        try {
            releaseInternal(false);
        } finally {
            mReattaching = wasReattaching;
        }

        mContentIntent = contentIntent;
        mPresenter = presenter;
        mPlayer = player;
        mNotificationPlayer = new QueueForwardingPlayer(player);

        mMediaSession = new MediaSessionCompat(getApplicationContext(), getPackageName());
        mMediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                if (mPlayer != null) {
                    mPlayer.setPlayWhenReady(true);
                }
                // Lock screen / headset / Android Auto reach the session, not the notification's
                // own player wrapper - both need the dead-state retry (see notifyPlayClicked).
                notifyPlayClicked();
            }

            @Override
            public void onPause() {
                if (mPlayer != null) {
                    mPlayer.setPlayWhenReady(false);
                }
                notifyPauseClicked();
            }

            @Override
            public void onSeekTo(long pos) {
                if (mPlayer != null) {
                    mPlayer.seekTo(pos);
                }
            }

            @Override
            public void onSkipToNext() {
                skipToNext();
            }

            @Override
            public void onSkipToPrevious() {
                skipToPrevious();
            }

            @Override
            public void onStop() {
                stopRequested();
            }
        });
        mMediaSession.setActive(true);

        // Media3 batches callbacks into one event set. Sync once for that set, rather than rebuilding
        // and sending identical metadata/album-art parcels for each callback during an open or seek.
        mSessionSyncListener = new Player.Listener() {
            @Override
            public void onEvents(Player player, Player.Events events) {
                if (events.containsAny(Player.EVENT_PLAYBACK_STATE_CHANGED,
                        Player.EVENT_PLAY_WHEN_READY_CHANGED, Player.EVENT_POSITION_DISCONTINUITY,
                        Player.EVENT_PLAYBACK_PARAMETERS_CHANGED, Player.EVENT_TIMELINE_CHANGED)) {
                    syncSession();
                }
            }
        };
        player.addListener(mSessionSyncListener);
        syncSession();

        mNotificationManager = new PlayerNotificationManager.Builder(
                new NotificationContextWrapper(this), NOTIFICATION_ID, CHANNEL_ID)
                .setChannelNameResourceId(R.string.mobile_playback_channel_name)
                .setChannelDescriptionResourceId(R.string.mobile_playback_channel_desc)
                .setSmallIconResourceId(R.drawable.ic_notification_play)
                .setMediaDescriptionAdapter(new PlayerNotificationManager.MediaDescriptionAdapter() {
                    @Override
                    public CharSequence getCurrentContentTitle(Player player) {
                        Video video = mPresenter != null ? mPresenter.getVideo() : null;
                        return video != null ? Helpers.toString(video.getTitleFull()) : "";
                    }

                    @Nullable
                    @Override
                    public PendingIntent createCurrentContentIntent(Player player) {
                        return contentIntent;
                    }

                    @Nullable
                    @Override
                    public CharSequence getCurrentContentText(Player player) {
                        Video video = mPresenter != null ? mPresenter.getVideo() : null;
                        return video != null ? video.getAuthor() : null;
                    }

                    @Nullable
                    @Override
                    public Bitmap getCurrentLargeIcon(Player player, PlayerNotificationManager.BitmapCallback callback) {
                        return loadArt(callback);
                    }
                })
                .setNotificationListener(notificationListener())
                .build();

        mNotificationManager.setUseNextAction(true);
        mNotificationManager.setUsePreviousAction(true);
        mNotificationManager.setUsePlayPauseActions(true);
        mNotificationManager.setUseStopAction(true);
        // media3 1.5+ dropped the MediaSessionCompat.Token overload; unwrap the platform token
        // (MediaSessionCompat.Token.getToken() returns the framework MediaSession.Token).
        mNotificationManager.setMediaSessionToken(
                (android.media.session.MediaSession.Token) mMediaSession.getSessionToken().getToken());

        // Posts the notification now (if media already loaded) and on every subsequent player event.
        mNotificationManager.setPlayer(mNotificationPlayer);
    }

    PlayerNotificationManager.NotificationListener notificationListener() {
        final int generation = mNotificationGeneration;
        return new PlayerNotificationManager.NotificationListener() {
            @Override
            public void onNotificationPosted(int notificationId, Notification notification, boolean ongoing) {
                if (generation != mNotificationGeneration) return;
                if (ongoing || isPlaybackRequested()) {
                    promote(notificationId, notification);
                } else {
                    // Paused: keep the notification but drop foreground state (dismissible).
                    ServiceCompat.stopForeground(MobilePlaybackService.this, ServiceCompat.STOP_FOREGROUND_DETACH);
                    mIsForeground = false;
                }
            }

            @Override
            public void onNotificationCancelled(int notificationId, boolean dismissedByUser) {
                if (generation != mNotificationGeneration) return;
                notificationCancelled(dismissedByUser);
            }
        };
    }

    /** Detach + tear down media session and notification. Called before the player is released. */
    public void detachPlayer() {
        releaseInternal(true);
        mController = null;
        mInitializer = null;
    }

    /**
     * Foreground-recovery hook (called from the Activity's onResume): if a background
     * {@code startForeground} was rejected (see {@code onNotificationPosted}), re-post the current
     * notification now that the app is in the foreground - the promotion is retried and succeeds.
     */
    public void ensureForeground() {
        if (!mIsForeground) {
            if (isPlaybackRequested()) showPreparingNotification();
            if (mNotificationManager != null) mNotificationManager.invalidate();
        }
    }

    private void releaseInternal(boolean removeNotification) {
        mNotificationGeneration++;
        PlayerNotificationManager manager = mNotificationManager;
        mNotificationManager = null;
        if (manager != null) manager.setPlayer(null);
        if (mPlayer != null && mSessionSyncListener != null) {
            mPlayer.removeListener(mSessionSyncListener);
        }
        mSessionSyncListener = null;
        if (mMediaSession != null) {
            mMediaSession.setActive(false);
            mMediaSession.release();
            mMediaSession = null;
        }
        if (removeNotification && mIsForeground) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
            mIsForeground = false;
        }
        // Invalidate callbacks before cancelling their requests.
        mArtwork.clear();
        // Release the Glide target so the loaded bitmap + its request can be collected.
        if (mArtTarget != null) {
            try {
                Glide.with(getApplicationContext()).clear(mArtTarget);
            } catch (Exception e) {
                // ignore
            }
            mArtTarget = null;
        }
        mPresenter = null;
        mPlayer = null;
        mNotificationPlayer = null;
    }

    @Override
    public void onDestroy() {
        traceLifecycle("destroyed");
        if (sRunning.get() == this) sRunning.clear();
        if (mDetachedView != null) stopDetachedPlayback();
        releaseInternal(true);
        super.onDestroy();
    }

    /** Push the current player state + metadata into the (lock-screen) media session. */
    private void syncSession() {
        if (mMediaSession == null || mPlayer == null) {
            return;
        }

        int sessionState;
        switch (mPlayer.getPlaybackState()) {
            case Player.STATE_BUFFERING:
                sessionState = PlaybackStateCompat.STATE_BUFFERING;
                break;
            case Player.STATE_READY:
                sessionState = mPlayer.getPlayWhenReady()
                        ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
                break;
            case Player.STATE_ENDED:
                sessionState = PlaybackStateCompat.STATE_STOPPED;
                break;
            case Player.STATE_IDLE:
            default:
                sessionState = PlaybackStateCompat.STATE_NONE;
                break;
        }

        mMediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                .setActions(SESSION_ACTIONS)
                .setState(sessionState, mPlayer.getCurrentPosition(), mPlayer.getPlaybackParameters().speed)
                .setBufferedPosition(mPlayer.getBufferedPosition())
                .build());
        mMediaSession.setMetadata(buildMetadata());
    }

    private MediaMetadataCompat buildMetadata() {
        Video video = mPresenter != null ? mPresenter.getVideo() : null;
        if (video == null) {
            return null;
        }

        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();
        builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, video.getTitleFull());
        builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, video.getTitleFull());
        builder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, video.getAuthor());
        builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE,
                Helpers.toString(video.getSecondTitleFull()));
        builder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, video.getCardImageUrl());
        Bitmap art = mArtwork.get(artworkUrl(video));
        if (art != null) {
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art);
        }
        // Duration for the lock-screen / media-control scrubber. The player returns C.TIME_UNSET
        // (negative) until the timeline is known, so only publish a real positive duration.
        long durationMs = mPlayer != null ? mPlayer.getDuration() : 0;
        if (durationMs > 0) {
            builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs);
        }
        return builder.build();
    }

    /** Load the current video thumbnail as the notification/lock-screen large icon (async, cached). */
    @Nullable
    private Bitmap loadArt(PlayerNotificationManager.BitmapCallback callback) {
        Video video = mPresenter != null ? mPresenter.getVideo() : null;
        String url = artworkUrl(video);

        if (TextUtils.isEmpty(url)) {
            mArtwork.clear();
            if (mArtTarget != null) {
                Glide.with(getApplicationContext()).clear(mArtTarget);
                mArtTarget = null;
            }
            return null;
        }

        // Serve the cached bitmap synchronously for the same video.
        Bitmap art = mArtwork.get(url);
        if (art != null) {
            return art;
        }

        // Each notification update supplies a new callback (older ones can be ignored by Media3).
        // Keep the latest callback while the same URL is loading instead of restarting its request.
        final long request = mArtwork.request(url, callback::onBitmap);
        if (request == 0) {
            return null;
        }

        // Cancel any in-flight art load before starting a new one (avoids leaking the target).
        if (mArtTarget != null) {
            Glide.with(getApplicationContext()).clear(mArtTarget);
            mArtTarget = null;
        }

        // Bounded target size + .override() so Glide decodes a downscaled bitmap, not SIZE_ORIGINAL.
        mArtTarget = new CustomTarget<Bitmap>(ART_SIZE_PX, ART_SIZE_PX) {
            @Override
            public void onResourceReady(Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                Video selectedVideo = mPresenter != null ? mPresenter.getVideo() : null;
                if (mArtwork.complete(request, artworkUrl(selectedVideo), resource)) {
                    // Artwork can arrive after the last player event. Publish it to the lock screen
                    // now, with the same video identity check used by the notification cache.
                    if (mMediaSession != null) {
                        mMediaSession.setMetadata(buildMetadata());
                    }
                }
            }

            @Override
            public void onLoadFailed(@Nullable android.graphics.drawable.Drawable errorDrawable) {
                mArtwork.fail(request);
            }

            @Override
            public void onLoadCleared(@Nullable android.graphics.drawable.Drawable placeholder) {
                // no-op
            }
        };

        Glide.with(getApplicationContext())
                .asBitmap()
                .load(url)
                .override(ART_SIZE_PX, ART_SIZE_PX)
                .into(mArtTarget);

        return null;
    }

    private static String artworkUrl(Video video) {
        return ClickbaitRemover.fitThumbnail(video != null ? video.getCardImageUrl() : null,
                ART_SIZE_PX);
    }
}
