package com.example.muzzicapp.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.muzzicapp.data.local.entity.FavoriteEntity;

import java.util.List;

@Dao
public interface FavoriteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertFavorite(FavoriteEntity favorite);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertFavorites(List<FavoriteEntity> favorites);

    @Query("DELETE FROM favorites WHERE userId = :userId AND trackKey = :trackKey")
    void deleteFavorite(String userId, String trackKey);

    @Query("SELECT * FROM favorites WHERE userId = :userId ORDER BY addedAt DESC")
    List<FavoriteEntity> getFavoritesByUser(String userId);

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE userId = :userId AND trackKey = :trackKey)")
    boolean isFavorite(String userId, String trackKey);

    @Query("DELETE FROM favorites WHERE userId = :userId")
    void clearFavoritesByUser(String userId);

    @Query("DELETE FROM favorites")
    void clearAllFavorites();
}
