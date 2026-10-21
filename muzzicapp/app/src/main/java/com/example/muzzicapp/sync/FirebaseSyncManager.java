package com.example.muzzicapp.sync;

import android.os.Handler;
import android.os.Looper;

import com.example.muzzicapp.PlaylistManager;
import com.example.muzzicapp.model.Track;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirebaseSyncManager {

    public interface SyncCallback {
        void onSyncComplete(List<PlaylistManager.UserPlaylist> playlists, List<Track> favorites);
        void onSyncFailed(String error);
    }

    private static volatile FirebaseSyncManager instance;
    private final FirebaseFirestore firestore;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();

    private FirebaseSyncManager() {
        firestore = FirebaseFirestore.getInstance();
    }

    public static FirebaseSyncManager getInstance() {
        if (instance == null) {
            synchronized (FirebaseSyncManager.class) {
                if (instance == null) {
                    instance = new FirebaseSyncManager();
                }
            }
        }
        return instance;
    }

    public static Map<String, Object> trackToMap(Track t) {
        if (t == null) return new HashMap<>();
        Map<String, Object> map = new HashMap<>();
        long id = t.getId();
        String title = t.getTitle();
        String artist = t.getArtist();
        String artworkUrl = t.getArtworkUrl();
        String previewUrl = t.getPreviewUrl();
        String zingId = t.getZingId();
        String fullAudioUrl = t.getFullAudioUrl();
        long durationMillis = t.getDurationMillis();
        String trackKey = t.getTrackKey();

        // Write both naming conventions (iTunes trackName/artistName and common title/artist)
        map.put("id", id);
        map.put("trackId", id);
        map.put("title", title != null ? title : "");
        map.put("trackName", title != null ? title : "");
        map.put("artist", artist != null ? artist : "");
        map.put("artistName", artist != null ? artist : "");
        map.put("artworkUrl", artworkUrl != null ? artworkUrl : "");
        map.put("artworkUrl100", artworkUrl != null ? artworkUrl : "");
        map.put("previewUrl", previewUrl != null ? previewUrl : "");
        map.put("zingId", zingId != null ? zingId : "");
        map.put("fullAudioUrl", fullAudioUrl != null ? fullAudioUrl : "");
        map.put("durationMillis", durationMillis);
        map.put("trackTimeMillis", durationMillis);
        map.put("trackKey", trackKey);
        return map;
    }

    public static Track parseTrackFromMap(Map<String, Object> map) {
        if (map == null) return null;
        Track t = new Track();

        // Title: check trackName first, then title
        Object titleObj = map.get("trackName");
        if (titleObj == null) titleObj = map.get("title");
        String titleStr = (titleObj != null && !"null".equalsIgnoreCase(String.valueOf(titleObj))) ? String.valueOf(titleObj).trim() : "";

        // Artist: check artistName first, then artist
        Object artistObj = map.get("artistName");
        if (artistObj == null) artistObj = map.get("artist");
        String artistStr = (artistObj != null && !"null".equalsIgnoreCase(String.valueOf(artistObj))) ? String.valueOf(artistObj).trim() : "";

        // Artwork: check artworkUrl100 first, then artworkUrl
        Object artObj = map.get("artworkUrl100");
        if (artObj == null) artObj = map.get("artworkUrl");
        String artStr = (artObj != null && !"null".equalsIgnoreCase(String.valueOf(artObj))) ? String.valueOf(artObj).trim() : "";

        // ID: check trackId first, then id
        long idVal = 0;
        Object idObj = map.get("trackId");
        if (idObj == null) idObj = map.get("id");
        if (idObj instanceof Number) {
            idVal = ((Number) idObj).longValue();
        } else if (idObj != null) {
            try {
                idVal = Long.parseLong(String.valueOf(idObj));
            } catch (NumberFormatException ignored) {}
        }

        // Duration: check trackTimeMillis first, then durationMillis
        long durVal = 0;
        Object durObj = map.get("trackTimeMillis");
        if (durObj == null) durObj = map.get("durationMillis");
        if (durObj instanceof Number) {
            durVal = ((Number) durObj).longValue();
        } else if (durObj != null) {
            try {
                durVal = Long.parseLong(String.valueOf(durObj));
            } catch (NumberFormatException ignored) {}
        }

        // Streaming URLs & Zing ID
        Object previewObj = map.get("previewUrl");
        String previewStr = (previewObj != null && !"null".equalsIgnoreCase(String.valueOf(previewObj))) ? String.valueOf(previewObj).trim() : "";

        Object fullAudioObj = map.get("fullAudioUrl");
        String fullAudioStr = (fullAudioObj != null && !"null".equalsIgnoreCase(String.valueOf(fullAudioObj))) ? String.valueOf(fullAudioObj).trim() : "";

        Object zingIdObj = map.get("zingId");
        String zingIdStr = (zingIdObj != null && !"null".equalsIgnoreCase(String.valueOf(zingIdObj))) ? String.valueOf(zingIdObj).trim() : "";

        // Salvage from trackKey if needed
        Object trackKeyObj = map.get("trackKey");
        if (trackKeyObj != null) {
            String key = String.valueOf(trackKeyObj).trim();
            if (idVal == 0 && key.startsWith("id_")) {
                try {
                    idVal = Long.parseLong(key.substring(3));
                } catch (Exception ignored) {}
            }
            if (zingIdStr.isEmpty() && key.startsWith("zing_")) {
                zingIdStr = key.substring(5);
            }
            if (titleStr.isEmpty() && !key.startsWith("id_") && !key.startsWith("zing_") && key.contains("_")) {
                String[] parts = key.split("_", 2);
                if (parts.length > 0 && !parts[0].isEmpty()) titleStr = parts[0];
                if (parts.length > 1 && !parts[1].isEmpty() && artistStr.isEmpty()) artistStr = parts[1];
            }
        }

        t.setId(idVal);
        t.setTitle(titleStr);
        t.setArtist(artistStr);
        t.setArtworkUrl(artStr);
        t.setPreviewUrl(previewStr);
        t.setFullAudioUrl(fullAudioStr);
        t.setZingId(zingIdStr);
        t.setDurationMillis(durVal);

        return t;
    }

    public void pushPlaylistToCloud(String userId, PlaylistManager.UserPlaylist playlist) {
        if (userId == null || "guest".equals(userId) || playlist == null || playlist.getId() == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("id", playlist.getId());
        data.put("name", playlist.getName());
        data.put("coverArtUrl", playlist.getCoverArtUrl());
        data.put("coverStyleIndex", playlist.getCoverStyleIndex());
        data.put("createdAt", playlist.getCreatedAt());
        data.put("updatedAt", System.currentTimeMillis());

        List<Map<String, Object>> trackList = new ArrayList<>();
        if (playlist.getTracks() != null) {
            for (Track t : playlist.getTracks()) {
                if (t == null) continue;
                // Skip invalid ghost tracks with no title and no artist
                if (t.getTitle().trim().isEmpty() && t.getArtist().trim().isEmpty() && t.getId() <= 0 && (t.getZingId() == null || t.getZingId().isEmpty())) {
                    continue;
                }
                trackList.add(trackToMap(t));
            }
        }
        data.put("tracks", trackList);

        firestore.collection("users")
                .document(userId)
                .collection("playlists")
                .document(playlist.getId())
                .set(data, SetOptions.merge());
    }

    public void deletePlaylistFromCloud(String userId, String playlistId) {
        if (userId == null || "guest".equals(userId) || playlistId == null) return;
        firestore.collection("users")
                .document(userId)
                .collection("playlists")
                .document(playlistId)
                .delete();
    }

    public void pushFavoriteToCloud(String userId, Track track) {
        if (userId == null || "guest".equals(userId) || track == null) return;
        if (track.getTitle().trim().isEmpty() && track.getArtist().trim().isEmpty() && track.getId() <= 0 && (track.getZingId() == null || track.getZingId().isEmpty())) {
            return;
        }

        Map<String, Object> data = trackToMap(track);
        data.put("addedAt", System.currentTimeMillis());

        firestore.collection("users")
                .document(userId)
                .collection("favorites")
                .document(track.getTrackKey())
                .set(data, SetOptions.merge());
    }

    public void removeFavoriteFromCloud(String userId, String trackKey) {
        if (userId == null || "guest".equals(userId) || trackKey == null) return;
        firestore.collection("users")
                .document(userId)
                .collection("favorites")
                .document(trackKey)
                .delete();
    }

    public void syncFromCloud(String userId, SyncCallback callback) {
        if (userId == null || "guest".equals(userId)) {
            if (callback != null) callback.onSyncFailed("Tài khoản khách không thể đồng bộ");
            return;
        }

        firestore.collection("users")
                .document(userId)
                .collection("playlists")
                .get()
                .addOnSuccessListener(playlistSnapshots -> {
                    List<PlaylistManager.UserPlaylist> cloudPlaylists = new ArrayList<>();
                    for (DocumentSnapshot doc : playlistSnapshots.getDocuments()) {
                        String id = doc.getString("id");
                        String name = doc.getString("name");
                        String coverArtUrl = doc.getString("coverArtUrl");
                        Long coverStyleIdx = doc.getLong("coverStyleIndex");
                        Long createdAt = doc.getLong("createdAt");

                        PlaylistManager.UserPlaylist p = new PlaylistManager.UserPlaylist();
                        p.setId(id != null ? id : doc.getId());
                        p.setName(name != null ? name : "Playlist");
                        p.setCoverArtUrl(coverArtUrl);
                        p.setCoverStyleIndex(coverStyleIdx != null ? coverStyleIdx.intValue() : 0);
                        p.setCreatedAt(createdAt != null ? createdAt : System.currentTimeMillis());

                        List<Map<String, Object>> tracksRaw = (List<Map<String, Object>>) doc.get("tracks");
                        List<Track> tracks = new ArrayList<>();
                        if (tracksRaw != null) {
                            for (Map<String, Object> map : tracksRaw) {
                                Track t = parseTrackFromMap(map);
                                if (t != null && (!t.getTitle().isEmpty() || !t.getArtist().isEmpty() || t.getId() > 0 || (t.getZingId() != null && !t.getZingId().isEmpty()))) {
                                    tracks.add(t);
                                }
                            }
                        }
                        p.setTracks(tracks);
                        cloudPlaylists.add(p);
                    }

                    // Now load favorites
                    firestore.collection("users")
                            .document(userId)
                            .collection("favorites")
                            .get()
                            .addOnSuccessListener(favSnapshots -> {
                                List<Track> cloudFavorites = new ArrayList<>();
                                for (DocumentSnapshot doc : favSnapshots.getDocuments()) {
                                    Map<String, Object> data = doc.getData();
                                    if (data != null) {
                                        Track t = parseTrackFromMap(data);
                                        if (t != null && (!t.getTitle().isEmpty() || !t.getArtist().isEmpty() || t.getId() > 0 || (t.getZingId() != null && !t.getZingId().isEmpty()))) {
                                            cloudFavorites.add(t);
                                        }
                                    }
                                }
                                if (callback != null) {
                                    mainHandler.post(() -> callback.onSyncComplete(cloudPlaylists, cloudFavorites));
                                }
                            })
                            .addOnFailureListener(e -> {
                                if (callback != null) {
                                    mainHandler.post(() -> callback.onSyncComplete(cloudPlaylists, new ArrayList<>()));
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    if (callback != null) {
                        mainHandler.post(() -> callback.onSyncFailed(e.getMessage()));
                    }
                });
    }

    public void deleteAllUserDataFromCloud(String userId, Runnable onComplete) {
        if (userId == null || "guest".equals(userId)) {
            if (onComplete != null) onComplete.run();
            return;
        }

        // Delete playlists subcollection
        firestore.collection("users").document(userId).collection("playlists").get()
                .addOnSuccessListener(playlists -> {
                    if (playlists != null) {
                        for (DocumentSnapshot doc : playlists.getDocuments()) {
                            doc.getReference().delete();
                        }
                    }
                    // Delete favorites subcollection
                    firestore.collection("users").document(userId).collection("favorites").get()
                            .addOnSuccessListener(favorites -> {
                                if (favorites != null) {
                                    for (DocumentSnapshot doc : favorites.getDocuments()) {
                                        doc.getReference().delete();
                                    }
                                }
                                // Delete root user document
                                firestore.collection("users").document(userId).delete()
                                        .addOnCompleteListener(task -> {
                                            if (onComplete != null) mainHandler.post(onComplete);
                                        });
                            })
                            .addOnFailureListener(e -> {
                                firestore.collection("users").document(userId).delete();
                                if (onComplete != null) mainHandler.post(onComplete);
                            });
                })
                .addOnFailureListener(e -> {
                    firestore.collection("users").document(userId).delete();
                    if (onComplete != null) mainHandler.post(onComplete);
                });
    }
}
