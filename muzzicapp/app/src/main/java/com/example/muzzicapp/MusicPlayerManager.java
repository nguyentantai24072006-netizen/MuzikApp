package com.example.muzzicapp;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import com.example.muzzicapp.model.Track;
import com.example.muzzicapp.api.FullAudioResolver;
import com.example.muzzicapp.service.MusicService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Random;
import android.os.Handler;
import android.os.Looper;

import com.example.muzzicapp.model.Track;

public class MusicPlayerManager {
    
    private static MusicPlayerManager instance;
    private android.content.Context context;
    private ExoPlayer exoPlayer;
    private Track currentTrack;
    private boolean isPrepared = false;
    private boolean isPlaying = false;
    private boolean repeatOne = false;
    private boolean repeatQueue = false;
    private boolean shuffleEnabled = false;
    private int currentQueueIndex = -1;
    private final List<Track> queue = new ArrayList<>();
    private final FullAudioResolver fullAudioResolver = new FullAudioResolver();
    private int playbackRequestId = 0;
    private int trackRecoveryAttempts = 0;
    
    private Handler progressHandler;
    private Runnable progressRunnable;
    
    private final List<OnPlaybackChangeListener> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    public interface OnPlaybackChangeListener {
        void onTrackChanged(Track track);
        void onPlaybackStateChanged(boolean isPlaying);
        void onProgressUpdated(int currentPos, int duration);
    }

    private MusicPlayerManager(android.content.Context context) {
        this.context = context.getApplicationContext();

        java.util.Map<String, String> defaultHeaders = new java.util.HashMap<>();
        defaultHeaders.put("Referer", "https://zingmp3.vn/");
        defaultHeaders.put("Origin", "https://zingmp3.vn");

        androidx.media3.datasource.DefaultHttpDataSource.Factory httpDataSourceFactory =
                new androidx.media3.datasource.DefaultHttpDataSource.Factory()
                        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .setDefaultRequestProperties(defaultHeaders)
                        .setAllowCrossProtocolRedirects(true)
                        .setConnectTimeoutMs(15000)
                        .setReadTimeoutMs(15000);

        androidx.media3.datasource.cache.CacheDataSource.Factory cacheDataSourceFactory =
                new androidx.media3.datasource.cache.CacheDataSource.Factory()
                        .setCache(getMediaCache(this.context))
                        .setUpstreamDataSourceFactory(httpDataSourceFactory)
                        .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);

        androidx.media3.datasource.DefaultDataSource.Factory dataSourceFactory =
                new androidx.media3.datasource.DefaultDataSource.Factory(this.context, cacheDataSourceFactory);

