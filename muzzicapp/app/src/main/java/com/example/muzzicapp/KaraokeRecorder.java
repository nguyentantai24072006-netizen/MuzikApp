package com.example.muzzicapp;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.example.muzzicapp.model.RecordingItem;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class KaraokeRecorder {

    public interface RecordTimerListener {
        void onTick(long durationMs, String formattedTime);
    }

    private static KaraokeRecorder instance;
    private MediaRecorder mediaRecorder;
    private File currentOutputFile;
    private String currentSongTitle;
    private String currentArtist;
    private long recordingStartTime = 0;
    private boolean isRecording = false;

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private RecordTimerListener timerListener;
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isRecording) {
                long elapsed = System.currentTimeMillis() - recordingStartTime;
                if (timerListener != null) {
                    timerListener.onTick(elapsed, formatDuration(elapsed));
                }
                timerHandler.postDelayed(this, 1000);
            }
        }
    };

    private KaraokeRecorder() {}

    public static synchronized KaraokeRecorder getInstance() {
        if (instance == null) {
            instance = new KaraokeRecorder();
        }
        return instance;
    }

    public synchronized boolean isRecording() {
        return isRecording;
    }

    public synchronized void setTimerListener(RecordTimerListener listener) {
        this.timerListener = listener;
    }

    public synchronized boolean startRecording(Context context, String songTitle, String artist) throws IOException {
        if (isRecording) {
            stopRecording();
        }

        this.currentSongTitle = songTitle != null ? songTitle : "Bài hát";
        this.currentArtist = artist != null ? artist : "Nghệ sĩ";

        File dir = new File(context.getExternalFilesDir(null), "Recordings");
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String safeTitle = currentSongTitle.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        if (safeTitle.length() > 20) safeTitle = safeTitle.substring(0, 20);
        String fileName = "REC_" + safeTitle + "_" + timeStamp + ".m4a";
        currentOutputFile = new File(dir, fileName);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mediaRecorder = new MediaRecorder(context);
        } else {
            mediaRecorder = new MediaRecorder();
        }

        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setAudioSamplingRate(44100);
        mediaRecorder.setAudioEncodingBitRate(128000);
        mediaRecorder.setOutputFile(currentOutputFile.getAbsolutePath());

        mediaRecorder.prepare();
        mediaRecorder.start();

        isRecording = true;
        recordingStartTime = System.currentTimeMillis();
        timerHandler.removeCallbacks(timerRunnable);
        timerHandler.post(timerRunnable);

        return true;
    }

    public synchronized RecordingItem stopRecording() {
        if (!isRecording) return null;

        timerHandler.removeCallbacks(timerRunnable);
        long durationMs = System.currentTimeMillis() - recordingStartTime;
        isRecording = false;

        try {
            if (mediaRecorder != null) {
                try {
                    mediaRecorder.stop();
                } catch (Exception ignored) {}
                mediaRecorder.release();
                mediaRecorder = null;
            }
        } catch (Exception e) {
            android.util.Log.e("KaraokeRecorder", "Error stopping MediaRecorder: " + e.getMessage());
        }

        if (currentOutputFile != null && currentOutputFile.exists() && currentOutputFile.length() > 0) {
            String title = "Bản thu - " + currentSongTitle;
            RecordingItem item = new RecordingItem(
                    title,
                    currentSongTitle,
                    currentArtist,
                    currentOutputFile.getAbsolutePath(),
                    durationMs
            );
            currentOutputFile = null;
            return item;
        }

        return null;
    }

    public synchronized void cancelRecording() {
        if (!isRecording) return;

        timerHandler.removeCallbacks(timerRunnable);
        isRecording = false;

        try {
            if (mediaRecorder != null) {
                try {
                    mediaRecorder.stop();
                } catch (Exception ignored) {}
                mediaRecorder.release();
                mediaRecorder = null;
            }
        } catch (Exception ignored) {}

        if (currentOutputFile != null && currentOutputFile.exists()) {
            try {
                currentOutputFile.delete();
            } catch (Exception ignored) {}
            currentOutputFile = null;
        }
    }

    public static String formatDuration(long durationMs) {
        long seconds = (durationMs / 1000) % 60;
        long minutes = (durationMs / (1000 * 60)) % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }
}
