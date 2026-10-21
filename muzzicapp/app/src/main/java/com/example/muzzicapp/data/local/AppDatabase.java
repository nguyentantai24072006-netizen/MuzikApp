package com.example.muzzicapp.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.muzzicapp.data.local.dao.FavoriteDao;
import com.example.muzzicapp.data.local.dao.PlaylistDao;
import com.example.muzzicapp.data.local.entity.FavoriteEntity;
import com.example.muzzicapp.data.local.entity.PlaylistEntity;
import com.example.muzzicapp.data.local.entity.TrackEntity;

@Database(entities = {PlaylistEntity.class, TrackEntity.class, FavoriteEntity.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "muzzic_database";
    private static volatile AppDatabase instance;

    public abstract PlaylistDao playlistDao();
    public abstract FavoriteDao favoriteDao();

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            DATABASE_NAME
                    ).fallbackToDestructiveMigration().build();
                }
            }
        }
        return instance;
    }
}
