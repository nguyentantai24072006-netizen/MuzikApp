package com.example.muzzicapp.model;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class RecordingItem {
    private String id;
    private String title;
    private String songTitle;
    private String artist;
    private String filePath;
    private long durationMs;
    private long createdAt;

    public RecordingItem() {}

    public RecordingItem(String title, String songTitle, String artist, String filePath, long durationMs) {
        this.id = UUID.randomUUID().toString();
        this.title = title;
        this.songTitle = songTitle;
        this.artist = artist;
        this.filePath = filePath;
        this.durationMs = durationMs;
        this.createdAt = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSongTitle() { return songTitle; }
    public void setSongTitle(String songTitle) { this.songTitle = songTitle; }

    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public String getFormattedDuration() {
        long seconds = (durationMs / 1000) % 60;
        long minutes = (durationMs / (1000 * 60)) % 60;
        long hours = durationMs / (1000 * 60 * 60);
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    public String getFormattedDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        return sdf.format(new Date(createdAt));
    }

    public File getFile() {
        return filePath != null ? new File(filePath) : null;
    }

    public boolean exists() {
        File file = getFile();
        return file != null && file.exists() && file.length() > 0;
    }
}
