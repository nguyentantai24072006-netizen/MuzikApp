package com.example.muzzicapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.example.muzzicapp.model.Track;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class HistoryManager {

    private static final String PREF_NAME = "muzzic_history_prefs";
    private static final String KEY_RECENT_TRACKS = "recent_tracks";
    private static final int MAX_HISTORY = 40;

    private static volatile HistoryManager instance;
    private final SharedPreferences prefs;
    private final Gson gson = new Gson();
    private final List<Track> recentTracks = new ArrayList<>();
    private final List<HistoryListener> listeners = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface HistoryListener {
        void onHistoryChanged(List<Track> recentTracks);
    }

    public static HistoryManager getInstance(Context context) {
        if (instance == null) {
            synchronized (HistoryManager.class) {
                if (instance == null) {
                    instance = new HistoryManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private HistoryManager(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        loadHistory();
    }

    private synchronized void loadHistory() {
        String json = prefs.getString(KEY_RECENT_TRACKS, null);
        recentTracks.clear();
        if (json != null && !json.trim().isEmpty()) {
            try {
                Type listType = new TypeToken<ArrayList<Track>>() {}.getType();
                List<Track> loaded = gson.fromJson(json, listType);
                if (loaded != null) {
                    recentTracks.addAll(loaded);
                }
            } catch (Exception e) {
                android.util.Log.e("HistoryManager", "Failed to parse history JSON", e);
            }
        }
    }

    private synchronized void saveHistory() {
        try {
            String json = gson.toJson(recentTracks);
            prefs.edit().putString(KEY_RECENT_TRACKS, json).apply();
        } catch (Exception e) {
            android.util.Log.e("HistoryManager", "Failed to save history JSON", e);
        }
    }

    public synchronized void addTrack(Track track) {
        if (track == null || track.getTitle() == null || track.getTitle().trim().isEmpty()) return;

        // Remove duplicate if already in history
        for (int i = 0; i < recentTracks.size(); i++) {
            Track t = recentTracks.get(i);
            if (t != null && t.getTrackKey() != null && t.getTrackKey().equals(track.getTrackKey())) {
                recentTracks.remove(i);
                break;
            }
        }

        // Insert at the front (most recent)
        recentTracks.add(0, track);

        // Cap at MAX_HISTORY
        while (recentTracks.size() > MAX_HISTORY) {
            recentTracks.remove(recentTracks.size() - 1);
        }

        saveHistory();
        notifyListeners();
    }

    public synchronized List<Track> getRecentTracks() {
        return new ArrayList<>(recentTracks);
    }

    public synchronized void clearHistory() {
        recentTracks.clear();
        saveHistory();
        notifyListeners();
    }

    public void addListener(HistoryListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(HistoryListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        List<Track> copy = getRecentTracks();
        mainHandler.post(() -> {
            for (HistoryListener l : new ArrayList<>(listeners)) {
                l.onHistoryChanged(copy);
            }
        });
    }
}
