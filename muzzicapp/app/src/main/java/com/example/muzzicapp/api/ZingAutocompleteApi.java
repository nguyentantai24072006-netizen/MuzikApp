package com.example.muzzicapp.api;

import android.os.Handler;
import android.os.Looper;

import com.example.muzzicapp.model.Track;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

public class ZingAutocompleteApi {
    private static final String SECRET = "2aa2d1c561e809b267f3638c4bbe4afa";
    public static final String API_KEY = "88265e23d4284f25963e6eedac8fbfa3";
    private static final String VERSION = "1.11.11";

    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();

    private static String cachedCookie = null;
    private static long cookieTimestamp = 0;

    public interface ZingCallback {
        void onSuccess(List<Track> tracks);
        void onError(Exception e);
    }

    private synchronized String getZingCookie() {
        long now = System.currentTimeMillis();
        if (cachedCookie != null && (now - cookieTimestamp) < 3600000) {
            return cachedCookie;
        }
        try {
            URL url = new URL("https://zingmp3.vn");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setInstanceFollowRedirects(true);
            conn.connect();

            List<String> cookies = conn.getHeaderFields().get("Set-Cookie");
            if (cookies != null && !cookies.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (String c : cookies) {
                    if (sb.length() > 0) sb.append("; ");
                    String[] parts = c.split(";");
                    sb.append(parts[0].trim());
                }
                cachedCookie = sb.toString();
                cookieTimestamp = now;
            }
            conn.disconnect();
        } catch (Exception ignored) {}
        return cachedCookie != null ? cachedCookie : "";
    }

