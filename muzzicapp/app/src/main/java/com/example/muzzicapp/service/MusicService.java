package com.example.muzzicapp.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.example.muzzicapp.MainActivity;
import com.example.muzzicapp.MusicPlayerManager;
import com.example.muzzicapp.R;
import com.example.muzzicapp.model.Track;

public class MusicService extends Service implements MusicPlayerManager.OnPlaybackChangeListener {

    public static final String ACTION_PLAY = "com.example.muzzicapp.ACTION_PLAY";
    public static final String ACTION_PAUSE = "com.example.muzzicapp.ACTION_PAUSE";
    public static final String ACTION_PLAY_PAUSE = "com.example.muzzicapp.ACTION_PLAY_PAUSE";
    public static final String ACTION_NEXT = "com.example.muzzicapp.ACTION_NEXT";
    public static final String ACTION_PREV = "com.example.muzzicapp.ACTION_PREV";
    public static final String ACTION_STOP = "com.example.muzzicapp.ACTION_STOP";
    public static final String ACTION_UPDATE = "com.example.muzzicapp.ACTION_UPDATE";

    public static final String CHANNEL_ID = "muzzic_playback_channel";
    public static final int NOTIFICATION_ID = 1001;

    private MediaSessionCompat mediaSession;
    private PowerManager.WakeLock wakeLock;
    private String currentTrackKey;
    private String currentArtworkUrl;
    private Bitmap currentArtworkBitmap;
    private Target<Bitmap> currentGlideTarget;
    private Bitmap defaultArtBitmap;

