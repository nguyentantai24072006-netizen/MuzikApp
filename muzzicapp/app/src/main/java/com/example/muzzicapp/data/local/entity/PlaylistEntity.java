package com.example.muzzicapp.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "playlists")
public class PlaylistEntity {
    @PrimaryKey
    @NonNull
    private String id;
    private String userId;
    private String name;
    private String coverArtUrl;
    private int coverStyleIndex;
    private long createdAt;
    private long updatedAt;

    public PlaylistEntity(@NonNull String id, String userId, String name, String coverArtUrl,
                          int coverStyleIndex, long createdAt, long updatedAt) {
        this.id = id;
        this.userId = userId != null ? userId : "guest";
        this.name = name;
        this.coverArtUrl = coverArtUrl;
        this.coverStyleIndex = coverStyleIndex;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCoverArtUrl() { return coverArtUrl; }
    public void setCoverArtUrl(String coverArtUrl) { this.coverArtUrl = coverArtUrl; }

    public int getCoverStyleIndex() { return coverStyleIndex; }
    public void setCoverStyleIndex(int coverStyleIndex) { this.coverStyleIndex = coverStyleIndex; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