        androidx.media3.exoplayer.source.DefaultMediaSourceFactory mediaSourceFactory =
                new androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory);

        androidx.media3.exoplayer.DefaultLoadControl loadControl = new androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                        15000, // minBufferMs (15s)
                        50000, // maxBufferMs (50s)
                        500,   // bufferForPlaybackMs (start in 500ms like Spotify!)
                        1000   // bufferForPlaybackAfterRebufferMs
                )
                .build();

        exoPlayer = new ExoPlayer.Builder(this.context)
                .setMediaSourceFactory(mediaSourceFactory)
                .setLoadControl(loadControl)
                .build();
        
        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    isPrepared = true;
                    trackRecoveryAttempts = 0;
                    int dur = (int) exoPlayer.getDuration();
                    if (dur <= 0 && currentTrack != null && currentTrack.getDurationMillis() > 0) {
                        dur = (int) currentTrack.getDurationMillis();
                    }
                    int pos = (int) exoPlayer.getCurrentPosition();
                    for (OnPlaybackChangeListener l : new ArrayList<>(listeners)) {
                        l.onProgressUpdated(pos, dur);
                        l.onPlaybackStateChanged(exoPlayer.isPlaying());
                    }
                } else if (playbackState == Player.STATE_ENDED) {
                    if (repeatOne) {
                        exoPlayer.seekTo(0);
                        exoPlayer.play();
                        isPlaying = true;
                        for (OnPlaybackChangeListener l : new ArrayList<>(listeners)) l.onPlaybackStateChanged(true);
                    } else {
                        nextTrack();
                    }
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlayingNow) {
                isPlaying = isPlayingNow;
                if (isPlaying) {
                    startProgressTracker();
                } else {
                    stopProgressTracker();
                }
                for (OnPlaybackChangeListener l : new ArrayList<>(listeners)) {
                    l.onPlaybackStateChanged(isPlaying);
                }
            }

            @Override
            public void onPlayerError(androidx.media3.common.PlaybackException error) {
                android.util.Log.e("MusicPlayerManager", "ExoPlayer playback error: " + error.getMessage(), error);

                // Auto-recovery: refresh audio URL or fallback to preview to prevent user-facing Source error
                if (currentTrack != null && trackRecoveryAttempts < 2) {
                    trackRecoveryAttempts++;
                    android.util.Log.w("MusicPlayerManager", "Attempting playback recovery attempt " + trackRecoveryAttempts + " for track: " + currentTrack.getTitle());

                    if (trackRecoveryAttempts == 1) {
                        currentTrack.setFullAudioUrl(null);
                        final int currentReq = playbackRequestId;
                        fullAudioResolver.resolve(currentTrack, true, new FullAudioResolver.Callback() {
                            @Override
                            public void onResolved(String freshUrl) {
                                if (currentReq != playbackRequestId) return;
                                android.util.Log.i("MusicPlayerManager", "Recovered fresh audio URL: " + freshUrl);
                                currentTrack.setFullAudioUrl(freshUrl);
                                startTrack(currentTrack);
                            }

                            @Override
                            public void onUnavailable(Exception err) {
                                if (currentReq != playbackRequestId) return;
                                android.util.Log.w("MusicPlayerManager", "Fresh resolve failed during recovery, trying preview fallback", err);
                                fallbackToPreviewOrError(error);
                            }
                        });
                        return;
                    } else if (trackRecoveryAttempts == 2) {
                        fallbackToPreviewOrError(error);
                        return;
                    }
                }

                handleTerminalPlaybackError(error);
            }
        });
        
        progressHandler = new Handler(Looper.getMainLooper());
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isPrepared || !isPlaying || exoPlayer == null) {
                    return;
                }
                int pos = (int) exoPlayer.getCurrentPosition();
                int dur = (int) exoPlayer.getDuration();
                if (dur <= 0 && currentTrack != null && currentTrack.getDurationMillis() > 0) {
                    dur = (int) currentTrack.getDurationMillis();
                }
                for (OnPlaybackChangeListener l : listeners) {
                    l.onProgressUpdated(pos, dur);
                }
                progressHandler.postDelayed(this, 100); // Standard 100ms progress update
            }
        };
    }
    
    private void startProgressTracker() {
        progressHandler.removeCallbacks(progressRunnable);
        progressHandler.post(progressRunnable);
    }
    
    private void stopProgressTracker() {
        progressHandler.removeCallbacks(progressRunnable);
    }

    private static androidx.media3.datasource.cache.SimpleCache mediaCache;

    public static synchronized androidx.media3.datasource.cache.SimpleCache getMediaCache(android.content.Context context) {
        if (mediaCache == null) {
            java.io.File cacheDir = new java.io.File(context.getCacheDir(), "muzzic_media_cache");
            androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor evictor =
                    new androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor(150 * 1024 * 1024); // 150 MB disk cache
            androidx.media3.database.StandaloneDatabaseProvider dbProvider =
                    new androidx.media3.database.StandaloneDatabaseProvider(context);
            mediaCache = new androidx.media3.datasource.cache.SimpleCache(cacheDir, evictor, dbProvider);
        }
        return mediaCache;
    }

    public static synchronized MusicPlayerManager getInstance(android.content.Context context) {
        if (instance == null) {
            instance = new MusicPlayerManager(context.getApplicationContext());
        }
        return instance;
    }
    
    // For legacy usages that don't pass context. Will throw NullPointerException if getInstance(context) was not called first.
    public static synchronized MusicPlayerManager getInstance() {
        return instance;
    }

    public void addListener(OnPlaybackChangeListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
        if (currentTrack != null) {
            listener.onTrackChanged(currentTrack);
            listener.onPlaybackStateChanged(isPlaying);
            if (exoPlayer != null && isPrepared) {
                int dur = (int) exoPlayer.getDuration();
                if (dur <= 0 && currentTrack.getDurationMillis() > 0) {
                    dur = (int) currentTrack.getDurationMillis();
                }
                listener.onProgressUpdated((int) exoPlayer.getCurrentPosition(), dur);
            }
        }
    }
    
    public void removeListener(OnPlaybackChangeListener listener) {
        listeners.remove(listener);
    }

    public void playTrack(Track track) {
        if (track == null) return;

        trackRecoveryAttempts = 0;
        if (FullAudioResolver.isUrlExpired(track.getFullAudioUrl())) {
            android.util.Log.i("MusicPlayerManager", "playTrack: Clearing expired audio URL before resolution");
            track.setFullAudioUrl(null);
        }

        int existingIndex = -1;
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).getTrackKey().equals(track.getTrackKey())) {
                existingIndex = i;
                break;
            }
        }
        if (existingIndex < 0) {
            queue.add(track);
            existingIndex = queue.size() - 1;
        }
        currentQueueIndex = existingIndex;
        int requestId = ++playbackRequestId;
        isPrepared = false;
        isPlaying = false;
        currentTrack = track;
        notifyTrackChanged(track);

        new Handler(Looper.getMainLooper()).post(() -> {
            android.widget.Toast.makeText(context, "Đang tải bài hát: " + track.getTitle() + "...", android.widget.Toast.LENGTH_SHORT).show();
        });

        fullAudioResolver.resolve(track, new FullAudioResolver.Callback() {
            @Override
            public void onResolved(String url) {
                if (requestId != playbackRequestId) return;
                track.setFullAudioUrl(url);
                startTrack(track);
            }

            @Override
            public void onUnavailable(Exception error) {
                if (requestId != playbackRequestId) return;
                
                // AUTO-FALLBACK: Use preview URL from iTunes if full source fails
                if (track.getPreviewUrl() != null && !track.getPreviewUrl().isEmpty()) {
                    track.setFullAudioUrl(track.getPreviewUrl());
                    startTrack(track);
                } else {
                    isPrepared = false;
                    isPlaying = false;
                    for (OnPlaybackChangeListener listener : new ArrayList<>(listeners)) {
                        listener.onPlaybackStateChanged(false);
                    }
                    new Handler(Looper.getMainLooper()).post(() -> {
                        android.widget.Toast.makeText(context, "Lỗi: Không tìm thấy nguồn phát cho bài hát này.", android.widget.Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private void fallbackToPreviewOrError(androidx.media3.common.PlaybackException error) {
        if (currentTrack != null && currentTrack.getPreviewUrl() != null && !currentTrack.getPreviewUrl().isEmpty()
                && !currentTrack.getPreviewUrl().equals(currentTrack.getFullAudioUrl())) {
            android.util.Log.i("MusicPlayerManager", "Falling back to preview stream for: " + currentTrack.getTitle());
            currentTrack.setFullAudioUrl(currentTrack.getPreviewUrl());
            startTrack(currentTrack);
            new Handler(Looper.getMainLooper()).post(() -> {
                android.widget.Toast.makeText(context, "Đang phát đoạn nghe thử của bài hát", android.widget.Toast.LENGTH_SHORT).show();
            });
        } else {
            handleTerminalPlaybackError(error);
        }
    }

    private void handleTerminalPlaybackError(androidx.media3.common.PlaybackException error) {
        isPlaying = false;
        isPrepared = false;
        stopProgressTracker();
        for (OnPlaybackChangeListener l : new ArrayList<>(listeners)) l.onPlaybackStateChanged(false);
        new Handler(Looper.getMainLooper()).post(() -> {
            android.widget.Toast.makeText(context, "Lỗi phát nhạc: " + (error.getMessage() != null ? error.getMessage() : "Không thể tải bài hát"), android.widget.Toast.LENGTH_SHORT).show();
        });
    }

    private void startTrack(Track track) {
        currentTrack = track;
        isPrepared = false;
        isPlaying = false;
        
        notifyTrackChanged(track);
        
        try {
            String playUrl = track.getPlaybackUrl();
            android.util.Log.e("MusicPlayerManager", "startTrack playbackUrl: " + playUrl);
            if (playUrl == null || (!playUrl.startsWith("http://") && !playUrl.startsWith("https://"))) {
                throw new Exception("URL phát nhạc không hợp lệ: " + playUrl);
            }
            exoPlayer.setMediaItem(MediaItem.fromUri(playUrl));
            exoPlayer.prepare();
            exoPlayer.play();
            isPlaying = true;
            isPrepared = true;
            startProgressTracker();
            for (OnPlaybackChangeListener l : listeners) l.onPlaybackStateChanged(true);
            MusicService.startOrUpdate(context);
        } catch (Exception e) {
            android.util.Log.e("MusicPlayerManager", "startTrack exception", e);
            for (OnPlaybackChangeListener l : listeners) l.onPlaybackStateChanged(false);
            new Handler(Looper.getMainLooper()).post(() -> {
                android.widget.Toast.makeText(context, "Lỗi: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void notifyTrackChanged(Track track) {
        if (track != null && context != null) {
            try {
                HistoryManager.getInstance(context).addTrack(track);
            } catch (Exception e) {
                android.util.Log.e("MusicPlayerManager", "Failed to record track history", e);
            }
        }
        for (OnPlaybackChangeListener listener : new ArrayList<>(listeners)) {
            listener.onTrackChanged(track);
            listener.onPlaybackStateChanged(false);
        }
    }

    public void togglePlayPause() {
        if (!isPrepared) {
            if (currentTrack != null) {
                playTrack(currentTrack);
            }
            return;
        }
        if (exoPlayer.isPlaying()) {
            pause();
        } else {
            play();
        }
    }

    public void seekTo(int positionMs) {
        if (isPrepared) {
            exoPlayer.seekTo(Math.max(0, positionMs));
        }
    }

    public void setRepeatOne(boolean repeatOne) {
        this.repeatOne = repeatOne;
    }

    public void setRepeatQueue(boolean repeatQueue) {
        this.repeatQueue = repeatQueue;
    }

    public void setShuffleEnabled(boolean enabled) {
        shuffleEnabled = enabled;
    }

    public boolean isShuffleEnabled() {
        return shuffleEnabled;
    }

    public boolean toggleShuffle() {
        shuffleEnabled = !shuffleEnabled;
        return shuffleEnabled;
    }

    public int getRepeatMode() {
        if (repeatOne) return 2;
        if (repeatQueue) return 1;
        return 0;
    }

    public void setRepeatMode(int mode) {
        if (mode == 2) {
            repeatOne = true;
            repeatQueue = false;
        } else if (mode == 1) {
            repeatOne = false;
            repeatQueue = true;
        } else {
            repeatOne = false;
            repeatQueue = false;
        }
    }

    public int cycleRepeatMode() {
        int nextMode = (getRepeatMode() + 1) % 3;
        setRepeatMode(nextMode);
        return nextMode;
    }

    public void nextTrack() {
        if (queue.isEmpty()) return;
        if (shuffleEnabled && queue.size() > 1) {
            int nextIndex;
            Random rng = new Random();
            int attempts = 0;
            do {
                nextIndex = rng.nextInt(queue.size());
                attempts++;
            } while (nextIndex == currentQueueIndex && attempts < 10);
            currentQueueIndex = nextIndex;
        } else if (currentQueueIndex < queue.size() - 1) {
            currentQueueIndex++;
        } else if (repeatQueue) {
            currentQueueIndex = 0;
        } else {
            // Auto-play recommended tracks when queue ends so music keeps going seamlessly
            triggerAutoPlay();
            return;
        }
        playTrack(queue.get(currentQueueIndex));
    }

    public void previousTrack() {
        if (!isPrepared || getCurrentPosition() > 4000) {
            seekTo(0);
            return;
        }
        if (queue.size() > 1) {
            currentQueueIndex = currentQueueIndex > 0 ? currentQueueIndex - 1 : queue.size() - 1;
            playTrack(queue.get(currentQueueIndex));
        } else {
            seekTo(0);
        }
    }

    private void triggerAutoPlay() {
        new com.example.muzzicapp.api.MusicApiService().getRecommendedTracks(new com.example.muzzicapp.api.MusicApiService.MusicCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                if (tracks != null && !tracks.isEmpty()) {
                    for (Track t : tracks) {
                        boolean exists = false;
                        for (Track q : queue) {
                            if (q.getTrackKey().equals(t.getTrackKey())) {
                                exists = true;
                                break;
                            }
                        }
                        if (!exists) queue.add(t);
                    }
                    nextTrack();
                } else {
                    isPlaying = false;
                    stopProgressTracker();
                    for (OnPlaybackChangeListener l : new ArrayList<>(listeners)) l.onPlaybackStateChanged(false);
                }
            }

            @Override
            public void onError(Exception e) {
                isPlaying = false;
                stopProgressTracker();
                for (OnPlaybackChangeListener l : new ArrayList<>(listeners)) l.onPlaybackStateChanged(false);
            }
        });
    }

    public int getQueueSize() {
        return queue.size();
    }

    public int getCurrentQueueIndex() {
        return currentQueueIndex;
    }

    public void setQueue(List<Track> tracks) {
        queue.clear();
        if (tracks != null) {
            queue.addAll(tracks);
        }
        currentQueueIndex = -1;
    }

    public List<Track> getQueue() {
        return new ArrayList<>(queue);
    }

    public void addToQueue(Track track) {
        if (track == null) return;
        queue.add(track);
        if (queue.size() == 1) {
            currentQueueIndex = 0;
            playTrack(track);
        }
    }

    public void addToQueueNext(Track track) {
        if (track == null) return;
        int insertIndex = currentQueueIndex >= 0 ? currentQueueIndex + 1 : queue.size();
        if (insertIndex > queue.size()) insertIndex = queue.size();
        queue.add(insertIndex, track);
    }

    public void removeFromQueue(int position) {
        if (position >= 0 && position < queue.size()) {
            queue.remove(position);
            if (position < currentQueueIndex) {
                currentQueueIndex--;
            } else if (position == currentQueueIndex) {
                if (!queue.isEmpty()) {
                    if (currentQueueIndex >= queue.size()) currentQueueIndex = 0;
                    playTrack(queue.get(currentQueueIndex));
                } else {
                    currentQueueIndex = -1;
                    pause();
                }
            }
        }
    }

    public void clearQueue() {
        Track current = currentTrack;
        queue.clear();
        if (current != null) {
            queue.add(current);
            currentQueueIndex = 0;
        } else {
            currentQueueIndex = -1;
        }
    }

    public void playTrackAt(int position) {
        if (position >= 0 && position < queue.size()) {
            currentQueueIndex = position;
            playTrack(queue.get(position));
        }
    }

    public void play() {
        if (isPrepared) {
            exoPlayer.play();
            isPlaying = true;
            startProgressTracker();
            for (OnPlaybackChangeListener l : listeners) l.onPlaybackStateChanged(true);
            MusicService.startOrUpdate(context);
        }
    }

    public void pause() {
        if (isPrepared && exoPlayer.isPlaying()) {
            exoPlayer.pause();
            isPlaying = false;
            stopProgressTracker();
            for (OnPlaybackChangeListener l : listeners) l.onPlaybackStateChanged(false);
            MusicService.startOrUpdate(context);
        }
    }
    
    public int getCurrentPosition() {
        if (isPrepared) return (int) exoPlayer.getCurrentPosition();
        return 0;
    }
    
    public int getDuration() {
        if (isPrepared) return (int) exoPlayer.getDuration();
        return 0;
    }
    
    public Track getCurrentTrack() {
        return currentTrack;
    }
    
    public boolean isPlaying() {
        return isPlaying;
    }

    private android.media.AudioDeviceInfo preferredAudioDevice = null;

    public void setPreferredAudioDevice(android.media.AudioDeviceInfo device) {
        this.preferredAudioDevice = device;
        if (exoPlayer != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            exoPlayer.setPreferredAudioDevice(device);
        }
    }

    public android.media.AudioDeviceInfo getPreferredAudioDevice() {
        return preferredAudioDevice;
    }

    public void release() {
        MusicService.stop(context);
        if (exoPlayer != null) {
            stopProgressTracker();
            exoPlayer.release();
            exoPlayer = null;
        }
    }
}
