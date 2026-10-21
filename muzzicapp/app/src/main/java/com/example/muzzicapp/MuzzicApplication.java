package com.example.muzzicapp;

import android.app.Application;
import android.util.Log;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLException;
import java.io.File;

public class MuzzicApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("java.net.preferIPv6Addresses", "false");
        
        // Initialize background tasks asynchronously so cold startup is instantaneous (sub-100ms)
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            try {
                File ytDlpDir = new File(getNoBackupFilesDir(), "youtubedl-android/yt-dlp");
                if (ytDlpDir.exists()) {
                    deleteRecursive(ytDlpDir);
                    Log.d("MuzzicApp", "Cleaned incompatible yt-dlp update directory in background");
                }
            } catch (Exception e) {
                Log.w("MuzzicApp", "Failed to clean yt-dlp dir", e);
            }

            try {
                YoutubeDL.getInstance().init(MuzzicApplication.this);
                Log.i("MuzzicApp", "YoutubeDL initialized successfully in background thread");
            } catch (YoutubeDLException e) {
                Log.e("MuzzicApp", "failed to initialize youtubedl-android", e);
            }

            try {
                PlaylistManager.getInstance(MuzzicApplication.this);
            } catch (Exception ignored) {}
        });
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] files = fileOrDirectory.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteRecursive(child);
                }
            }
        }
        fileOrDirectory.delete();
    }
}
