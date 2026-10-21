package com.example.muzzicapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.example.muzzicapp.model.RecordingItem;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class RecordingManager {

    private static final String PREF_NAME = "muzzic_recordings_prefs";
    private static final String KEY_RECORDINGS = "user_recordings";

    private static RecordingManager instance;
    private final SharedPreferences prefs;
    private final Gson gson = new Gson();
    private final List<RecordingItem> recordings = new ArrayList<>();
    private final List<RecordingListener> listeners = new ArrayList<>();

    public interface RecordingListener {
        void onRecordingsChanged();
    }

    private RecordingManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        loadRecordings();
    }

    public static synchronized RecordingManager getInstance(Context context) {
        if (instance == null) {
            instance = new RecordingManager(context.getApplicationContext());
        }
        return instance;
    }

    public static synchronized RecordingManager getInstance() {
        return instance;
    }

    public synchronized void loadRecordings() {
        recordings.clear();
        String json = prefs.getString(KEY_RECORDINGS, null);
        if (json != null && !json.trim().isEmpty()) {
            try {
                Type type = new TypeToken<List<RecordingItem>>() {}.getType();
                List<RecordingItem> saved = gson.fromJson(json, type);
                if (saved != null) {
                    // Filter out recordings whose files no longer exist on storage
                    Iterator<RecordingItem> iterator = saved.iterator();
                    while (iterator.hasNext()) {
                        RecordingItem item = iterator.next();
                        if (!item.exists()) {
                            iterator.remove();
                        }
                    }
                    recordings.addAll(saved);
                    // Sort descending by creation date (newest first)
                    Collections.sort(recordings, (a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
                }
            } catch (Exception e) {
                android.util.Log.e("RecordingManager", "Failed to parse recordings JSON: " + e.getMessage());
            }
        }
    }

    public synchronized void saveRecordings() {
        try {
            String json = gson.toJson(recordings);
            prefs.edit().putString(KEY_RECORDINGS, json).apply();
            notifyListeners();
        } catch (Exception e) {
            android.util.Log.e("RecordingManager", "Failed to save recordings: " + e.getMessage());
        }
    }

    public synchronized List<RecordingItem> getRecordings() {
        return new ArrayList<>(recordings);
    }

    public synchronized void addRecording(RecordingItem item) {
        if (item == null) return;
        recordings.add(0, item);
        saveRecordings();
    }

    public synchronized boolean deleteRecording(RecordingItem item) {
        if (item == null) return false;
        boolean removed = recordings.removeIf(r -> r.getId().equals(item.getId()));
        if (removed) {
            File file = item.getFile();
            if (file != null && file.exists()) {
                try {
                    file.delete();
                } catch (Exception ignored) {}
            }
            saveRecordings();
        }
        return removed;
    }

    public synchronized void renameRecording(RecordingItem item, String newTitle) {
        if (item == null || newTitle == null || newTitle.trim().isEmpty()) return;
        for (RecordingItem r : recordings) {
            if (r.getId().equals(item.getId())) {
                r.setTitle(newTitle.trim());
                saveRecordings();
                break;
            }
        }
    }

    public void addListener(RecordingListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(RecordingListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    private void notifyListeners() {
        for (RecordingListener l : new ArrayList<>(listeners)) {
            try {
                l.onRecordingsChanged();
            } catch (Exception ignored) {}
        }
    }

    public static void shareRecording(Context context, RecordingItem recording) {
        if (context == null || recording == null) return;
        File file = recording.getFile();
        if (file == null || !file.exists()) {
            Toast.makeText(context, "File ghi âm không tồn tại", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Uri contentUri = FileProvider.getUriForFile(
                    context.getApplicationContext(),
                    context.getPackageName() + ".provider",
                    file
            );

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("audio/*");
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, recording.getTitle());
            String songInfo = (recording.getSongTitle() != null && !recording.getSongTitle().isEmpty())
                    ? (" bài " + recording.getSongTitle())
                    : "";
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Nghe bản thu âm karaoke" + songInfo + " của tôi trên Muzzic nhé!");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Intent chooser = Intent.createChooser(shareIntent, "Chia sẻ bản thu âm qua");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);
        } catch (Exception e) {
            android.util.Log.e("RecordingManager", "Error sharing recording: " + e.getMessage(), e);
            Toast.makeText(context, "Lỗi khi chia sẻ: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
