package com.example.muzzicapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.example.muzzicapp.api.MusicApiService;
import com.example.muzzicapp.auth.AuthManager;
import com.example.muzzicapp.data.local.AppDatabase;
import com.example.muzzicapp.data.local.dao.FavoriteDao;
import com.example.muzzicapp.data.local.dao.PlaylistDao;
import com.example.muzzicapp.data.local.entity.FavoriteEntity;
import com.example.muzzicapp.data.local.entity.PlaylistEntity;
import com.example.muzzicapp.data.local.entity.TrackEntity;
import com.example.muzzicapp.model.Track;
import com.example.muzzicapp.sync.FirebaseSyncManager;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlaylistManager {

    private static final String PREF_NAME = "muzzic_playlists_prefs";
    private static final String KEY_PLAYLISTS_PREFIX = "user_playlists_";

    private static volatile PlaylistManager instance;
    private final SharedPreferences prefs;
    private final Gson gson = new Gson();
    private final List<UserPlaylist> playlists = new ArrayList<>();
    private final List<PlaylistListener> listeners = new ArrayList<>();
    private final AppDatabase db;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String activeUserId = null;

    public interface PlaylistListener {
        void onPlaylistsChanged();
    }

    public static class UserPlaylist {
        private String id;
        private String name;
        private long createdAt;
        private String coverArtUrl;
        private int coverStyleIndex = 0;
        private List<Track> tracks = new ArrayList<>();

        public UserPlaylist() {}

        public UserPlaylist(String name) {
            this.id = UUID.randomUUID().toString();
            this.name = name;
            this.createdAt = System.currentTimeMillis();
            this.tracks = new ArrayList<>();
            this.coverStyleIndex = Math.abs(name != null ? name.hashCode() : 0) % 6;
        }

        public UserPlaylist(String name, String coverArtUrl, int coverStyleIndex) {
            this.id = UUID.randomUUID().toString();
            this.name = name;
            this.createdAt = System.currentTimeMillis();
            this.coverArtUrl = coverArtUrl;
            this.coverStyleIndex = coverStyleIndex;
            this.tracks = new ArrayList<>();
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public String getCoverArtUrl() { return coverArtUrl; }
        public void setCoverArtUrl(String coverArtUrl) { this.coverArtUrl = coverArtUrl; }
        public int getCoverStyleIndex() { return coverStyleIndex; }
        public void setCoverStyleIndex(int index) { this.coverStyleIndex = index; }
        public List<Track> getTracks() { return tracks != null ? tracks : new ArrayList<>(); }
        public void setTracks(List<Track> tracks) { this.tracks = tracks; }
        public int getTrackCount() { return tracks != null ? tracks.size() : 0; }

        public String getEffectiveCoverUrl() {
            if (coverArtUrl != null && !coverArtUrl.trim().isEmpty()) {
                return coverArtUrl;
            }
            if (tracks != null && !tracks.isEmpty()) {
                for (Track t : tracks) {
                    if (t.getArtworkUrl() != null && !t.getArtworkUrl().trim().isEmpty()) {
                        return t.getArtworkUrl();
                    }
                }
            }
            int idx = Math.abs(coverStyleIndex != 0 ? coverStyleIndex : (id != null ? id.hashCode() : 0)) % PlaylistDialogHelper.AUTO_COVERS.length;
            return PlaylistDialogHelper.AUTO_COVERS[idx];
        }

        public boolean hasTrack(Track track) {
            if (track == null || tracks == null || tracks.isEmpty()) return false;
            String key = track.getTrackKey();
            String title = track.getTitle() != null ? track.getTitle().trim() : "";
            String artist = track.getArtist() != null ? track.getArtist().trim() : "";

            for (Track t : tracks) {
                if (t == null) continue;
                if (key != null && key.equals(t.getTrackKey())) {
                    return true;
                }
                if (track.getId() > 0 && t.getId() > 0 && track.getId() == t.getId()) {
                    return true;
                }
                if (track.getZingId() != null && !track.getZingId().isEmpty() && track.getZingId().equals(t.getZingId())) {
                    return true;
                }
                if (!title.isEmpty() && title.equalsIgnoreCase(t.getTitle())) {
                    if (artist.isEmpty() || t.getArtist() == null || artist.equalsIgnoreCase(t.getArtist())) {
                        return true;
                    }
                }
            }
            return false;
        }

        public int getCoverGradientRes() {
            int style = Math.abs(coverStyleIndex) % 6;
            switch (style) {
                case 1: return R.drawable.bg_gradient_suggest2;
                case 2: return R.drawable.bg_gradient_suggest3;
                case 3: return R.drawable.bg_gradient_suggest4;
                case 4: return R.drawable.bg_gradient_chill;
                case 5: return R.drawable.bg_gradient_hiphop;
                default: return R.drawable.bg_gradient_suggest1;
            }
        }
    }

    private PlaylistManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        db = AppDatabase.getInstance(context);

        // Listen for authentication changes to isolate and auto-sync playlists per account
        AuthManager.getInstance().addAuthStateListener(user -> {
            if (user != null && !user.isAnonymous()) {
                switchUser(user.getUid());
            } else {
                clearUserData();
            }
        });

        // Initialize state based on current authentication
        if (AuthManager.getInstance().isLoggedIn()) {
            switchUser(AuthManager.getInstance().getUserId());
        } else {
            clearUserData();
        }
    }

    public static synchronized PlaylistManager getInstance(Context context) {
        if (instance == null) {
            instance = new PlaylistManager(context.getApplicationContext());
        }
        return instance;
    }

    public static synchronized PlaylistManager getInstance() {
        return instance;
    }

    public void switchUser(String userId) {
        if (userId == null || userId.isEmpty() || "guest".equals(userId)) {
            clearUserData();
            return;
        }
        this.activeUserId = userId;

        ioExecutor.execute(() -> {
            try {
                PlaylistDao dao = db.playlistDao();
                List<PlaylistEntity> entities = dao.getPlaylistsByUser(userId);
                List<UserPlaylist> loaded = new ArrayList<>();

                if (entities != null && !entities.isEmpty()) {
                    for (PlaylistEntity pe : entities) {
                        UserPlaylist p = new UserPlaylist();
                        p.setId(pe.getId());
                        p.setName(pe.getName());
                        p.setCoverArtUrl(pe.getCoverArtUrl());
                        p.setCoverStyleIndex(pe.getCoverStyleIndex());
                        p.setCreatedAt(pe.getCreatedAt());

                        List<TrackEntity> trackEntities = dao.getTracksForPlaylist(pe.getId());
                        List<Track> tracks = new ArrayList<>();
                        if (trackEntities != null) {
                            for (TrackEntity te : trackEntities) {
                                Track t = convertTrackEntity(te);
                                if (t != null) tracks.add(t);
                            }
                        }
                        p.setTracks(tracks);
                        loaded.add(p);
                    }
                } else {
                    // Fallback to user-isolated SharedPreferences
                    String json = prefs.getString(KEY_PLAYLISTS_PREFIX + userId, null);
                    if (json != null) {
                        Type type = new TypeToken<List<UserPlaylist>>() {}.getType();
                        List<UserPlaylist> prefList = gson.fromJson(json, type);
                        if (prefList != null) loaded.addAll(prefList);
                    }
                }

                synchronized (this) {
                    playlists.clear();
                    playlists.addAll(loaded);

                    // Ensure default "Bài hát yêu thích" exists for this user
                    boolean hasFav = false;
                    for (UserPlaylist p : playlists) {
                        if ("Bài hát yêu thích".equalsIgnoreCase(p.getName())) {
                            hasFav = true;
                            break;
                        }
                    }
                    if (!hasFav) {
                        UserPlaylist favorites = new UserPlaylist("Bài hát yêu thích", PlaylistDialogHelper.AUTO_COVERS[0], 0);
                        playlists.add(0, favorites);
                        savePlaylistsLocally();
                    }
                }

                mainHandler.post(this::notifyListeners);

                // Sync with Firestore Cloud for this account
                syncWithCloud(null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public synchronized void clearUserData() {
        this.activeUserId = null;
        playlists.clear();
        mainHandler.post(this::notifyListeners);
    }

    public void deleteUserData(String userId, Runnable onComplete) {
        if (userId == null) {
            if (onComplete != null) onComplete.run();
            return;
        }
        final String uid = userId;
        ioExecutor.execute(() -> {
            try {
                db.playlistDao().deleteTracksByUserPlaylists(uid);
                db.playlistDao().deletePlaylistsByUser(uid);
                db.favoriteDao().clearFavoritesByUser(uid);
            } catch (Exception e) {
                e.printStackTrace();
            }
            mainHandler.post(() -> {
                synchronized (PlaylistManager.this) {
                    if (uid.equals(activeUserId)) {
                        activeUserId = null;
                        playlists.clear();
                        notifyListeners();
                    }
                }
                if (onComplete != null) onComplete.run();
            });
        });
    }

    private void saveToRoomDatabase() {
        if (activeUserId == null) return;
        final String userId = activeUserId;
        final List<UserPlaylist> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(playlists);
        }

        ioExecutor.execute(() -> {
            try {
                PlaylistDao dao = db.playlistDao();
                dao.deleteTracksByUserPlaylists(userId);
                dao.deletePlaylistsByUser(userId);

                List<PlaylistEntity> entities = new ArrayList<>();
                List<TrackEntity> trackEntities = new ArrayList<>();

                for (UserPlaylist p : snapshot) {
                    PlaylistEntity pe = new PlaylistEntity(
                            p.getId(),
                            userId,
                            p.getName(),
                            p.getCoverArtUrl(),
                            p.getCoverStyleIndex(),
                            p.getCreatedAt(),
                            System.currentTimeMillis()
                    );
                    entities.add(pe);

                    if (p.getTracks() != null) {
                        for (int i = 0; i < p.getTracks().size(); i++) {
                            Track t = p.getTracks().get(i);
                            if (t == null) continue;
                            TrackEntity te = new TrackEntity(
                                    p.getId(),
                                    t.getTrackKey(),
                                    t.getId(),
                                    t.getTitle(),
                                    t.getArtist(),
                                    t.getArtworkUrl(),
                                    t.getPreviewUrl(),
                                    t.getZingId(),
                                    t.getFullAudioUrl(),
                                    t.getDurationMillis(),
                                    i,
                                    System.currentTimeMillis()
                            );
                            trackEntities.add(te);
                        }
                    }
                }

                dao.insertPlaylists(entities);
                dao.insertTracks(trackEntities);

                // Also persist favorites to FavoriteDao for complete Room offline caching
                FavoriteDao favDao = db.favoriteDao();
                favDao.clearFavoritesByUser(userId);
                List<FavoriteEntity> favEntities = new ArrayList<>();
                for (UserPlaylist p : snapshot) {
                    if ("Bài hát yêu thích".equalsIgnoreCase(p.getName()) && p.getTracks() != null) {
                        for (Track t : p.getTracks()) {
                            if (t == null) continue;
                            favEntities.add(new FavoriteEntity(
                                    userId,
                                    t.getTrackKey(),
                                    t.getId(),
                                    t.getTitle(),
                                    t.getArtist(),
                                    t.getArtworkUrl(),
                                    t.getPreviewUrl(),
                                    t.getZingId(),
                                    t.getFullAudioUrl(),
                                    t.getDurationMillis(),
                                    System.currentTimeMillis()
                            ));
                        }
                    }
                }
                if (!favEntities.isEmpty()) {
                    favDao.insertFavorites(favEntities);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private Track convertTrackEntity(TrackEntity te) {
        if (te == null) return null;
        Track t = new Track();
        t.setId(te.getTrackId());
        t.setTitle(te.getTitle());
        t.setArtist(te.getArtist());
        t.setArtworkUrl(te.getArtworkUrl());
        t.setPreviewUrl(te.getPreviewUrl());
        t.setZingId(te.getZingId());
        t.setFullAudioUrl(te.getFullAudioUrl());
        t.setDurationMillis(te.getDurationMillis());
        return t;
    }

    private synchronized void savePlaylistsLocally() {
        if (activeUserId == null) return;
        String json = gson.toJson(playlists);
        prefs.edit().putString(KEY_PLAYLISTS_PREFIX + activeUserId, json).apply();
        saveToRoomDatabase();
        notifyListeners();
    }

    public synchronized void savePlaylists() {
        savePlaylistsLocally();
    }

    private List<Track> mergeTracks(List<Track> localTracks, List<Track> cloudTracks) {
        List<Track> result = new ArrayList<>();
        if (localTracks == null) localTracks = new ArrayList<>();
        if (cloudTracks == null) cloudTracks = new ArrayList<>();

        Map<String, Track> localMap = new HashMap<>();
        for (Track lt : localTracks) {
            if (lt != null && !lt.getTrackKey().isEmpty()) {
                localMap.put(lt.getTrackKey(), lt);
            }
        }

        Set<String> processedKeys = new HashSet<>();
        for (Track ct : cloudTracks) {
            if (ct == null) continue;
            String key = ct.getTrackKey();
            processedKeys.add(key);

            Track lt = localMap.get(key);
            if (lt == null && ct.getId() > 0) {
                for (Track candidate : localTracks) {
                    if (candidate != null && candidate.getId() == ct.getId()) {
                        lt = candidate;
                        break;
                    }
                }
            }

            if (lt != null) {
                boolean localHasTitle = lt.getTitle() != null && !lt.getTitle().trim().isEmpty();
                boolean cloudHasTitle = ct.getTitle() != null && !ct.getTitle().trim().isEmpty();

                if (cloudHasTitle) {
                    if (ct.getFullAudioUrl() == null || ct.getFullAudioUrl().isEmpty()) {
                        ct.setFullAudioUrl(lt.getFullAudioUrl());
                    }
                    result.add(ct);
                } else if (localHasTitle) {
                    result.add(lt);
                } else {
                    if (ct.getId() > 0) result.add(ct);
                }
            } else {
                if ((ct.getTitle() != null && !ct.getTitle().trim().isEmpty()) || ct.getId() > 0 || (ct.getZingId() != null && !ct.getZingId().isEmpty())) {
                    result.add(ct);
                }
            }
        }

        for (Track lt : localTracks) {
            if (lt == null) continue;
            String key = lt.getTrackKey();
            if (!processedKeys.contains(key)) {
                if (lt.getTitle() != null && !lt.getTitle().trim().isEmpty()) {
                    result.add(lt);
                }
            }
        }

        return result;
    }

    private void autoHealTracksAsync() {
        ioExecutor.execute(() -> {
            boolean changed = false;
            MusicApiService api = new MusicApiService();
            synchronized (PlaylistManager.this) {
                for (UserPlaylist p : playlists) {
                    if (p.getTracks() == null) continue;
                    for (int i = 0; i < p.getTracks().size(); i++) {
                        Track t = p.getTracks().get(i);
                        if (t != null && t.getId() > 0 && (t.getTitle() == null || t.getTitle().trim().isEmpty())) {
                            Track resolved = api.lookupITunesTrackSync(t.getId());
                            if (resolved != null && !resolved.getTitle().isEmpty()) {
                                t.setTitle(resolved.getTitle());
                                t.setArtist(resolved.getArtist());
                                t.setArtworkUrl(resolved.getArtworkUrl());
                                t.setPreviewUrl(resolved.getPreviewUrl());
                                t.setFullAudioUrl(resolved.getFullAudioUrl());
                                t.setDurationMillis(resolved.getDurationMillis());
                                changed = true;
                            }
                        }
                    }
                }
            }
            if (changed) {
                savePlaylistsLocally();
                if (activeUserId != null && AuthManager.getInstance().isLoggedIn()) {
                    for (UserPlaylist p : getPlaylists()) {
                        FirebaseSyncManager.getInstance().pushPlaylistToCloud(activeUserId, p);
                    }
                }
                mainHandler.post(this::notifyListeners);
            }
        });
    }

    public void syncWithCloud(Runnable onComplete) {
        if (activeUserId == null || !AuthManager.getInstance().isLoggedIn()) {
            if (onComplete != null) onComplete.run();
            return;
        }

        final String userId = activeUserId;
        FirebaseSyncManager.getInstance().syncFromCloud(userId, new FirebaseSyncManager.SyncCallback() {
            @Override
            public void onSyncComplete(List<UserPlaylist> cloudPlaylists, List<Track> favorites) {
                synchronized (PlaylistManager.this) {
                    if (activeUserId == null || !activeUserId.equals(userId)) return;

                    if (cloudPlaylists != null && !cloudPlaylists.isEmpty()) {
                        for (UserPlaylist cp : cloudPlaylists) {
                            UserPlaylist existing = getPlaylistById(cp.getId());
                            if (existing == null) {
                                for (UserPlaylist p : playlists) {
                                    if (p.getName() != null && p.getName().equalsIgnoreCase(cp.getName()) && (p.getTracks() == null || p.getTracks().isEmpty())) {
                                        existing = p;
                                        existing.setId(cp.getId());
                                        break;
                                    }
                                }
                            }

                            if (existing == null) {
                                playlists.add(cp);
                            } else {
                                if (cp.getName() != null && !cp.getName().isEmpty()) {
                                    existing.setName(cp.getName());
                                }
                                if (cp.getCoverArtUrl() != null && !cp.getCoverArtUrl().isEmpty()) {
                                    existing.setCoverArtUrl(cp.getCoverArtUrl());
                                }
                                existing.setCoverStyleIndex(cp.getCoverStyleIndex());
                                existing.setTracks(mergeTracks(existing.getTracks(), cp.getTracks()));
                            }
                        }
                    }

                    // Push local playlists to cloud
                    for (UserPlaylist lp : playlists) {
                        FirebaseSyncManager.getInstance().pushPlaylistToCloud(userId, lp);
                    }

                    // Merge favorites
                    UserPlaylist favPlaylist = getFavoritesPlaylist();
                    if (favPlaylist != null) {
                        if (favorites != null && !favorites.isEmpty()) {
                            favPlaylist.setTracks(mergeTracks(favPlaylist.getTracks(), favorites));
                        }
                        for (Track t : favPlaylist.getTracks()) {
                            if (t != null && t.getTitle() != null && !t.getTitle().trim().isEmpty()) {
                                FirebaseSyncManager.getInstance().pushFavoriteToCloud(userId, t);
                            }
                        }
                    }

                    savePlaylistsLocally();
                }

                autoHealTracksAsync();

                if (onComplete != null) {
                    mainHandler.post(onComplete);
                }
            }

            @Override
            public void onSyncFailed(String error) {
                autoHealTracksAsync();
                if (onComplete != null) {
                    mainHandler.post(onComplete);
                }
            }
        });
    }

    public synchronized List<UserPlaylist> getPlaylists() {
        if (activeUserId == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(playlists);
    }

    public synchronized UserPlaylist getPlaylistById(String id) {
        if (activeUserId == null || id == null) return null;
        for (UserPlaylist p : playlists) {
            if (id.equals(p.getId())) return p;
        }
        return null;
    }

    public synchronized UserPlaylist createPlaylist(String name) {
        int randomIdx = new java.util.Random().nextInt(PlaylistDialogHelper.AUTO_COVERS.length);
        return createPlaylist(name, PlaylistDialogHelper.AUTO_COVERS[randomIdx], randomIdx);
    }

    public synchronized UserPlaylist createPlaylist(String name, String coverArtUrl, int coverStyleIndex) {
        if (activeUserId == null) return null;

        if (name == null || name.trim().isEmpty()) {
            name = "Danh sách phát mới";
        }
        int idx = coverStyleIndex >= 0 ? (coverStyleIndex % PlaylistDialogHelper.AUTO_COVERS.length) : 0;
        if (coverArtUrl == null || coverArtUrl.trim().isEmpty()) {
            coverArtUrl = PlaylistDialogHelper.AUTO_COVERS[idx];
        }
        UserPlaylist newPlaylist = new UserPlaylist(name.trim(), coverArtUrl, coverStyleIndex);
        playlists.add(0, newPlaylist);
        savePlaylistsLocally();

        if (AuthManager.getInstance().isLoggedIn()) {
            FirebaseSyncManager.getInstance().pushPlaylistToCloud(activeUserId, newPlaylist);
        }
        return newPlaylist;
    }

    public synchronized boolean addTrackToPlaylist(String playlistId, Track track) {
        if (activeUserId == null || track == null) return false;
        UserPlaylist target = getPlaylistById(playlistId);
        if (target == null) return false;

        if (target.hasTrack(track)) {
            return false;
        }
        target.getTracks().add(track);
        if (target.getCoverArtUrl() == null && track.getArtworkUrl() != null) {
            target.setCoverArtUrl(track.getArtworkUrl());
        }
        savePlaylistsLocally();

        if (AuthManager.getInstance().isLoggedIn()) {
            FirebaseSyncManager.getInstance().pushPlaylistToCloud(activeUserId, target);
        }
        return true;
    }

    public synchronized boolean removeTrackFromPlaylist(String playlistId, String trackKey) {
        if (activeUserId == null) return false;
        UserPlaylist target = getPlaylistById(playlistId);
        if (target == null) return false;

        boolean removed = false;
        List<Track> list = target.getTracks();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getTrackKey().equals(trackKey)) {
                list.remove(i);
                removed = true;
                break;
            }
        }
        if (removed) {
            savePlaylistsLocally();
            if (AuthManager.getInstance().isLoggedIn()) {
                FirebaseSyncManager.getInstance().pushPlaylistToCloud(activeUserId, target);
            }
        }
        return removed;
    }

    public synchronized UserPlaylist getFavoritesPlaylist() {
        if (activeUserId == null) return null;

        for (UserPlaylist p : playlists) {
            if ("Bài hát yêu thích".equalsIgnoreCase(p.getName())) {
                return p;
            }
        }
        if (!playlists.isEmpty()) {
            return playlists.get(0);
        }
        UserPlaylist favorites = new UserPlaylist("Bài hát yêu thích", PlaylistDialogHelper.AUTO_COVERS[0], 0);
        playlists.add(0, favorites);
        savePlaylistsLocally();
        return favorites;
    }

    public synchronized boolean isTrackFavorite(Track track) {
        if (activeUserId == null || track == null) return false;
        UserPlaylist fav = getFavoritesPlaylist();
        if (fav == null || fav.getTracks() == null) return false;
        String trackKey = track.getTrackKey();
        for (Track t : fav.getTracks()) {
            if (t.getTrackKey() != null && t.getTrackKey().equals(trackKey)) {
                return true;
            }
        }
        return false;
    }

    public synchronized boolean toggleFavorite(Track track) {
        if (activeUserId == null || track == null) return false;
        UserPlaylist fav = getFavoritesPlaylist();
        if (fav == null) return false;
        boolean currentlyFav = isTrackFavorite(track);
        if (currentlyFav) {
            removeTrackFromPlaylist(fav.getId(), track.getTrackKey());
            if (AuthManager.getInstance().isLoggedIn()) {
                FirebaseSyncManager.getInstance().removeFavoriteFromCloud(activeUserId, track.getTrackKey());
            }
            return false;
        } else {
            addTrackToPlaylist(fav.getId(), track);
            if (AuthManager.getInstance().isLoggedIn()) {
                FirebaseSyncManager.getInstance().pushFavoriteToCloud(activeUserId, track);
            }
            return true;
        }
    }

    public synchronized void deletePlaylist(String playlistId) {
        if (activeUserId == null) return;
        for (int i = 0; i < playlists.size(); i++) {
            if (playlists.get(i).getId().equals(playlistId)) {
                playlists.remove(i);
                savePlaylistsLocally();
                if (AuthManager.getInstance().isLoggedIn()) {
                    FirebaseSyncManager.getInstance().deletePlaylistFromCloud(activeUserId, playlistId);
                }
                break;
            }
        }
    }

    public void addListener(PlaylistListener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(PlaylistListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (PlaylistListener l : new ArrayList<>(listeners)) {
            try {
                l.onPlaylistsChanged();
            } catch (Exception ignored) {}
        }
    }
}
