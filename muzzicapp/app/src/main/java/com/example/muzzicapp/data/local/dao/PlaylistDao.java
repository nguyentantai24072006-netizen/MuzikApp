package com.example.muzzicapp.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.example.muzzicapp.data.local.entity.PlaylistEntity;
import com.example.muzzicapp.data.local.entity.TrackEntity;

import java.util.List;

@Dao
public interface PlaylistDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertPlaylist(PlaylistEntity playlist);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertPlaylists(List<PlaylistEntity> playlists);

    @Update
    void updatePlaylist(PlaylistEntity playlist);

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    void deletePlaylist(String playlistId);

    @Query("SELECT * FROM playlists WHERE userId = :userId ORDER BY createdAt DESC")
    List<PlaylistEntity> getPlaylistsByUser(String userId);

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    List<PlaylistEntity> getAllPlaylists();

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    PlaylistEntity getPlaylistById(String playlistId);

    // Track operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTrack(TrackEntity track);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTracks(List<TrackEntity> tracks);

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackKey = :trackKey")
    void deleteTrack(String playlistId, String trackKey);

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    void deleteAllTracksForPlaylist(String playlistId);

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY orderIndex ASC, addedAt ASC")
    List<TrackEntity> getTracksForPlaylist(String playlistId);

    @Query("DELETE FROM playlists WHERE userId = :userId")
    void deletePlaylistsByUser(String userId);

    @Query("DELETE FROM playlist_tracks WHERE playlistId IN (SELECT id FROM playlists WHERE userId = :userId)")
    void deleteTracksByUserPlaylists(String userId);

    @Query("DELETE FROM playlists")
    void clearAllPlaylists();

    @Query("DELETE FROM playlist_tracks")
    void clearAllTracks();
}
