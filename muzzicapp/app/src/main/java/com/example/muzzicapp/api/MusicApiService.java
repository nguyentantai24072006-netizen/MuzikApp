package com.example.muzzicapp.api;

import android.os.Handler;
import android.os.Looper;

import com.example.muzzicapp.model.AudiusModels.AudiusResponse;
import com.example.muzzicapp.model.AudiusModels.AudiusTrack;
import com.example.muzzicapp.model.Track;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MusicApiService {
    
    private final ExecutorService executor = Executors.newFixedThreadPool(6);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();
    private final ZingAutocompleteApi zingApi = new ZingAutocompleteApi();
    
    private static final String APP_NAME = "muzzicapp";
    private static final String BASE_URL = "https://discoveryprovider.audius.co/v1";

    public interface MusicCallback {
        void onSuccess(List<Track> tracks);
        void onError(Exception e);
    }

    private static class CacheEntry {
        final List<Track> tracks;
        final long timestamp;

        CacheEntry(List<Track> tracks) {
            this.tracks = new ArrayList<>(tracks);
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired(long ttlMs) {
            return System.currentTimeMillis() - timestamp > ttlMs;
        }
    }

    private static final android.util.LruCache<String, CacheEntry> MEMORY_CACHE = new android.util.LruCache<>(150);
    private static final java.util.Map<String, List<MusicCallback>> IN_FLIGHT_REQUESTS = new java.util.HashMap<>();

    private interface ActionFetcher {
        List<Track> fetch() throws Exception;
    }

    public static void clearCache() {
        synchronized (MEMORY_CACHE) {
            MEMORY_CACHE.evictAll();
        }
    }

    public static void invalidateKeyPrefix(String keyPrefix) {
        synchronized (MEMORY_CACHE) {
            java.util.Map<String, CacheEntry> snapshot = MEMORY_CACHE.snapshot();
            for (String k : snapshot.keySet()) {
                if (k.startsWith(keyPrefix)) {
                    MEMORY_CACHE.remove(k);
                }
            }
        }
    }

    private void executeCached(String cacheKey, long ttlMs, MusicCallback callback, ActionFetcher fetcher) {
        // 1. Instant in-memory cache hit (0ms UI latency!)
        synchronized (MEMORY_CACHE) {
            CacheEntry cached = MEMORY_CACHE.get(cacheKey);
            if (cached != null && !cached.isExpired(ttlMs) && !cached.tracks.isEmpty()) {
                final List<Track> cachedResult = new ArrayList<>(cached.tracks);
                mainHandler.post(() -> callback.onSuccess(cachedResult));
                return;
            }
        }

        // 2. In-flight request deduplication (prevents redundant parallel HTTP requests)
        synchronized (IN_FLIGHT_REQUESTS) {
            List<MusicCallback> callbacks = IN_FLIGHT_REQUESTS.get(cacheKey);
            if (callbacks != null) {
                callbacks.add(callback);
                return;
            }
            callbacks = new ArrayList<>();
            callbacks.add(callback);
            IN_FLIGHT_REQUESTS.put(cacheKey, callbacks);
        }

        // 3. Background execution
        executor.execute(() -> {
            try {
                List<Track> result = fetcher.fetch();
                if (result == null) result = new ArrayList<>();

                if (!result.isEmpty()) {
                    synchronized (MEMORY_CACHE) {
                        MEMORY_CACHE.put(cacheKey, new CacheEntry(result));
                    }
                }

                final List<Track> finalResult = result;
                List<MusicCallback> callbackList;
                synchronized (IN_FLIGHT_REQUESTS) {
                    callbackList = IN_FLIGHT_REQUESTS.remove(cacheKey);
                }

                if (callbackList != null) {
                    mainHandler.post(() -> {
                        for (MusicCallback cb : callbackList) {
                            if (!finalResult.isEmpty()) {
                                cb.onSuccess(new ArrayList<>(finalResult));
                            } else {
                                cb.onError(new Exception("Không có kết quả"));
                            }
                        }
                    });
                }
            } catch (Exception e) {
                List<MusicCallback> callbackList;
                synchronized (IN_FLIGHT_REQUESTS) {
                    callbackList = IN_FLIGHT_REQUESTS.remove(cacheKey);
                }
                if (callbackList != null) {
                    mainHandler.post(() -> {
                        for (MusicCallback cb : callbackList) {
                            cb.onError(e);
                        }
                    });
                }
            }
        });
    }

    // ========== SEARCH ==========

    public void searchTracks(String query, MusicCallback callback) {
        if (query == null || query.trim().isEmpty()) {
            mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
            return;
        }
        executeCached("search_" + query.toLowerCase().trim(), 10 * 60 * 1000L, callback, () -> searchTracksInternal(query));
    }

    private List<Track> searchTracksInternal(String query) {
        List<Track> combined = new ArrayList<>();
        java.util.Set<String> seenKeys = new java.util.HashSet<>();

        // 1. Zing MP3 (Vietnamese high-accuracy + lyrics)
        List<Track> zingTracks = zingApi.searchAllTracksSync(query);
        if (zingTracks != null) {
            for (Track t : zingTracks) {
                String key = normalizeText(t.getTitle()) + " - " + normalizeText(t.getArtist());
                if (seenKeys.add(key)) {
                    combined.add(t);
                }
            }
        }

        // 2. Deezer (high quality licensed Vietnamese & Global songs)
        List<Track> deezerTracks = searchDeezerSync(query);
        if (deezerTracks != null) {
            for (Track t : deezerTracks) {
                if (isRelevant(t, query)) {
                    String key = normalizeText(t.getTitle()) + " - " + normalizeText(t.getArtist());
                    if (seenKeys.add(key)) {
                        combined.add(t);
                    }
                }
            }
        }

        // 3. iTunes (Apple Music Vietnam catalog)
        List<Track> itunesTracks = searchITunesSync(query);
        if (itunesTracks != null) {
            for (Track t : itunesTracks) {
                if (isRelevant(t, query)) {
                    String key = normalizeText(t.getTitle()) + " - " + normalizeText(t.getArtist());
                    if (seenKeys.add(key)) {
                        combined.add(t);
                    }
                }
            }
        }

        if (combined.isEmpty()) {
            try {
                String encodedQuery = URLEncoder.encode(query, "UTF-8");
                String urlString = BASE_URL + "/tracks/search?query=" + encodedQuery + "&app_name=" + APP_NAME;
                List<Track> audiusResults = fetchFromApi(urlString);
                if (audiusResults != null) combined.addAll(audiusResults);
            } catch (Exception ignored) {}
        }
        return combined;
    }

    public static String normalizeText(String input) {
        if (input == null) return "";
        String nfd = java.text.Normalizer.normalize(input.toLowerCase(), java.text.Normalizer.Form.NFD);
        return nfd.replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                  .replace('đ', 'd').replace('Đ', 'd')
                  .replaceAll("[^a-z0-9]", " ")
                  .replaceAll("\\s+", " ")
                  .trim();
    }

    private boolean isRelevant(Track track, String query) {
        if (query == null || query.trim().isEmpty()) return true;
        String normQ = normalizeText(query);
        String[] qWords = normQ.split("\\s+");
        if (qWords.length == 0) return true;

        String titleNorm = normalizeText(track.getTitle());
        String artistNorm = normalizeText(track.getArtist());

        if (qWords.length == 1) {
            return titleNorm.contains(qWords[0]) || artistNorm.contains(qWords[0]);
        }

        for (String w : qWords) {
            if (w.length() >= 2 && (titleNorm.contains(w) || artistNorm.contains(w))) {
                return true;
            }
        }
        return false;
    }

    private List<Track> searchDeezerSync(String query) {
        List<Track> list = new ArrayList<>();
        try {
            String encoded = URLEncoder.encode(query, "UTF-8");
            String urlStr = "https://api.deezer.com/search?q=" + encoded + "&limit=30";
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.setRequestProperty("Accept", "application/json");

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                InputStreamReader reader = new InputStreamReader(conn.getInputStream(), "UTF-8");
                JsonObject json = gson.fromJson(reader, JsonObject.class);
                reader.close();
                if (json != null && json.has("data")) {
                    JsonArray data = json.getAsJsonArray("data");
                    for (JsonElement el : data) {
                        JsonObject obj = el.getAsJsonObject();
                        Track track = new Track();
                        track.setTitle(obj.has("title") ? obj.get("title").getAsString() : "Unknown");
                        if (obj.has("artist") && obj.getAsJsonObject("artist").has("name")) {
                            track.setArtist(obj.getAsJsonObject("artist").get("name").getAsString());
                        } else {
                            track.setArtist("Unknown Artist");
                        }
                        if (obj.has("album") && obj.getAsJsonObject("album").has("cover_xl")) {
                            track.setArtworkUrl(obj.getAsJsonObject("album").get("cover_xl").getAsString());
                        } else if (obj.has("album") && obj.getAsJsonObject("album").has("cover_big")) {
                            track.setArtworkUrl(obj.getAsJsonObject("album").get("cover_big").getAsString());
                        } else if (obj.has("album") && obj.getAsJsonObject("album").has("cover_medium")) {
                            track.setArtworkUrl(obj.getAsJsonObject("album").get("cover_medium").getAsString());
                        }
                        if (obj.has("duration")) {
                            track.setDurationMillis(obj.get("duration").getAsLong() * 1000);
                        }
                        if (obj.has("preview")) {
                            track.setPreviewUrl(obj.get("preview").getAsString());
                            track.setFullAudioUrl(null);
                        }
                        list.add(track);
                    }
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    private List<Track> searchITunesSync(String query) {
        List<Track> list = new ArrayList<>();
        try {
            String encoded = URLEncoder.encode(query, "UTF-8");
            String urlStr = "https://itunes.apple.com/search?term=" + encoded + "&entity=song&country=VN&limit=30";
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setRequestProperty("Accept", "application/json");

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                InputStreamReader reader = new InputStreamReader(conn.getInputStream(), "UTF-8");
                JsonObject json = gson.fromJson(reader, JsonObject.class);
                reader.close();
                if (json != null && json.has("results")) {
                    JsonArray results = json.getAsJsonArray("results");
                    for (JsonElement el : results) {
                        JsonObject obj = el.getAsJsonObject();
                        Track track = new Track();
                        track.setTitle(obj.has("trackName") ? obj.get("trackName").getAsString() : "Unknown");
                        track.setArtist(obj.has("artistName") ? obj.get("artistName").getAsString() : "Unknown");
                        if (obj.has("artworkUrl100")) {
                            String art = obj.get("artworkUrl100").getAsString().replace("100x100bb", "600x600bb");
                            track.setArtworkUrl(art);
                        }
                        if (obj.has("trackTimeMillis")) {
                            track.setDurationMillis(obj.get("trackTimeMillis").getAsLong());
                        }
                        if (obj.has("previewUrl")) {
                            track.setPreviewUrl(obj.get("previewUrl").getAsString());
                            track.setFullAudioUrl(null);
                        }
                        list.add(track);
                    }
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    private void searchAudiusFallback(String query, MusicCallback callback) {
        executor.execute(() -> {
            try {
                String encodedQuery = URLEncoder.encode(query, "UTF-8");
                String urlString = BASE_URL + "/tracks/search?query=" + encodedQuery + "&app_name=" + APP_NAME;
                List<Track> audiusResults = fetchFromApi(urlString);
                if (!audiusResults.isEmpty()) {
                    mainHandler.post(() -> callback.onSuccess(audiusResults));
                } else {
                    mainHandler.post(() -> callback.onError(new Exception("Không tìm thấy kết quả")));
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }
    
    // ========== TRENDING ==========

    public void getTrendingTracks(MusicCallback callback) {
        executeCached("trending_global", 10 * 60 * 1000L, callback, () -> {
            // 1. Try real-time official Zing MP3 chart (100 songs)
            List<Track> chartTracks = fetchZingChartHome();
            if (chartTracks != null && chartTracks.size() >= 10) {
                return chartTracks;
            }

            // 2. High-performance fallback: Multi-artist parallel aggregation
            String[] topArtists = {
                "Sơn Tùng", "HIEUTHUHAI", "Wren Evans", "Anh Trai Say Hi",
                "Vũ", "tlinh", "MCK", "Grey D", "Low G", "Đen Vâu", "Phan Mạnh Quỳnh"
            };
            List<String> shuffled = new ArrayList<>(Arrays.asList(topArtists));
            Collections.shuffle(shuffled);
            
            List<Track> aggregated = new ArrayList<>();
            for (int i = 0; i < Math.min(5, shuffled.size()); i++) {
                List<Track> found = zingApi.searchTracksSync(shuffled.get(i));
                if (found != null) aggregated.addAll(found);
            }

            return deduplicateTracks(aggregated);
        });
    }

    private List<Track> fetchZingChartHome() {
        try {
            URL url = new URL("https://api-zingmp3-vercel.vercel.app/api/charthome");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setRequestProperty("Accept", "application/json");

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                InputStreamReader reader = new InputStreamReader(conn.getInputStream(), "UTF-8");
                JsonObject json = gson.fromJson(reader, JsonObject.class);
                reader.close();
                conn.disconnect();

                if (json != null && json.has("data") && !json.get("data").isJsonNull()) {
                    JsonObject data = json.getAsJsonObject("data");
                    if (data.has("RTChart") && !data.get("RTChart").isJsonNull()) {
                        JsonObject rtChart = data.getAsJsonObject("RTChart");
                        if (rtChart.has("items") && !rtChart.get("items").isJsonNull()) {
                            JsonArray items = rtChart.getAsJsonArray("items");
                            List<Track> tracks = new ArrayList<>();
                            int max = Math.min(30, items.size());
                            for (int i = 0; i < max; i++) {
                                JsonObject it = items.get(i).getAsJsonObject();
                                Track t = new Track();
                                t.setTitle(it.has("title") ? it.get("title").getAsString() : "Unknown");
                                t.setArtist(it.has("artistsNames") ? it.get("artistsNames").getAsString() : "V-Pop");
                                if (it.has("encodeId")) t.setZingId(it.get("encodeId").getAsString());
                                if (it.has("thumbnailM")) t.setArtworkUrl(it.get("thumbnailM").getAsString());
                                else if (it.has("thumbnail")) t.setArtworkUrl(it.get("thumbnail").getAsString());
                                if (it.has("duration")) t.setDurationMillis(it.get("duration").getAsLong() * 1000);
                                tracks.add(t);
                            }
                            return tracks;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
    
    // ========== TRENDING BY GENRE ==========

    public void getTrendingByGenre(String genre, MusicCallback callback) {
        String key = "genre_" + (genre != null ? genre.toLowerCase().trim() : "vpop");
        executeCached(key, 15 * 60 * 1000L, callback, () -> {
            String[] keywords;
            if (genre != null && (genre.toLowerCase().contains("vpop") || genre.toLowerCase().contains("v-pop"))) {
                keywords = new String[]{"Sơn Tùng", "Đức Phúc", "Erik", "Min", "Amee", "Hoàng Dũng"};
            } else if (genre != null && genre.toLowerCase().contains("hiphop")) {
                keywords = new String[]{"Binz", "Karik", "MCK", "Tlinh", "JustaTee", "Rhymastic", "B Ray"};
            } else {
                keywords = new String[]{genre != null ? genre : "vpop"};
            }

            List<String> shuffled = new ArrayList<>(Arrays.asList(keywords));
            Collections.shuffle(shuffled);
            List<Track> aggregated = new ArrayList<>();
            for (int i = 0; i < Math.min(3, shuffled.size()); i++) {
                List<Track> res = zingApi.searchTracksSync(shuffled.get(i));
                if (res != null) aggregated.addAll(res);
            }

            return deduplicateTracks(aggregated);
        });
    }

    // ========== MOOD-BASED PLAYLISTS ==========

    public void getTracksByMood(String mood, MusicCallback callback) {
        String key = "mood_" + (mood != null ? mood.toLowerCase().trim() : "relax");
        executeCached(key, 15 * 60 * 1000L, callback, () -> {
            String[] keywords;
            String moodStr = (mood != null ? mood.toLowerCase() : "relax");
            switch (moodStr) {
                case "relax":
                case "thư giãn": 
                    keywords = new String[]{"Vũ", "Lê Cát Trọng Lý", "Thái Đinh", "Đen Vâu", "Hà Anh Tuấn", "Hoàng Dũng"}; 
                    break;
                case "energy":
                case "năng lượng": 
                    keywords = new String[]{"Sơn Tùng", "HIEUTHUHAI", "Mono", "Tóc Tiên", "Soobin", "Touliver"}; 
                    break;
                case "chill": 
                    keywords = new String[]{"Chillies", "Thịnh Suy", "Da LAB", "Trang", "Obito", "Vũ", "Kha"}; 
                    break;
                case "focus":
                case "tập trung": 
                    keywords = new String[]{"Piano không lời", "Lofi chill", "Guitar acoustic", "Hoàng Dũng", "An An"}; 
                    break;
                case "happy":
                case "vui vẻ": 
                    keywords = new String[]{"Amee", "Trúc Nhân", "Suni Hạ Linh", "Hoàng Yến Chibi", "Phương Ly"}; 
                    break;
                case "sad":
                case "tâm trạng":
                case "buồn": 
                    keywords = new String[]{"Mr. Siro", "Phan Mạnh Quỳnh", "Hương Tràm", "Bùi Anh Tuấn", "Vũ", "Trung Quân"}; 
                    break;
                case "love":
                case "lãng mạn":
                case "tình yêu":
                    keywords = new String[]{"Đức Phúc", "Erik", "Văn Mai Hương", "Quân A.P", "Hoàng Dũng", "LyLy"};
                    break;
                default: 
                    keywords = new String[]{moodStr, "V-Pop", "Acoustic"};
            }

            List<String> list = new ArrayList<>(Arrays.asList(keywords));
            Collections.shuffle(list);
            List<Track> aggregated = new ArrayList<>();
            for (int i = 0; i < Math.min(3, list.size()); i++) {
                List<Track> found = zingApi.searchTracksSync(list.get(i));
                if (found != null) aggregated.addAll(found);
            }

            return deduplicateTracks(aggregated);
        });
    }

    // ========== CURATED PLAYLISTS & GENRES (RICH & RANDOMIZED) ==========
    
    public void getPlaylistByKeyword(String keyword, int limit, MusicCallback callback) {
        String rawKey = (keyword != null ? keyword.trim() : "");
        String lower = rawKey.toLowerCase();
        boolean isArtist = lower.startsWith("artist:") || isKnownArtist(rawKey);
        String key = (isArtist ? "artist_v4_" : "playlist_v4_") + (lower.isEmpty() ? "default" : lower) + "_" + limit;

        executeCached(key, 15 * 60 * 1000L, callback, () -> {
            if (isArtist) {
                String artistName = lower.startsWith("artist:") ? rawKey.substring(7).trim() : rawKey;
                return fetchSongsForSpecificArtist(artistName, limit);
            }

            String[] artistPool;

            if (lower.contains("trending") || lower.contains("featured") || lower.contains("thịnh hành")) {
                List<Track> chart = fetchZingChartHome();
                if (chart != null && !chart.isEmpty()) {
                    return chart;
                }
                artistPool = new String[]{"Sơn Tùng", "HIEUTHUHAI", "Wren Evans", "Anh Trai Say Hi", "Vũ", "Grey D", "MCK", "tlinh", "Low G"};
            } else if (lower.contains("vpop") || lower.contains("nhạc trẻ") || lower.contains("vietnamese")) {
                artistPool = new String[]{
                    "Sơn Tùng M-TP", "HIEUTHUHAI", "Wren Evans", "Vũ", "Grey D",
                    "Amee", "Đức Phúc", "Erik", "Bích Phương", "Phan Mạnh Quỳnh",
                    "Hoàng Dũng", "Soobin", "Mono", "Min", "Quang Hùng MasterD"
                };
            } else if (lower.contains("indie") || lower.contains("alternative")) {
                artistPool = new String[]{
                    "Ngọt", "Chillies", "Thịnh Suy", "Trang", "Vũ",
                    "Lê Cát Trọng Lý", "Kiên", "The Cassette", "Cá Hồi Hoang",
                    "Phùng Khánh Linh", "Hoàng Dũng"
                };
            } else if (lower.contains("hiphop") || lower.contains("rap")) {
                artistPool = new String[]{
                    "MCK", "tlinh", "Low G", "Binz", "Đen Vâu",
                    "JustaTee", "Rhymastic", "B Ray", "Andiez", "24k.Right", "Double2T"
                };
            } else if (lower.contains("pop") || lower.contains("global") || lower.contains("usuk") || lower.contains("billboard")) {
                artistPool = new String[]{
                    "Taylor Swift", "Ed Sheeran", "Ariana Grande", "Bruno Mars",
                    "Billie Eilish", "Dua Lipa", "Justin Bieber", "Charlie Puth",
                    "The Weeknd", "Coldplay"
                };
            } else if (lower.contains("chill") || lower.contains("lofi")) {
                artistPool = new String[]{
                    "Lofi chill", "Thịnh Suy", "Chillies", "Vũ", "Thái Đinh",
                    "Kha", "Da LAB", "Hoàng Dũng", "An An", "Duy Ngọc"
                };
            } else if (lower.contains("acoustic") || lower.contains("guitar")) {
                artistPool = new String[]{
                    "Lê Cát Trọng Lý", "Phan Mạnh Quỳnh", "Vũ",
                    "Trang", "Hoàng Dũng", "Nguyên Hà", "Hà Anh Tuấn"
                };
            } else if (lower.contains("deep house") || lower.contains("house") || lower.contains("electronic")) {
                artistPool = new String[]{
                    "Deep House", "Alan Walker", "Kygo", "Touliver", "Hoaprox", "Avicii", "Calvin Harris"
                };
            } else if (lower.contains("rnb") || lower.contains("soul")) {
                artistPool = new String[]{
                    "JustaTee", "Wren Evans", "Grey D", "tlinh", "Bruno Mars", "The Weeknd"
                };
            } else if (lower.contains("jazz") || lower.contains("cafe")) {
                artistPool = new String[]{
                    "Smooth Jazz", "Norah Jones", "Laufey", "Lê Cát Trọng Lý", "Piano Jazz"
                };
            } else if (lower.contains("rock")) {
                artistPool = new String[]{
                    "Bức Tường", "Microwave", "Ngũ Cung", "Queen", "Linkin Park", "Coldplay", "Imagine Dragons"
                };
            } else if (lower.contains("edm") || lower.contains("party")) {
                artistPool = new String[]{
                    "Touliver", "Hoaprox", "Sơn Tùng", "Tóc Tiên", "Alan Walker", "The Chainsmokers", "Martin Garrix"
                };
            } else if (lower.contains("relax") || lower.contains("thư giãn")) {
                artistPool = new String[]{
                    "Vũ", "Lê Cát Trọng Lý", "Thái Đinh", "Đen Vâu", "Hà Anh Tuấn", "Hoàng Dũng"
                };
            } else if (lower.contains("energy") || lower.contains("năng lượng")) {
                artistPool = new String[]{
                    "Sơn Tùng", "HIEUTHUHAI", "Mono", "Tóc Tiên", "Soobin", "Touliver"
                };
            } else if (lower.contains("focus") || lower.contains("tập trung")) {
                artistPool = new String[]{
                    "Piano không lời", "Lofi chill", "Hoàng Dũng", "An An"
                };
            } else if (lower.contains("happy") || lower.contains("vui vẻ")) {
                artistPool = new String[]{
                    "Amee", "Trúc Nhân", "Suni Hạ Linh", "Hoàng Yến Chibi", "Phương Ly"
                };
            } else if (lower.contains("sad") || lower.contains("tâm trạng") || lower.contains("buồn")) {
                artistPool = new String[]{
                    "Mr. Siro", "Phan Mạnh Quỳnh", "Hương Tràm", "Bùi Anh Tuấn", "Vũ", "Trung Quân"
                };
            } else if (lower.contains("love") || lower.contains("lãng mạn") || lower.contains("tình yêu")) {
                artistPool = new String[]{
                    "Đức Phúc", "Erik", "Văn Mai Hương", "Quân A.P", "Hoàng Dũng", "LyLy"
                };
            } else {
                List<Track> specific = fetchSongsForSpecificArtist(rawKey, limit);
                if (specific != null && !specific.isEmpty()) {
                    return specific;
                }
                artistPool = new String[]{rawKey.isEmpty() ? "V-Pop" : rawKey, "V-Pop"};
            }

            List<String> list = new ArrayList<>(Arrays.asList(artistPool));
            Collections.shuffle(list);

            List<Track> aggregated = new ArrayList<>();
            for (int i = 0; i < Math.min(4, list.size()); i++) {
                String q = list.get(i);
                List<Track> found = zingApi.searchTracksSync(q);
                if (found != null && !found.isEmpty()) {
                    aggregated.addAll(found);
                } else {
                    List<Track> dz = searchDeezerSync(q);
                    if (dz != null) aggregated.addAll(dz);
                }
            }

            if (lower.contains("deep house") || lower.contains("jazz") || lower.contains("rock")) {
                List<Track> dz = searchDeezerSync(list.get(0));
                if (dz != null) aggregated.addAll(dz);
            }

            List<Track> unique = deduplicateTracks(aggregated);
            Collections.shuffle(unique);

            return unique.size() > limit ? new ArrayList<>(unique.subList(0, limit)) : unique;
        });
    }

    // ========== RECOMMENDED TRACKS (DIVERSE & FRESH REFRESH) ==========

    public void getRecommendedTracks(MusicCallback callback) {
        executeCached("recommended_tracks", 5 * 60 * 1000L, callback, () -> {
            String[] pool = {
                "Sơn Tùng", "HIEUTHUHAI", "Wren Evans", "Vũ", "tlinh", "MCK", "Grey D", "Amee",
                "Low G", "Phương Ly", "Hoà Minzy", "Đen Vâu", "Chillies", "Thịnh Suy", "Da LAB",
                "Bích Phương", "Phan Mạnh Quỳnh", "Hoàng Dũng", "Soobin", "Mono", "Double2T",
                "Erik", "Đức Phúc", "Suni Hạ Linh", "Min", "Quang Hùng MasterD"
            };

            List<String> shuffledPool = new ArrayList<>(Arrays.asList(pool));
            Collections.shuffle(shuffledPool);

            // Pick 3-4 distinct random artists
            List<Track> aggregated = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                List<Track> found = zingApi.searchTracksSync(shuffledPool.get(i));
                if (found != null) aggregated.addAll(found);
            }

            List<Track> unique = deduplicateTracks(aggregated);
            Collections.shuffle(unique); // Shuffle so results feel lively and dynamic

            return unique.size() > 20 ? new ArrayList<>(unique.subList(0, 20)) : unique;
        });
    }

    // ========== FEATURED ARTISTS ==========

    public static final String[][] TOP_VIETNAMESE_ARTISTS = {
            {"Sơn Tùng M-TP", "https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/avatars/5/9/6/9/59696c9dba7a914d587d886049c10df6.jpg"},
            {"HIEUTHUHAI", "https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/avatars/c/0/7/4/c0742c35795d4d234bf86cc7258b5077.jpg"},
            {"Vũ.", "https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/avatars/d/1/7/1/d17181fe947a2c205119a5a774863889.jpg"},
            {"tlinh", "https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/avatars/1/b/e/7/1be7b0f6f95b88917e86b98f156012b9.jpg"},
            {"Wren Evans", "https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/avatars/d/5/8/e/d58e299bcd36e35751c52ec79e56d2b5.jpg"},
            {"MCK", "https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/avatars/d/2/a/f/d2af3346f0541b2eb81d49648a2d814a.jpg"},
            {"GREY D", "https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/avatars/6/a/d/4/6ad41e27f0771a45bd43627f60221825.jpg"},
            {"Đen Vâu", "https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/avatars/0/9/e/c/09ec67133c4ff1e7c49e79aea4980ede.jpg"},
            {"Hoàng Dũng", "https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/avatars/9/7/f/d/97fda14f69f4a564898a52380a59093a.jpg"},
            {"MONO", "https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/avatars/f/c/4/f/fc4f15088ebaa97b704e192ddd5fcf8f.jpg"},
            {"SOOBIN", "https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/avatars/a/3/a/b/a3ab763366da3d3df96b55a20177285c.jpg"},
            {"Phương Ly", "https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/avatars/4/4/9/a/449ad5795ff3aaa38f2b07d933ba84bf.jpg"},
            {"AMEE", "https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/avatars/d/b/8/2/db82811d8db60180f8251866fd2ede7d.jpg"},
            {"B Ray", "https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/avatars/a/7/2/3/a723cb7c5d08df1e941fcf98848b7d50.jpg"}
    };

    public void getFeaturedArtists(MusicCallback callback) {
        executeCached("featured_artists_vn", 60 * 60 * 1000L, callback, () -> {
            List<Track> artistList = new ArrayList<>();
            for (String[] a : TOP_VIETNAMESE_ARTISTS) {
                Track t = new Track();
                t.setArtist(a[0]);
                t.setTitle("Nghệ sĩ " + a[0]);
                t.setArtworkUrl(a[1]);
                artistList.add(t);
            }
            return artistList;
        });
    }

    // ========== SPECIFIC ARTIST PLAYLIST FETCHER & FILTER ==========

    public boolean isKnownArtist(String query) {
        if (query == null || query.trim().isEmpty()) return false;
        String norm = normalizeText(query);
        for (String[] a : TOP_VIETNAMESE_ARTISTS) {
            String aNorm = normalizeText(a[0]);
            if (norm.equals(aNorm) || (aNorm.length() >= 4 && norm.contains(aNorm)) || (norm.length() >= 4 && aNorm.contains(norm))) {
                return true;
            }
        }
        String[] more = {
            "amee", "duc phuc", "erik", "bich phuong", "phan manh quynh", "huong tram",
            "bui anh tuan", "trung quan", "jack", "truc nhan", "min", "justatee", "rhymastic",
            "low g", "binz", "phuong ly", "hoa minzy", "thinh suy", "chillies", "ngot",
            "da lab", "le cat trong ly", "quang hung masterd"
        };
        for (String m : more) {
            if (norm.equals(m) || norm.contains(m)) return true;
        }
        return false;
    }

    public static boolean isArtistMatch(String trackArtist, String targetArtist) {
        if (trackArtist == null || targetArtist == null) return false;
        String tNorm = normalizeText(trackArtist);
        String targetNorm = normalizeText(targetArtist);
        if (tNorm.isEmpty() || targetNorm.isEmpty()) return false;

        // Exact match
        if (tNorm.equals(targetNorm)) return true;

        // Special handling for key Vietnamese artists:
        if (targetNorm.contains("son tung")) {
            return tNorm.contains("son tung");
        }
        if (targetNorm.equals("hieuthuhai") || targetNorm.contains("hieu thu hai")) {
            return tNorm.contains("hieuthuhai") || tNorm.contains("hieu thu hai");
        }
        if (targetNorm.equals("den") || targetNorm.contains("den vau")) {
            return tNorm.contains("den vau") || tNorm.equals("den")
                    || tNorm.startsWith("den ") || tNorm.endsWith(" den") || tNorm.contains(" den ");
        }
        if (targetNorm.equals("vu") || targetNorm.startsWith("vu ")) {
            return tNorm.equals("vu") || tNorm.startsWith("vu ")
                    || tNorm.endsWith(" vu") || tNorm.contains(" vu ") || tNorm.contains("thai vu");
        }
        if (targetNorm.contains("soobin")) {
            return tNorm.contains("soobin");
        }
        if (targetNorm.contains("wren evans")) {
            return tNorm.contains("wren evans");
        }
        if (targetNorm.contains("tlinh")) {
            return tNorm.contains("tlinh");
        }
        if (targetNorm.contains("grey d") || targetNorm.contains("grey")) {
            return tNorm.contains("grey d") || tNorm.contains("grey");
        }
        if (targetNorm.equals("mck") || targetNorm.contains("rpt mck")) {
            return tNorm.contains("mck");
        }
        if (targetNorm.contains("hoang dung")) {
            return tNorm.contains("hoang dung");
        }
        if (targetNorm.equals("mono")) {
            return tNorm.equals("mono") || tNorm.startsWith("mono ") || tNorm.endsWith(" mono");
        }

        // Generic matching
        if (targetNorm.length() >= 4) {
            if (tNorm.contains(targetNorm) || targetNorm.contains(tNorm)) {
                return true;
            }
        }
        String[] words = targetNorm.split(" ");
        for (String w : words) {
            if (w.length() >= 4 && tNorm.contains(w)) {
                return true;
            }
        }
        return false;
    }

    public List<Track> fetchSongsForSpecificArtist(String targetArtist, int limit) {
        if (targetArtist == null || targetArtist.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String rawArtist = targetArtist.trim();
        List<Track> candidateTracks = new ArrayList<>();
        Set<String> seenTitles = new HashSet<>();

        List<String> zingQueries = getZingQueriesForArtist(rawArtist);
        List<String> deezerQueries = getDeezerQueriesForArtist(rawArtist);
        List<String> itunesQueries = getITunesQueriesForArtist(rawArtist);

        // 1. Fetch from Zing MP3 (Vietnamese high-accuracy + lyrics + Zing IDs)
        for (String q : zingQueries) {
            List<Track> zTracks = zingApi.searchTracksSync(q);
            if (zTracks != null) {
                for (Track t : zTracks) {
                    if (isArtistMatch(t.getArtist(), rawArtist)) {
                        String titleKey = normalizeText(t.getTitle());
                        if (seenTitles.add(titleKey)) {
                            candidateTracks.add(t);
                        }
                    }
                }
            }
        }

        // 2. Fetch from Deezer (high quality tracks, 30s previews, cover art)
        for (String q : deezerQueries) {
            List<Track> dTracks = searchDeezerSync(q);
            if (dTracks != null) {
                for (Track t : dTracks) {
                    if (isArtistMatch(t.getArtist(), rawArtist)) {
                        String titleKey = normalizeText(t.getTitle());
                        if (seenTitles.add(titleKey)) {
                            candidateTracks.add(t);
                        } else {
                            for (Track existing : candidateTracks) {
                                if (normalizeText(existing.getTitle()).equals(titleKey)) {
                                    if ((existing.getPreviewUrl() == null || existing.getPreviewUrl().isEmpty())
                                            && t.getPreviewUrl() != null && !t.getPreviewUrl().isEmpty()) {
                                        existing.setPreviewUrl(t.getPreviewUrl());
                                    }
                                    if ((existing.getArtworkUrl() == null || existing.getArtworkUrl().isEmpty())
                                            && t.getArtworkUrl() != null && !t.getArtworkUrl().isEmpty()) {
                                        existing.setArtworkUrl(t.getArtworkUrl());
                                    }
                                    if (existing.getDurationMillis() <= 0 && t.getDurationMillis() > 0) {
                                        existing.setDurationMillis(t.getDurationMillis());
                                    }
                                    if ((existing.getLyricUrl() == null || existing.getLyricUrl().isEmpty())
                                            && t.getLyricUrl() != null && !t.getLyricUrl().isEmpty()) {
                                        existing.setLyricUrl(t.getLyricUrl());
                                    }
                                    if ((existing.getZingId() == null || existing.getZingId().isEmpty())
                                            && t.getZingId() != null && !t.getZingId().isEmpty()) {
                                        existing.setZingId(t.getZingId());
                                    }
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Fetch from iTunes Vietnam (high quality metadata & preview)
        for (String q : itunesQueries) {
            List<Track> iTracks = searchITunesSync(q);
            if (iTracks != null) {
                for (Track t : iTracks) {
                    if (isArtistMatch(t.getArtist(), rawArtist)) {
                        String titleKey = normalizeText(t.getTitle());
                        if (seenTitles.add(titleKey)) {
                            candidateTracks.add(t);
                        } else {
                            for (Track existing : candidateTracks) {
                                if (normalizeText(existing.getTitle()).equals(titleKey)) {
                                    if ((existing.getPreviewUrl() == null || existing.getPreviewUrl().isEmpty())
                                            && t.getPreviewUrl() != null && !t.getPreviewUrl().isEmpty()) {
                                        existing.setPreviewUrl(t.getPreviewUrl());
                                    }
                                    if (existing.getDurationMillis() <= 0 && t.getDurationMillis() > 0) {
                                        existing.setDurationMillis(t.getDurationMillis());
                                    }
                                    if ((existing.getLyricUrl() == null || existing.getLyricUrl().isEmpty())
                                            && t.getLyricUrl() != null && !t.getLyricUrl().isEmpty()) {
                                        existing.setLyricUrl(t.getLyricUrl());
                                    }
                                    if ((existing.getZingId() == null || existing.getZingId().isEmpty())
                                            && t.getZingId() != null && !t.getZingId().isEmpty()) {
                                        existing.setZingId(t.getZingId());
                                    }
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sort direct solo tracks to the top, then collaborations
        List<Track> directMatches = new ArrayList<>();
        List<Track> collabMatches = new ArrayList<>();
        String normTarget = normalizeText(rawArtist);

        for (Track t : candidateTracks) {
            String normTrackArtist = normalizeText(t.getArtist());
            if (normTrackArtist.equals(normTarget) || normTrackArtist.startsWith(normTarget) || normTarget.startsWith(normTrackArtist)) {
                directMatches.add(t);
            } else {
                collabMatches.add(t);
            }
        }

        List<Track> result = new ArrayList<>();
        result.addAll(directMatches);
        result.addAll(collabMatches);

        if (result.size() > limit) {
            return new ArrayList<>(result.subList(0, limit));
        }
        return result;
    }

    private List<String> getZingQueriesForArtist(String artistName) {
        List<String> queries = new ArrayList<>();
        queries.add(artistName);
        String norm = normalizeText(artistName);
        if (norm.contains("son tung")) {
            queries.add("Sơn Tùng M-TP");
            queries.add("Sơn Tùng");
            queries.add("Son Tung Chung Ta");
            queries.add("Son Tung Muon Roi");
            queries.add("Son Tung Hay Trao Cho Anh");
            queries.add("Son Tung Dung Lam Trai Tim");
            queries.add("Son Tung Co Chac Yeu La Day");
            queries.add("Son Tung Chay Ngay Di");
            queries.add("Son Tung Am Tham Ben Em");
            queries.add("Son Tung Chac Ai Do Se Ve");
            queries.add("Son Tung Lac Troi");
            queries.add("Son Tung Noi Nay Co Anh");
            queries.add("Son Tung Em Cua Ngay Hom Qua");
            queries.add("Son Tung Nang Am Xa Dan");
            queries.add("Son Tung Buong Doi Tay Nhau Ra");
        } else if (norm.equals("vu")) {
            queries.add("Vũ.");
            queries.add("Vũ");
            queries.add("Thái Vũ");
            queries.add("Vu Buoc Qua Nhau");
            queries.add("Vu La Lung");
            queries.add("Vu Nhung Loi Hua Bo Quen");
            queries.add("Vu Dong Kiem Em");
            queries.add("Vu Mot Giac Mo");
        } else if (norm.contains("den")) {
            queries.add("Đen Vâu");
            queries.add("Đen");
            queries.add("Den Nau An Cho Em");
            queries.add("Den Di Ve Nha");
            queries.add("Den Tron Tim");
            queries.add("Den Bai Nay Chill Phet");
            queries.add("Den Mang Tien Ve Cho Me");
            queries.add("Den Hai Trieu Nam");
        } else if (norm.contains("hieuthuhai")) {
            queries.add("HIEUTHUHAI");
            queries.add("HIEUTHUHAI Ngu Mot Minh");
            queries.add("HIEUTHUHAI Khong The Say");
            queries.add("HIEUTHUHAI Ve Tinh");
            queries.add("HIEUTHUHAI Gio Thi Ai Cuoi");
        } else if (norm.contains("soobin")) {
            queries.add("SOOBIN");
            queries.add("Soobin Hoàng Sơn");
            queries.add("Soobin Gia Nhu");
            queries.add("Soobin Phia Sau Mot Co Gai");
            queries.add("Soobin Xin Dung Lang Im");
            queries.add("Soobin Tro Ve");
        } else if (norm.contains("mck")) {
            queries.add("MCK");
            queries.add("RPT MCK");
            queries.add("MCK Chim Sau");
            queries.add("MCK Tai Vi Sao");
            queries.add("MCK Chi Mot Dem Nua Thoi");
        } else if (norm.contains("grey d")) {
            queries.add("Grey D");
            queries.add("Grey D vaicaunoicokhiennguoithaydoi");
            queries.add("Grey D Dua Em Ve Nha");
            queries.add("Grey D Du Bao Thoi Tiet");
        } else if (norm.contains("hoang dung")) {
            queries.add("Hoàng Dũng");
            queries.add("Hoang Dung Nang Tho");
            queries.add("Hoang Dung Doi Loi Tinh Ca");
        } else if (norm.contains("amee")) {
            queries.add("AMEE");
            queries.add("Amee Ung Qua Chung");
            queries.add("Amee Anh Nha O Dau The");
        }
        return queries;
    }

    private List<String> getDeezerQueriesForArtist(String artistName) {
        List<String> queries = new ArrayList<>();
        String norm = normalizeText(artistName);
        if (norm.contains("son tung")) {
            queries.add("Son Tung");
            queries.add("Son Tung M TP");
        } else if (norm.equals("vu")) {
            queries.add("Vu.");
        } else if (norm.contains("den")) {
            queries.add("Den");
            queries.add("Den Vau");
        } else if (norm.contains("soobin")) {
            queries.add("SOOBIN");
            queries.add("Soobin Hoang Son");
        } else if (norm.contains("mck")) {
            queries.add("RPT MCK");
            queries.add("MCK");
        } else if (norm.contains("hoang dung")) {
            queries.add("Hoang Dung");
        } else {
            String clean = artistName.replace("-", " ").replace(".", " ").replaceAll("\\s+", " ").trim();
            queries.add(clean);
            String unaccented = normalizeText(artistName);
            if (!unaccented.equalsIgnoreCase(clean)) {
                queries.add(unaccented);
            }
        }
        return queries;
    }

    private List<String> getITunesQueriesForArtist(String artistName) {
        List<String> queries = new ArrayList<>();
        queries.add(artistName);
        String norm = normalizeText(artistName);
        if (norm.contains("son tung")) {
            queries.add("Son Tung M-TP");
        } else if (norm.equals("vu")) {
            queries.add("Vu.");
        } else if (norm.contains("den")) {
            queries.add("Den");
        } else if (norm.contains("soobin")) {
            queries.add("SOOBIN");
            queries.add("Soobin Hoang Son");
        } else if (norm.contains("hoang dung")) {
            queries.add("Hoang Dung");
        }
        return queries;
    }

    // ========== HELPER METHODS ==========

    private List<Track> deduplicateTracks(List<Track> tracks) {
        List<Track> unique = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Track t : tracks) {
            String key = (t.getTitle() + "||" + t.getArtist()).toLowerCase().trim();
            if (!seen.contains(key)) {
                seen.add(key);
                unique.add(t);
            }
        }
        return unique;
    }

    private List<Track> fetchFromApi(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("Accept", "application/json");

        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            InputStreamReader reader = new InputStreamReader(connection.getInputStream(), "UTF-8");
            AudiusResponse response = gson.fromJson(reader, AudiusResponse.class);
            reader.close();
            
            List<Track> tracks = new ArrayList<>();
            if (response != null && response.data != null) {
                for (AudiusTrack aTrack : response.data) {
                    Track track = new Track();
                    track.setTitle(aTrack.title);
                    if (aTrack.user != null) {
                        track.setArtist(aTrack.user.name);
                    } else {
                        track.setArtist("Unknown Artist");
                    }
                    
                    if (aTrack.artwork != null) {
                        if (aTrack.artwork.artwork1000 != null) track.setArtworkUrl(aTrack.artwork.artwork1000);
                        else if (aTrack.artwork.artwork480 != null) track.setArtworkUrl(aTrack.artwork.artwork480);
                        else track.setArtworkUrl(aTrack.artwork.artwork150);
                    }
                    
                    track.setFullAudioUrl(BASE_URL + "/tracks/" + aTrack.id + "/stream?app_name=" + APP_NAME);
                    track.setPreviewUrl(null);
                    track.setZingId(null);
                    
                    if (aTrack.duration > 0) {
                        track.setDurationMillis(aTrack.duration * 1000);
                    }
                    
                tracks.add(track);
                }
                return tracks;
            }
        }
        return new ArrayList<>();
    }

    public Track lookupITunesTrackSync(long trackId) {
        if (trackId <= 0) return null;
        try {
            URL url = new URL("https://itunes.apple.com/lookup?id=" + trackId + "&country=VN");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setRequestProperty("Accept", "application/json");

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                InputStreamReader reader = new InputStreamReader(conn.getInputStream(), "UTF-8");
                JsonObject json = gson.fromJson(reader, JsonObject.class);
                reader.close();
                if (json != null && json.has("results")) {
                    JsonArray results = json.getAsJsonArray("results");
                    if (results.size() > 0) {
                        JsonObject obj = results.get(0).getAsJsonObject();
                        Track track = new Track();
                        track.setId(trackId);
                        track.setTitle(obj.has("trackName") ? obj.get("trackName").getAsString() : "");
                        track.setArtist(obj.has("artistName") ? obj.get("artistName").getAsString() : "");
                        if (obj.has("artworkUrl100")) {
                            track.setArtworkUrl(obj.get("artworkUrl100").getAsString().replace("100x100bb", "600x600bb"));
                        }
                        if (obj.has("trackTimeMillis")) {
                            track.setDurationMillis(obj.get("trackTimeMillis").getAsLong());
                        }
                        if (obj.has("previewUrl")) {
                            track.setPreviewUrl(obj.get("previewUrl").getAsString());
                            track.setFullAudioUrl(null);
                        }
                        return track;
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
