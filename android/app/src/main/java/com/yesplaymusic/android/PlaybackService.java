package com.yesplaymusic.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlaybackService extends Service {
    public static final String ACTION_SET_METADATA = "com.yesplaymusic.android.SET_METADATA";
    public static final String ACTION_SET_PLAYBACK_STATE = "com.yesplaymusic.android.SET_PLAYBACK_STATE";
    public static final String ACTION_SET_SOURCE = "com.yesplaymusic.android.SET_SOURCE";
    public static final String ACTION_SET_VOLUME = "com.yesplaymusic.android.SET_VOLUME";
    public static final String ACTION_APP_PLAY = "com.yesplaymusic.android.APP_PLAY";
    public static final String ACTION_APP_PAUSE = "com.yesplaymusic.android.APP_PAUSE";
    public static final String ACTION_APP_STOP = "com.yesplaymusic.android.APP_STOP";
    public static final String ACTION_APP_SEEK = "com.yesplaymusic.android.APP_SEEK";
    public static final String ACTION_MEDIA_EVENT = "com.yesplaymusic.android.MEDIA_EVENT";
    public static final String EXTRA_MEDIA_ACTION = "mediaAction";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_ARTIST = "artist";
    public static final String EXTRA_ALBUM = "album";
    public static final String EXTRA_ARTWORK = "artwork";
    public static final String EXTRA_DURATION = "duration";
    public static final String EXTRA_POSITION = "position";
    public static final String EXTRA_PLAYING = "playing";
    public static final String EXTRA_SOURCE = "source";
    public static final String EXTRA_VOLUME = "volume";
    public static final String EXTRA_ERROR_CODE = "errorCode";

    private static final String ACTION_PLAY = "com.yesplaymusic.android.PLAY";
    private static final String ACTION_PAUSE = "com.yesplaymusic.android.PAUSE";
    private static final String ACTION_PREVIOUS = "com.yesplaymusic.android.PREVIOUS";
    private static final String ACTION_NEXT = "com.yesplaymusic.android.NEXT";
    private static final String ACTION_STOP = "com.yesplaymusic.android.STOP";
    private static final String CHANNEL_ID = "yesplaymusic_playback";
    private static final int NOTIFICATION_ID = 2202;
    private static PlaybackService activeInstance;

    private final ExecutorService artworkExecutor = Executors.newSingleThreadExecutor();
    private MediaSession mediaSession;
    private MediaPlayer mediaPlayer;
    private PowerManager.WakeLock wakeLock;
    private String title = "YesPlayMusic";
    private String artist = "";
    private String album = "";
    private String artworkUrl = "";
    private Bitmap artwork;
    private long durationMs = 0L;
    private long positionMs = 0L;
    private boolean playing = false;
    private boolean playerPrepared = false;
    private boolean playWhenPrepared = false;
    private String source = "";
    private float volume = 1.0f;

    @Override
    public void onCreate() {
        super.onCreate();
        activeInstance = this;
        createNotificationChannel();
        mediaSession = new MediaSession(this, "YesPlayMusic");
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                playNative();
                dispatchMediaAction("play");
            }

            @Override
            public void onPause() {
                pauseNative();
                dispatchMediaAction("pause");
            }

            @Override
            public void onSkipToPrevious() {
                dispatchMediaAction("previous");
            }

            @Override
            public void onSkipToNext() {
                dispatchMediaAction("next");
            }

            @Override
            public void onStop() {
                stopNative();
                dispatchMediaAction("stop");
            }

            @Override
            public void onSeekTo(long position) {
                seekNative(position);
                dispatchSeekAction(position);
            }
        });
        mediaSession.setActive(true);

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            getPackageName() + ":playback"
        );
        wakeLock.setReferenceCounted(false);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            );
        } else {
            startForeground(NOTIFICATION_ID, buildNotification());
        }

        if (intent == null || intent.getAction() == null) {
            return START_STICKY;
        }

        switch (intent.getAction()) {
            case ACTION_SET_METADATA:
                title = intent.getStringExtra(EXTRA_TITLE);
                artist = intent.getStringExtra(EXTRA_ARTIST);
                album = intent.getStringExtra(EXTRA_ALBUM);
                durationMs = secondsToMillis(intent.getDoubleExtra(EXTRA_DURATION, 0.0));
                String newArtworkUrl = intent.getStringExtra(EXTRA_ARTWORK);
                if (newArtworkUrl != null && !newArtworkUrl.equals(artworkUrl)) {
                    artworkUrl = newArtworkUrl;
                    artwork = null;
                    loadArtwork(artworkUrl);
                }
                updateMetadata();
                break;
            case ACTION_SET_PLAYBACK_STATE:
                long newDuration = secondsToMillis(intent.getDoubleExtra(EXTRA_DURATION, 0.0));
                if (newDuration > 0) {
                    durationMs = newDuration;
                }
                if (mediaPlayer == null) {
                    playing = intent.getBooleanExtra(EXTRA_PLAYING, false);
                    positionMs = secondsToMillis(
                        intent.getDoubleExtra(EXTRA_POSITION, 0.0)
                    );
                }
                updatePlaybackState();
                updateWakeLock();
                break;
            case ACTION_SET_SOURCE:
                setSource(
                    intent.getStringExtra(EXTRA_SOURCE),
                    (float) intent.getDoubleExtra(EXTRA_VOLUME, 1.0)
                );
                break;
            case ACTION_SET_VOLUME:
                setVolume((float) intent.getDoubleExtra(EXTRA_VOLUME, 1.0));
                break;
            case ACTION_APP_PLAY:
                playNative();
                break;
            case ACTION_APP_PAUSE:
                pauseNative();
                break;
            case ACTION_APP_STOP:
                stopNative();
                break;
            case ACTION_APP_SEEK:
                seekNative(
                    secondsToMillis(intent.getDoubleExtra(EXTRA_POSITION, 0.0))
                );
                break;
            case ACTION_PLAY:
                playNative();
                dispatchMediaAction("play");
                break;
            case ACTION_PAUSE:
                pauseNative();
                dispatchMediaAction("pause");
                break;
            case ACTION_PREVIOUS:
                dispatchMediaAction("previous");
                break;
            case ACTION_NEXT:
                dispatchMediaAction("next");
                break;
            case ACTION_STOP:
                stopNative();
                dispatchMediaAction("stop");
                break;
            default:
                break;
        }

        updateNotification();
        return START_STICKY;
    }

    private long secondsToMillis(double seconds) {
        return Math.max(0L, Math.round(seconds * 1000.0));
    }

    private void setSource(String value, float requestedVolume) {
        String nextSource = value == null ? "" : value;
        setVolume(requestedVolume);
        if (nextSource.isEmpty()) {
            return;
        }
        if (nextSource.equals(source) && mediaPlayer != null) {
            return;
        }

        releaseMediaPlayer();
        source = nextSource;
        positionMs = 0L;
        playing = false;
        playerPrepared = false;
        playWhenPrepared = false;

        MediaPlayer player = new MediaPlayer();
        mediaPlayer = player;
        player.setAudioAttributes(
            new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        );
        player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
        player.setVolume(volume, volume);
        player.setOnPreparedListener(preparedPlayer -> {
            if (preparedPlayer != mediaPlayer) {
                return;
            }
            playerPrepared = true;
            long playerDuration = preparedPlayer.getDuration();
            if (playerDuration > 0) {
                durationMs = playerDuration;
                updateMetadata();
            }
            if (positionMs > 0) {
                preparedPlayer.seekTo((int) Math.min(positionMs, Integer.MAX_VALUE));
            }
            if (playWhenPrepared) {
                preparedPlayer.start();
                playing = true;
            }
            updatePlaybackState();
            updateWakeLock();
            updateNotification();
        });
        player.setOnCompletionListener(completedPlayer -> {
            if (completedPlayer != mediaPlayer) {
                return;
            }
            positionMs = durationMs;
            playing = false;
            playWhenPrepared = false;
            updatePlaybackState();
            updateWakeLock();
            updateNotification();
            dispatchMediaAction("ended");
        });
        player.setOnErrorListener((failedPlayer, what, extra) -> {
            if (failedPlayer != mediaPlayer) {
                return true;
            }
            playing = false;
            playWhenPrepared = false;
            releaseMediaPlayer();
            updatePlaybackState();
            updateWakeLock();
            updateNotification();
            dispatchError(what != 0 ? what : extra);
            return true;
        });

        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "Mozilla/5.0 (Linux; Android) YesPlayMusic");
            headers.put("Referer", "https://music.163.com/");
            player.setDataSource(this, Uri.parse(source), headers);
            player.prepareAsync();
        } catch (Exception error) {
            releaseMediaPlayer();
            dispatchError(MediaPlayer.MEDIA_ERROR_UNKNOWN);
        }
        updatePlaybackState();
        updateNotification();
    }

    private void playNative() {
        playWhenPrepared = true;
        if (mediaPlayer != null && playerPrepared) {
            try {
                mediaPlayer.start();
                playing = true;
                positionMs = getCurrentPositionMs();
            } catch (IllegalStateException ignored) {
                playing = false;
            }
        } else {
            playing = mediaPlayer != null;
        }
        updatePlaybackState();
        updateWakeLock();
        updateNotification();
    }

    private void pauseNative() {
        playWhenPrepared = false;
        positionMs = getCurrentPositionMs();
        if (mediaPlayer != null && playerPrepared) {
            try {
                mediaPlayer.pause();
            } catch (IllegalStateException ignored) {
                // The player was reset while the action was being delivered.
            }
        }
        playing = false;
        updatePlaybackState();
        updateWakeLock();
        updateNotification();
    }

    private void stopNative() {
        playWhenPrepared = false;
        if (mediaPlayer != null && playerPrepared) {
            try {
                mediaPlayer.pause();
                mediaPlayer.seekTo(0);
            } catch (IllegalStateException ignored) {
                // The player was reset while the action was being delivered.
            }
        }
        positionMs = 0L;
        playing = false;
        updatePlaybackState();
        updateWakeLock();
        updateNotification();
    }

    private void seekNative(long requestedPositionMs) {
        long upperBound = durationMs > 0 ? durationMs : Long.MAX_VALUE;
        positionMs = Math.max(0L, Math.min(requestedPositionMs, upperBound));
        if (mediaPlayer != null && playerPrepared) {
            try {
                mediaPlayer.seekTo((int) Math.min(positionMs, Integer.MAX_VALUE));
            } catch (IllegalStateException ignored) {
                // Keep the pending position for the prepared callback.
            }
        }
        updatePlaybackState();
        updateNotification();
    }

    private void setVolume(float requestedVolume) {
        volume = Math.max(0.0f, Math.min(1.0f, requestedVolume));
        if (mediaPlayer != null) {
            mediaPlayer.setVolume(volume, volume);
        }
    }

    private long getCurrentPositionMs() {
        if (mediaPlayer != null && playerPrepared) {
            try {
                return Math.max(0L, mediaPlayer.getCurrentPosition());
            } catch (IllegalStateException ignored) {
                // Fall through to the last position reported by the player.
            }
        }
        return positionMs;
    }

    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.reset();
            } catch (IllegalStateException ignored) {
                // The player may already be in the idle state.
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
        playerPrepared = false;
    }

    private void dispatchMediaAction(String action) {
        Intent event = new Intent(ACTION_MEDIA_EVENT);
        event.setPackage(getPackageName());
        event.putExtra(EXTRA_MEDIA_ACTION, action);
        event.putExtra(EXTRA_POSITION, getCurrentPositionMs() / 1000.0);
        event.putExtra(EXTRA_PLAYING, playing);
        event.putExtra(EXTRA_SOURCE, source);
        sendBroadcast(event);
    }

    private void dispatchSeekAction(long seekPositionMs) {
        Intent event = new Intent(ACTION_MEDIA_EVENT);
        event.setPackage(getPackageName());
        event.putExtra(EXTRA_MEDIA_ACTION, "seek");
        event.putExtra(
            EXTRA_POSITION,
            Math.max(0L, seekPositionMs) / 1000.0
        );
        event.putExtra(EXTRA_PLAYING, playing);
        event.putExtra(EXTRA_SOURCE, source);
        sendBroadcast(event);
    }

    private void dispatchError(int errorCode) {
        Intent event = new Intent(ACTION_MEDIA_EVENT);
        event.setPackage(getPackageName());
        event.putExtra(EXTRA_MEDIA_ACTION, "error");
        event.putExtra(EXTRA_ERROR_CODE, errorCode);
        event.putExtra(EXTRA_POSITION, getCurrentPositionMs() / 1000.0);
        event.putExtra(EXTRA_PLAYING, false);
        event.putExtra(EXTRA_SOURCE, source);
        sendBroadcast(event);
    }

    private void updateMetadata() {
        MediaMetadata.Builder builder = new MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, album)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs);
        if (artwork != null) {
            builder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artwork);
            builder.putBitmap(MediaMetadata.METADATA_KEY_ART, artwork);
        }
        mediaSession.setMetadata(builder.build());
    }

    private void updatePlaybackState() {
        long actions =
            PlaybackState.ACTION_PLAY |
            PlaybackState.ACTION_PAUSE |
            PlaybackState.ACTION_PLAY_PAUSE |
            PlaybackState.ACTION_SKIP_TO_PREVIOUS |
            PlaybackState.ACTION_SKIP_TO_NEXT |
            PlaybackState.ACTION_SEEK_TO |
            PlaybackState.ACTION_STOP;
        int state = playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
        float speed = playing ? 1.0f : 0.0f;
        PlaybackState playbackState = new PlaybackState.Builder()
            .setActions(actions)
            .setState(
                state,
                getCurrentPositionMs(),
                speed,
                SystemClock.elapsedRealtime()
            )
            .build();
        mediaSession.setPlaybackState(playbackState);
    }

    private void updateWakeLock() {
        if (playing && !wakeLock.isHeld()) {
            wakeLock.acquire();
        } else if (!playing && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    private Notification buildNotification() {
        Intent activityIntent = new Intent(this, MainActivity.class);
        activityIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? new Notification.Builder(this, CHANNEL_ID)
            : new Notification.Builder(this);
        builder
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title == null || title.isEmpty() ? "YesPlayMusic" : title)
            .setContentText(artist == null ? "" : artist)
            .setSubText(album == null ? "" : album)
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(playing)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .addAction(
                new Notification.Action.Builder(
                    android.R.drawable.ic_media_previous,
                    "Previous",
                    serviceAction(ACTION_PREVIOUS, 1)
                ).build()
            )
            .addAction(
                new Notification.Action.Builder(
                    playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                    playing ? "Pause" : "Play",
                    serviceAction(playing ? ACTION_PAUSE : ACTION_PLAY, 2)
                ).build()
            )
            .addAction(
                new Notification.Action.Builder(
                    android.R.drawable.ic_media_next,
                    "Next",
                    serviceAction(ACTION_NEXT, 3)
                ).build()
            )
            .setStyle(
                new Notification.MediaStyle()
                    .setMediaSession(mediaSession.getSessionToken())
                    .setShowActionsInCompactView(0, 1, 2)
            );
        if (artwork != null) {
            builder.setLargeIcon(artwork);
        }
        return builder.build();
    }

    private PendingIntent serviceAction(String action, int requestCode) {
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(action);
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private void updateNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(NOTIFICATION_ID, buildNotification());
    }

    private void loadArtwork(String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        artworkExecutor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(value);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setInstanceFollowRedirects(true);
                try (InputStream input = connection.getInputStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(input);
                    if (bitmap != null && value.equals(artworkUrl)) {
                        artwork = bitmap;
                        updateMetadata();
                        updateNotification();
                    }
                }
            } catch (Exception ignored) {
                // Artwork is optional; playback controls still work without it.
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID,
            "音乐播放",
            NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("显示当前歌曲和播放控制");
        channel.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (!playing) {
            stopSelf();
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        if (activeInstance == this) {
            activeInstance = null;
        }
        artworkExecutor.shutdownNow();
        releaseMediaPlayer();
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }
        super.onDestroy();
    }

    public static StateSnapshot getStateSnapshot() {
        PlaybackService service = activeInstance;
        if (service == null) {
            return null;
        }
        return new StateSnapshot(
            service.source,
            service.playing,
            service.getCurrentPositionMs() / 1000.0,
            service.durationMs / 1000.0
        );
    }

    public static class StateSnapshot {
        public final String source;
        public final boolean playing;
        public final double positionSeconds;
        public final double durationSeconds;

        StateSnapshot(
            String source,
            boolean playing,
            double positionSeconds,
            double durationSeconds
        ) {
            this.source = source;
            this.playing = playing;
            this.positionSeconds = positionSeconds;
            this.durationSeconds = durationSeconds;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