    public static void startOrUpdate(Context context) {
        if (context == null) return;
        Intent intent = new Intent(context, MusicService.class);
        intent.setAction(ACTION_UPDATE);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception e) {
            android.util.Log.e("MusicService", "Failed to start service: " + e.getMessage());
        }
    }

    public static void stop(Context context) {
        if (context == null) return;
        Intent intent = new Intent(context, MusicService.class);
        intent.setAction(ACTION_STOP);
        try {
            context.startService(intent);
        } catch (Exception ignored) {}
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        setupMediaSession();
        try {
            defaultArtBitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
        } catch (Exception ignored) {}

        MusicPlayerManager manager = MusicPlayerManager.getInstance();
        if (manager != null) {
            manager.addListener(this);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            MusicPlayerManager manager = MusicPlayerManager.getInstance();
            if (manager != null) {
                switch (action) {
                    case ACTION_PLAY:
                        manager.play();
                        break;
                    case ACTION_PAUSE:
                        manager.pause();
                        break;
                    case ACTION_PLAY_PAUSE:
                        manager.togglePlayPause();
                        break;
                    case ACTION_NEXT:
                        manager.nextTrack();
                        break;
                    case ACTION_PREV:
                        manager.previousTrack();
                        break;
                    case ACTION_STOP:
                        manager.pause();
                        stopForegroundService();
                        return START_NOT_STICKY;
                    case ACTION_UPDATE:
                    default:
                        break;
                }
            }
        }

        updateNotification();
        return START_STICKY;
    }

    private void setupMediaSession() {
        mediaSession = new MediaSessionCompat(this, "MuzzicMediaSession");
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                MusicPlayerManager manager = MusicPlayerManager.getInstance();
                if (manager != null) manager.play();
            }

            @Override
            public void onPause() {
                MusicPlayerManager manager = MusicPlayerManager.getInstance();
                if (manager != null) manager.pause();
            }

            @Override
            public void onSkipToNext() {
                MusicPlayerManager manager = MusicPlayerManager.getInstance();
                if (manager != null) manager.nextTrack();
            }

            @Override
            public void onSkipToPrevious() {
                MusicPlayerManager manager = MusicPlayerManager.getInstance();
                if (manager != null) manager.previousTrack();
            }

            @Override
            public void onSeekTo(long pos) {
                MusicPlayerManager manager = MusicPlayerManager.getInstance();
                if (manager != null) manager.seekTo((int) pos);
            }

            @Override
            public void onStop() {
                MusicPlayerManager manager = MusicPlayerManager.getInstance();
                if (manager != null) manager.pause();
                stopForegroundService();
            }
        });
        mediaSession.setActive(true);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Phát nhạc Muzzic",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Thanh trạng thái điều khiển bài hát");
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private synchronized void updateNotification() {
        updateNotification(null);
    }

    private synchronized void updateNotification(@Nullable Track overrideTrack) {
        MusicPlayerManager manager = MusicPlayerManager.getInstance();
        Track track = overrideTrack != null ? overrideTrack : (manager != null ? manager.getCurrentTrack() : null);
        boolean isPlaying = manager != null && manager.isPlaying();

        if (track == null) {
            stopForegroundService();
            return;
        }

        // Manage CPU WakeLock for screen-off uninterrupted playback
        if (isPlaying) {
            acquireWakeLock();
        } else {
            releaseWakeLock();
        }

        String newTrackKey = track.getTrackKey();
        boolean isNewTrack = currentTrackKey == null || !currentTrackKey.equals(newTrackKey);

        if (isNewTrack) {
            currentTrackKey = newTrackKey;
            // Clear any pending image loading from the previous track
            if (currentGlideTarget != null) {
                try {
                    Glide.with(getApplicationContext()).clear(currentGlideTarget);
                } catch (Exception ignored) {}
                currentGlideTarget = null;
            }
            // Reset current artwork so previous track's image NEVER leaks into the new track
            currentArtworkBitmap = null;
            currentArtworkUrl = null;
        }

        String artworkUrl = track.getHighResArtworkUrl() != null && !track.getHighResArtworkUrl().trim().isEmpty()
                ? track.getHighResArtworkUrl()
                : track.getArtworkUrl();
        if (artworkUrl != null) artworkUrl = artworkUrl.trim();

        if (artworkUrl == null || artworkUrl.isEmpty()) {
            currentArtworkBitmap = null;
            currentArtworkUrl = null;
            publishNotification(track, isPlaying, null);
        } else if (artworkUrl.equals(currentArtworkUrl) && currentArtworkBitmap != null) {
            publishNotification(track, isPlaying, currentArtworkBitmap);
        } else {
            currentArtworkUrl = artworkUrl;
            // Publish immediately with default artwork placeholder so old track art is NEVER displayed
            publishNotification(track, isPlaying, null);
            loadArtworkAndPublish(track, isPlaying, artworkUrl, newTrackKey);
        }
    }

    private void loadArtworkAndPublish(Track track, boolean isPlaying, String artworkUrl, String expectedTrackKey) {
        final String expectedUrl = artworkUrl;

        currentGlideTarget = Glide.with(getApplicationContext())
                .asBitmap()
                .load(artworkUrl)
                .into(new CustomTarget<Bitmap>(512, 512) {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                        // Ensure this callback matches the current track and URL
                        if (expectedTrackKey.equals(currentTrackKey) && expectedUrl.equals(currentArtworkUrl)) {
                            currentArtworkBitmap = resource;
                            currentGlideTarget = null;
                            MusicPlayerManager mgr = MusicPlayerManager.getInstance();
                            publishNotification(track, mgr != null && mgr.isPlaying(), currentArtworkBitmap);
                        }
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                        if (expectedTrackKey.equals(currentTrackKey)) {
                            currentArtworkBitmap = null;
                        }
                    }

                    @Override
                    public void onLoadFailed(@Nullable Drawable errorDrawable) {
                        if (expectedTrackKey.equals(currentTrackKey)) {
                            currentArtworkBitmap = null;
                            currentGlideTarget = null;
                            MusicPlayerManager mgr = MusicPlayerManager.getInstance();
                            publishNotification(track, mgr != null && mgr.isPlaying(), null);
                        }
                    }
                });
    }

    private void publishNotification(Track track, boolean isPlaying, @Nullable Bitmap artwork) {
        if (mediaSession == null || track == null) return;

        MusicPlayerManager manager = MusicPlayerManager.getInstance();
        String title = track.getTitle() != null ? track.getTitle() : "Muzzic Player";
        String artist = track.getArtist() != null ? track.getArtist() : "Đang phát nhạc";

        Bitmap artBitmap = artwork != null ? artwork : defaultArtBitmap;

        // Update MediaSession metadata (for system media notification and lockscreen controls)
        MediaMetadataCompat.Builder metaBuilder = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "Muzzic");
        if (track.getDurationMillis() > 0) {
            metaBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, track.getDurationMillis());
        }
        if (artBitmap != null) {
            metaBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artBitmap)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, artBitmap)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, artBitmap);
        }
        mediaSession.setMetadata(metaBuilder.build());

        // Update MediaSession playback state
        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE |
                        PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS | PlaybackStateCompat.ACTION_SEEK_TO |
                        PlaybackStateCompat.ACTION_STOP)
                .setState(isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
                        manager != null ? manager.getCurrentPosition() : 0, 1.0f);
        mediaSession.setPlaybackState(stateBuilder.build());

        // Build notification
        Intent openAppIntent = new Intent(this, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        openAppIntent.putExtra("from_notification", true);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        PendingIntent prevIntent = getActionPendingIntent(ACTION_PREV, 1);
        PendingIntent playPauseIntent = getActionPendingIntent(ACTION_PLAY_PAUSE, 2);
        PendingIntent nextIntent = getActionPendingIntent(ACTION_NEXT, 3);
        PendingIntent stopIntent = getActionPendingIntent(ACTION_STOP, 4);

        int playPauseIcon = isPlaying ? R.drawable.ic_pause : R.drawable.ic_play;
        String playPauseTitle = isPlaying ? "Tạm dừng" : "Phát";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(artist)
                .setSubText("Muzzic")
                .setContentIntent(contentIntent)
                .setDeleteIntent(stopIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(isPlaying)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
                .setShowWhen(false)
                .addAction(R.drawable.ic_previous, "Bài trước", prevIntent)
                .addAction(playPauseIcon, playPauseTitle, playPauseIntent)
                .addAction(R.drawable.ic_next, "Bài tiếp theo", nextIntent)
                .addAction(R.drawable.ic_close, "Đóng", stopIntent)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2)
                        .setShowCancelButton(true)
                        .setCancelButtonIntent(stopIntent)
                );

        if (artBitmap != null) {
            builder.setLargeIcon(artBitmap);
        }

        Notification notification = builder.build();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            android.util.Log.e("MusicService", "startForeground error: " + e.getMessage());
        }

        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify(NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            android.util.Log.e("MusicService", "NotificationManager.notify error: " + e.getMessage());
        }
    }

    private PendingIntent getActionPendingIntent(String action, int requestCode) {
        Intent intent = new Intent(this, MusicService.class);
        intent.setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getService(this, requestCode, intent, flags);
    }

    private void acquireWakeLock() {
        if (wakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Muzzic:PlaybackWakeLock");
                wakeLock.setReferenceCounted(false);
            }
        }
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire(6 * 60 * 60 * 1000L); // 6 hours
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    private void stopForegroundService() {
        releaseWakeLock();
        currentArtworkBitmap = null;
        currentArtworkUrl = null;
        currentTrackKey = null;
        if (currentGlideTarget != null) {
            try {
                Glide.with(getApplicationContext()).clear(currentGlideTarget);
            } catch (Exception ignored) {}
            currentGlideTarget = null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(NOTIFICATION_ID);
        }
        stopSelf();
    }

    @Override
    public void onTrackChanged(Track track) {
        updateNotification(track);
    }

    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        updateNotification();
    }

    @Override
    public void onProgressUpdated(int currentPos, int duration) {
        // Ignored for notification updates to prevent overhead
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        releaseWakeLock();
        currentArtworkBitmap = null;
        currentArtworkUrl = null;
        currentTrackKey = null;
        if (currentGlideTarget != null) {
            try {
                Glide.with(getApplicationContext()).clear(currentGlideTarget);
            } catch (Exception ignored) {}
            currentGlideTarget = null;
        }
        MusicPlayerManager manager = MusicPlayerManager.getInstance();
        if (manager != null) {
            manager.removeListener(this);
        }
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
