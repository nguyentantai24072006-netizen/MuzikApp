package com.example.muzzicapp.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = {"playlistId", "trackKey"},
    indices = {@Index("playlistId"), @Index("trackKey")}
)
public class TrackEntity {
    @NonNull
    private String playlistId;
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
    private int orderIndex;
    private long addedAt;

    public TrackEntity(@NonNull String playlistId, @NonNull String trackKey, long trackId,
                       String title, String artist, String artworkUrl, String previewUrl,
                       String zingId, String fullAudioUrl, long durationMillis,
                       int orderIndex, long addedAt) {
        this.playlistId = playlistId;
        this.trackKey = trackKey;
        this.trackId = trackId;
        this.title = title;
        this.artist = artist;
        this.artworkUrl = artworkUrl;
        this.previewUrl = previewUrl;
        this.zingId = zingId;
        this.fullAudioUrl = fullAudioUrl;
        this.durationMillis = durationMillis;
        this.orderIndex = orderIndex;
        this.addedAt = addedAt;
    }

    @NonNull
    public String getPlaylistId() { return playlistId; }
    public void setPlaylistId(@NonNull String playlistId) { this.playlistId = playlistId; }

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

    public int getOrderIndex() { return orderIndex; }
    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }

    public long getAddedAt() { return addedAt; }
    public void setAddedAt(long addedAt) { this.addedAt = addedAt; }
}