    public List<Track> searchFullTracksSync(String query) {
        List<Track> tracks = new ArrayList<>();
        try {
            String cookie = getZingCookie();
            long ctime = System.currentTimeMillis() / 1000;
            String apiPath = "/api/v2/search/multi";

            String hash256 = ZingMp3Api.getHash256("ctime=" + ctime + "version=" + VERSION);
            String sig = ZingMp3Api.getHmac512(apiPath + hash256, SECRET);

            String encodedQuery = URLEncoder.encode(query, "UTF-8");
            String urlString = "https://zingmp3.vn" + apiPath
                    + "?q=" + encodedQuery
                    + "&ctime=" + ctime
                    + "&version=" + VERSION
                    + "&apiKey=" + API_KEY
                    + "&sig=" + sig;

            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.setRequestProperty("Accept", "application/json");
            if (cookie != null && !cookie.isEmpty()) {
                conn.setRequestProperty("Cookie", cookie);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                InputStream is = conn.getInputStream();
                String encoding = conn.getHeaderField("Content-Encoding");
                if ("gzip".equalsIgnoreCase(encoding)) {
                    is = new GZIPInputStream(is);
                }
                InputStreamReader reader = new InputStreamReader(is, "UTF-8");
                JsonObject response = gson.fromJson(reader, JsonObject.class);
                reader.close();

                if (response != null && response.has("data")) {
                    JsonObject data = response.getAsJsonObject("data");
                    if (data.has("songs")) {
                        JsonArray songs = data.getAsJsonArray("songs");
                        for (JsonElement el : songs) {
                            if (!el.isJsonObject()) continue;
                            JsonObject s = el.getAsJsonObject();
                            Track track = new Track();
                            track.setTitle(s.has("title") ? s.get("title").getAsString() : "Unknown Title");
                            track.setArtist(s.has("artistsNames") ? s.get("artistsNames").getAsString() : "Unknown Artist");
                            if (s.has("thumbnailM") && !s.get("thumbnailM").isJsonNull()) {
                                track.setArtworkUrl(s.get("thumbnailM").getAsString());
                            } else if (s.has("thumbnail") && !s.get("thumbnail").isJsonNull()) {
                                track.setArtworkUrl(s.get("thumbnail").getAsString());
                            }
                            if (s.has("duration") && !s.get("duration").isJsonNull()) {
                                track.setDurationMillis(s.get("duration").getAsLong() * 1000);
                            }
                            if (s.has("encodeId") && !s.get("encodeId").isJsonNull()) {
                                track.setZingId(s.get("encodeId").getAsString());
                            }
                            track.setFullAudioUrl(null);
                            tracks.add(track);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return tracks;
    }

    public List<Track> searchTracksSync(String query) {
        List<Track> tracks = new ArrayList<>();
        try {
            String encodedQuery = URLEncoder.encode(query, "UTF-8");
            String urlString = "https://ac.zingmp3.vn/v1/web/ac-suggestions?query=" + encodedQuery;

            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(6000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            connection.setRequestProperty("Accept", "application/json");

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                InputStream is = connection.getInputStream();
                String encoding = connection.getHeaderField("Content-Encoding");
                if ("gzip".equalsIgnoreCase(encoding)) {
                    is = new GZIPInputStream(is);
                }
                InputStreamReader reader = new InputStreamReader(is, "UTF-8");
                JsonObject response = gson.fromJson(reader, JsonObject.class);
                reader.close();

                if (response != null && response.has("data")) {
                    JsonObject data = response.getAsJsonObject("data");
                    if (data.has("items")) {
                        JsonArray items = data.getAsJsonArray("items");
                        for (JsonElement itemEl : items) {
                            JsonObject item = itemEl.getAsJsonObject();
                            if (item.has("suggestions")) {
                                JsonArray suggestions = item.getAsJsonArray("suggestions");
                                for (JsonElement sugEl : suggestions) {
                                    JsonObject sug = sugEl.getAsJsonObject();
                                    if (sug.has("type") && sug.get("type").getAsInt() == 1) { // Type 1 is Song
                                        Track track = new Track();
                                        track.setTitle(sug.has("title") ? sug.get("title").getAsString() : "Unknown Title");

                                        String artistName = "Unknown Artist";
                                        if (sug.has("artists")) {
                                            JsonArray artists = sug.getAsJsonArray("artists");
                                            if (artists.size() > 0) {
                                                StringBuilder sb = new StringBuilder();
                                                for (int aIdx = 0; aIdx < artists.size(); aIdx++) {
                                                    if (aIdx > 0) sb.append(", ");
                                                    sb.append(artists.get(aIdx).getAsJsonObject().get("name").getAsString());
                                                }
                                                artistName = sb.toString();
                                            }
                                        }
                                        track.setArtist(artistName);

                                        if (sug.has("thumb")) {
                                            track.setArtworkUrl(sug.get("thumb").getAsString());
                                        }

                                        if (sug.has("duration")) {
                                            track.setDurationMillis(sug.get("duration").getAsLong() * 1000);
                                        }

                                        track.setZingId(sug.get("id").getAsString());
                                        if (sug.has("lyricLink") && !sug.get("lyricLink").isJsonNull()) {
                                            track.setLyricUrl(sug.get("lyricLink").getAsString());
                                        }
                                        track.setFullAudioUrl(null);

                                        tracks.add(track);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return tracks;
    }

    public List<Track> searchAllTracksSync(String query) {
        Map<String, Track> map = new LinkedHashMap<>();

        // 1. Exact query on Zing
        List<Track> exactResults = searchTracksSync(query);
        for (Track t : exactResults) {
            String key = (t.getTitle() + " - " + t.getArtist()).toLowerCase().trim();
            map.put(key, t);
        }

        // 2. Multi-word variations if query has 3 or more words
        String trimmed = query.trim();
        String[] words = trimmed.split("\\s+");
        if (words.length >= 3) {
            // Drop last word: e.g. "vừa lòng em chưa" -> "vừa lòng em"
            int lastSpace = trimmed.lastIndexOf(' ');
            if (lastSpace > 0) {
                String sub1 = trimmed.substring(0, lastSpace).trim();
                List<Track> subResults1 = searchTracksSync(sub1);
                for (Track t : subResults1) {
                    String key = (t.getTitle() + " - " + t.getArtist()).toLowerCase().trim();
                    if (!map.containsKey(key)) {
                        map.put(key, t);
                    }
                }
            }

            // First 2 words: e.g. "vừa lòng"
            if (words.length >= 4) {
                String sub2 = words[0] + " " + words[1];
                List<Track> subResults2 = searchTracksSync(sub2);
                for (Track t : subResults2) {
                    String key = (t.getTitle() + " - " + t.getArtist()).toLowerCase().trim();
                    if (!map.containsKey(key)) {
                        map.put(key, t);
                    }
                }
            }
        }

        return new ArrayList<>(map.values());
    }

    public void searchTracks(String query, ZingCallback callback) {
        executor.execute(() -> {
            try {
                List<Track> tracks = searchAllTracksSync(query);
                if (!tracks.isEmpty()) {
                    mainHandler.post(() -> callback.onSuccess(tracks));
                } else {
                    mainHandler.post(() -> callback.onError(new Exception("No results found on Zing")));
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }
}
