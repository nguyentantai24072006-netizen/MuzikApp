package com.example.muzzicapp.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;

@Entity(
    tableName = "favorites",
    primaryKeys = {"userId", "trackKey"},
    indices = {@Index("userId"), @Index("trackKey")}
)
public class FavoriteEntity {
    @NonNull
    private String userId;
    @NonNull
    private String trackKey;

    private long trackId;
    private String title;
    private String artist;
    private String artworkUrl;
    private String previewUrl;
    private String zingId;
    private String fullAudioUrl;
    private long durationMillis;
    private long addedAt;

    public FavoriteEntity(@NonNull String userId, @NonNull String trackKey, long trackId,
                          String title, String artist, String artworkUrl, String previewUrl,
                          String zingId, String fullAudioUrl, long durationMillis, long addedAt) {
        this.userId = userId != null ? userId : "guest";
        this.trackKey = trackKey;
        this.trackId = trackId;
        this.title = title;
        this.artist = artist;
        this.artworkUrl = artworkUrl;
        this.previewUrl = previewUrl;
        this.zingId = zingId;
        this.fullAudioUrl = fullAudioUrl;
        this.durationMillis = durationMillis;
        this.addedAt = addedAt;
    }

    @NonNull
    public String getUserId() { return userId; }
    public void setUserId(@NonNull String userId) { this.userId = userId; }

    @NonNull
    public String getTrackKey() { return trackKey; }
    public void setTrackKey(@NonNull String trackKey) { this.trackKey = trackKey; }

    public long getTrackId() { return trackId; }
    public void setTrackId(long trackId) { this.trackId = trackId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }

    public String getArtworkUrl() { return artworkUrl; }
    public void setArtworkUrl(String artworkUrl) { this.artworkUrl = artworkUrl; }

    public String getPreviewUrl() { return previewUrl; }
    public void setPreviewUrl(String previewUrl) { this.previewUrl = previewUrl; }

    public String getZingId() { return zingId; }
    public void setZingId(String zingId) { this.zingId = zingId; }

    public String getFullAudioUrl() { return fullAudioUrl; }
    public void setFullAudioUrl(String fullAudioUrl) { this.fullAudioUrl = fullAudioUrl; }

    public long getDurationMillis() { return durationMillis; }
    public void setDurationMillis(long durationMillis) { this.durationMillis = durationMillis; }

    public long getAddedAt() { return addedAt; }
    public void setAddedAt(long addedAt) { this.addedAt = addedAt; }
}
