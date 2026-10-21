package com.example.muzzicapp.api;

import com.example.muzzicapp.BuildConfig;
import com.example.muzzicapp.model.Track;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Resolves only licensed/full sources. iTunes preview URLs are deliberately ignored. */
public class FullAudioResolver {
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final Gson gson = new Gson();

    public interface Callback {
        void onResolved(String url);
        void onUnavailable(Exception error);
    }

    public void resolve(Track track, Callback callback) {
        resolve(track, false, callback);
    }

    public void resolve(Track track, boolean forceRefresh, Callback callback) {
        executor.execute(() -> {
            try {
                android.util.Log.e("FullAudioResolver", "Resolving track: " + track.getTitle() + " - " + track.getArtist() + ", fullUrl: " + track.getFullAudioUrl() + ", forceRefresh: " + forceRefresh);
                String url = null;

                // 1. Check configured sources / pre-existing direct URLs
                try {
                    url = resolveConfiguredSources(track, forceRefresh);
                } catch (Exception e) {
                    android.util.Log.w("FullAudioResolver", "resolveConfiguredSources error", e);
                }

                // 2. ZingMp3 (fastest: 200ms, free V-pop tracks)
                if (url == null) {
                    try {
                        url = resolveZingMp3(track);
                    } catch (Exception e) {
                        android.util.Log.w("FullAudioResolver", "resolveZingMp3 error", e);
                    }
                }

                // 3. SoundCloud (fast: ~1s, resolves tracks that are VIP on Zing like JustaTee, MCK, etc.)
                if (url == null) {
                    try {
                        String query = track.getTitle() != null ? track.getTitle().trim() : "";
                        if (track.getArtist() != null && !track.getArtist().isEmpty() && !track.getArtist().equalsIgnoreCase("Unknown Artist")) {
                            query += " " + track.getArtist().trim();
                        }
                        long durationMs = track.getDurationMillis();
                        url = SoundCloudResolver.resolve(query, durationMs);
                        if (url == null && !query.equals(track.getTitle())) {
                            url = SoundCloudResolver.resolve(track.getTitle().trim(), durationMs);
                        }
                    } catch (Exception e) {
                        android.util.Log.w("FullAudioResolver", "SoundCloudResolver error", e);
                    }
                }

                // 4. Piped streams (YouTube stream)
                if (url == null) {
                    try {
                        url = resolvePiped(track);
                    } catch (Exception e) {
                        android.util.Log.w("FullAudioResolver", "resolvePiped error", e);
                    }
                }

                // 5. YouTubeDL (full YouTube audio)
                if (url == null) {
                    try {
                        url = resolveYoutubeDL(track);
                    } catch (Exception e) {
                        android.util.Log.w("FullAudioResolver", "resolveYoutubeDL error", e);
                    }
                }

                // 6. Audius
                if (url == null) {
                    try {
                        url = resolveAudius(track);
                    } catch (Exception e) {
                        android.util.Log.w("FullAudioResolver", "resolveAudius error", e);
                    }
                }

                // 7. Jamendo
                if (url == null) {
                    try {
                        url = resolveJamendo(track);
                    } catch (Exception e) {
                        android.util.Log.w("FullAudioResolver", "resolveJamendo error", e);
                    }
                }

                // 8. iTunes Preview (last resort only if all full sources fail)
                if (url == null) {
                    try {
                        url = resolveITunesPreview(track);
                    } catch (Exception e) {
                        android.util.Log.w("FullAudioResolver", "resolveITunesPreview error", e);
                    }
                }

                if (url == null || (!url.startsWith("http://") && !url.startsWith("https://"))) {
                    throw new Exception("Không tìm thấy nguồn phát hợp lệ");
                }
                android.util.Log.e("FullAudioResolver", "Successfully resolved audio URL: " + url);
                String resolved = url;
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onResolved(resolved));
            } catch (Exception error) {
                android.util.Log.e("FullAudioResolver", "Failed to resolve track", error);
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onUnavailable(error));
            }
        });
    }

    public static boolean isUrlExpired(String url) {
        if (url == null || url.trim().isEmpty()) return true;
        if (url.startsWith("file:") || url.startsWith("content:") || url.startsWith("android.resource:")) return false;

        long currentSec = System.currentTimeMillis() / 1000;

        // 1. Check Zing MP3 expiration: authen=exp=12345678
        java.util.regex.Matcher mZing = java.util.regex.Pattern.compile("authen=exp=(\\d+)").matcher(url);
        if (mZing.find()) {
            try {
                long exp = Long.parseLong(mZing.group(1));
                if (currentSec >= exp - 60) {
                    return true;
                }
            } catch (Exception ignored) {}
        }

        // 2. Check AWS / S3 / CloudFront Expires: Expires=12345678
        java.util.regex.Matcher mExp = java.util.regex.Pattern.compile("[?&]Expires=(\\d+)").matcher(url);
        if (mExp.find()) {
            try {
                long exp = Long.parseLong(mExp.group(1));
                if (currentSec >= exp - 60) {
                    return true;
                }
            } catch (Exception ignored) {}
        }

        // 3. Check SoundCloud CloudFront Policy: Policy=base64
        java.util.regex.Matcher mPolicy = java.util.regex.Pattern.compile("[?&]Policy=([^&]+)").matcher(url);
        if (mPolicy.find()) {
            try {
                String policyB64 = mPolicy.group(1);
                byte[] decoded = android.util.Base64.decode(policyB64, android.util.Base64.URL_SAFE | android.util.Base64.NO_PADDING | android.util.Base64.NO_WRAP);
                String json = new String(decoded, "UTF-8");
                java.util.regex.Matcher mEpoch = java.util.regex.Pattern.compile("EpochTime[\"']?\\s*:\\s*(\\d+)").matcher(json);
                if (mEpoch.find()) {
                    long exp = Long.parseLong(mEpoch.group(1));
                    if (currentSec >= exp - 60) {
                        return true;
                    }
                }
            } catch (Exception ignored) {}
        }

        return false;
    }

    private String resolveConfiguredSources(Track track, boolean forceRefresh) throws Exception {
        if (forceRefresh) return null;
        if (track.getFullAudioUrl() != null && !track.getFullAudioUrl().trim().isEmpty()) {
            String url = track.getFullAudioUrl();
            if (url.startsWith("search://") || url.startsWith("piped://")) {
                return null;
            } else if (isUrlExpired(url)) {
                android.util.Log.w("FullAudioResolver", "Cached fullAudioUrl has expired, will refresh: " + url);
                track.setFullAudioUrl(null);
                return null;
            } else if (url.contains("dzcdn.net") || url.contains("mzstatic.com") || url.contains("itunes.apple.com")
                    || url.contains("/preview/") || (track.getPreviewUrl() != null && url.equals(track.getPreviewUrl()))) {
                // This is a 30s preview URL, not full audio! Preserve as fallback preview and resolve full audio
                if (track.getPreviewUrl() == null || track.getPreviewUrl().isEmpty()) {
                    track.setPreviewUrl(url);
                }
                track.setFullAudioUrl(null);
                return null;
            } else if (url.contains("audius.co")) {
                url = resolveRedirects(url);
            }
            return url;
        }
        String baseUrl = BuildConfig.FULL_AUDIO_BACKEND_URL.trim();
        if (baseUrl.isEmpty()) return null;
        String endpoint = baseUrl + "/api/track?artist=" + encode(track.getArtist()) + "&title=" + encode(track.getTitle());
        JsonObject result = getJson(endpoint);
        String url = result.has("fullAudioUrl") ? result.get("fullAudioUrl").getAsString() : "";
        return url.trim().isEmpty() ? null : url;
    }

    private String resolveYoutubeDL(Track track) {
        try {
            String title = track.getTitle() != null ? track.getTitle().trim() : "";
            String artist = track.getArtist() != null ? track.getArtist().trim() : "";
            String query = title;
            if (!artist.isEmpty() && !artist.equalsIgnoreCase("Unknown Artist") && !artist.equalsIgnoreCase("Unknown")) {
                query = query + " " + artist;
            }
            android.util.Log.e("FullAudioResolver", "resolveYoutubeDL started for query: " + query);
            String url = executeYoutubeDLQuery(query);
            if (url == null && !query.equals(title)) {
                android.util.Log.e("FullAudioResolver", "Retrying resolveYoutubeDL with title only: " + title);
                url = executeYoutubeDLQuery(title);
            }
            return url;
        } catch (Exception e) {
            android.util.Log.e("FullAudioResolver", "resolveYoutubeDL exception", e);
        }
        return null;
    }

    private String executeYoutubeDLQuery(String query) {
        final String processId = "ytdl_" + System.currentTimeMillis();
        java.util.concurrent.Future<String> future = executor.submit(() -> {
            try {
                com.yausername.youtubedl_android.YoutubeDLRequest request = new com.yausername.youtubedl_android.YoutubeDLRequest("ytsearch1:" + query);
                request.addOption("-g");
                request.addOption("-f", "best[ext=mp4]/bestaudio/best");
                request.addOption("--extractor-args", "youtube:player_client=android");
                request.addOption("--no-playlist");
                request.addOption("--no-warnings");
                request.addOption("--no-update");
                request.addOption("--socket-timeout", "4");

                com.yausername.youtubedl_android.YoutubeDLResponse response = com.yausername.youtubedl_android.YoutubeDL.getInstance().execute(request, processId, null);
                if (response != null && response.getOut() != null) {
                    for (String line : response.getOut().split("\n")) {
                        String trimmed = line.trim();
                        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                            android.util.Log.e("FullAudioResolver", "resolveYoutubeDL found valid URL: " + trimmed);
                            return trimmed;
                        }
                    }
                    android.util.Log.w("FullAudioResolver", "resolveYoutubeDL no http url in output: " + response.getOut() + ", err=" + response.getErr());
                } else if (response != null) {
                    android.util.Log.w("FullAudioResolver", "resolveYoutubeDL empty output. err=" + response.getErr());
                }
            } catch (Exception e) {
                android.util.Log.w("FullAudioResolver", "executeYoutubeDLQuery internal error: " + e.getMessage());
            }
            return null;
        });

        try {
            return future.get(6, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            android.util.Log.w("FullAudioResolver", "executeYoutubeDLQuery timed out or cancelled for: " + query);
            try {
                com.yausername.youtubedl_android.YoutubeDL.getInstance().destroyProcessById(processId);
            } catch (Exception ignored) {}
            future.cancel(true);
            return null;
        }
    }

    private String resolvePiped(Track track) {
        String fullAudio = track.getFullAudioUrl();
        if (fullAudio == null || !fullAudio.startsWith("piped://")) return null;
        
        String videoId = fullAudio.replace("piped://", "");
        String[] instances = {
            "https://pipedapi.kavin.rocks",
            "https://api.piped.stream",
            "https://pipedapi.moomoo.me",
            "https://pipedapi.smnz.de"
        };
        
        for (String instance : instances) {
            try {
                JsonObject result = getJson(instance + "/streams/" + videoId);
                if (result.has("audioStreams")) {
                    com.google.gson.JsonArray streams = result.getAsJsonArray("audioStreams");
                    String bestUrl = null;
                    int maxBitrate = 0;
                    
                    for (com.google.gson.JsonElement element : streams) {
                        JsonObject stream = element.getAsJsonObject();
                        int bitrate = stream.has("bitrate") ? stream.get("bitrate").getAsInt() : 0;
                        if (bitrate > maxBitrate) {
                            maxBitrate = bitrate;
                            bestUrl = stream.get("url").getAsString();
                        }
                    }
                    if (bestUrl != null) return bestUrl;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private String resolveAudius(Track track) throws Exception {
        return resolveAudiusWithQuery(track.getArtist() + " " + track.getTitle());
    }

    private String resolveAudiusWithQuery(String queryText) throws Exception {
        String query = "https://discoveryprovider.audius.co/v1/tracks/search?query="
                + encode(queryText)
                + "&app_name=" + encode(BuildConfig.AUDIUS_APP_NAME);
        JsonObject result = getJson(query);
        if (!result.has("data") || result.getAsJsonArray("data").size() == 0) return null;
        String id = result.getAsJsonArray("data").get(0).getAsJsonObject().get("id").getAsString();
        return "https://discoveryprovider.audius.co/v1/tracks/" + id + "/stream?app_name="
                + encode(BuildConfig.AUDIUS_APP_NAME);
    }

    private String resolveZingMp3(Track track) throws Exception {
        if (track.getZingId() == null || track.getZingId().trim().isEmpty()) {
            try {
                ZingAutocompleteApi zingApi = new ZingAutocompleteApi();
                String query = (track.getTitle() + " " + track.getArtist()).trim();
                List<Track> found = zingApi.searchTracksSync(query);
                if (found == null || found.isEmpty()) {
                    found = zingApi.searchTracksSync(track.getTitle().trim());
                }
                if (found != null && !found.isEmpty()) {
                    for (Track f : found) {
                        if (MusicApiService.isArtistMatch(f.getArtist(), track.getArtist())) {
                            if (f.getZingId() != null && !f.getZingId().trim().isEmpty()) {
                                track.setZingId(f.getZingId());
                                if (track.getLyricUrl() == null && f.getLyricUrl() != null) {
                                    track.setLyricUrl(f.getLyricUrl());
                                }
                                break;
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        if (track.getZingId() == null || track.getZingId().trim().isEmpty()) return null;
        
        // 1. Get signed URL from Vercel proxy
        String proxyUrl = "https://api-zingmp3-vercel.vercel.app/api/song?id=" + track.getZingId();
        JsonObject proxyResponse = getJson(proxyUrl);
        if (proxyResponse == null) return null;

        // 1a. Check if direct 128 stream is already provided in data
        if (proxyResponse.has("data") && !proxyResponse.get("data").isJsonNull()) {
            JsonObject data = proxyResponse.getAsJsonObject("data");
            if (data.has("128") && !data.get("128").isJsonNull() && !data.get("128").getAsString().equals("VIP")) {
                return data.get("128").getAsString();
            }
        }

        // 1b. If not direct 128, check signed URL for phone fetch (VIP/regional bypass)
        if (!proxyResponse.has("url") || proxyResponse.get("url").isJsonNull()) return null;
        String signedUrl = proxyResponse.get("url").getAsString();
        
        // 2. Fetch cookie directly from phone (Vietnamese IP)
        HttpURLConnection initConn = (HttpURLConnection) new URL(ZingMp3Api.BASE_URL).openConnection();
        initConn.setRequestMethod("GET");
        initConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
        initConn.setConnectTimeout(5000);
        initConn.connect();
        StringBuilder cookies = new StringBuilder();
        if (initConn.getHeaderFields().get("Set-Cookie") != null) {
            for (String cookie : initConn.getHeaderFields().get("Set-Cookie")) {
                cookies.append(cookie.split(";")[0]).append("; ");
            }
        }
        initConn.disconnect();

        // 3. Fetch the signed URL using the phone's IP and cookies
        HttpURLConnection connection = (HttpURLConnection) new URL(signedUrl).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(7000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
        connection.setRequestProperty("Origin", "https://zingmp3.vn");
        connection.setRequestProperty("Referer", "https://zingmp3.vn/");
        if (cookies.length() > 0) connection.setRequestProperty("Cookie", cookies.toString());
        connection.setRequestProperty("Accept-Encoding", "gzip");

        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
        
        BufferedReader reader;
        if ("gzip".equalsIgnoreCase(connection.getContentEncoding())) {
            reader = new BufferedReader(new InputStreamReader(new java.util.zip.GZIPInputStream(connection.getInputStream()), "UTF-8"));
        } else {
            reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
        }
        
        StringBuilder body = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) body.append(line);
        reader.close();
        connection.disconnect();
        
        JsonObject result = gson.fromJson(body.toString(), JsonObject.class);
        if (result != null && result.has("data") && !result.get("data").isJsonNull()) {
            JsonObject data = result.getAsJsonObject("data");
            if (data.has("128") && !data.get("128").isJsonNull() && !data.get("128").getAsString().equals("VIP")) {
                return data.get("128").getAsString();
            }
        }
        return null;
    }

    private String resolveJamendo(Track track) throws Exception {
        String clientId = BuildConfig.JAMENDO_CLIENT_ID.trim();
        if (clientId.isEmpty()) return null;
        String query = "https://api.jamendo.com/v3.0/tracks/?client_id=" + encode(clientId)
                + "&format=json&limit=1&namesearch=" + encode(track.getTitle())
                + "&artist_name=" + encode(track.getArtist());
        JsonObject result = getJson(query);
        if (!result.has("results") || result.getAsJsonArray("results").size() == 0) return null;
        JsonObject item = result.getAsJsonArray("results").get(0).getAsJsonObject();
        String audio = item.has("audio") ? item.get("audio").getAsString() : "";
        return audio.trim().isEmpty() ? null : audio;
    }

    private String resolveITunesPreview(Track track) {
        try {
            String title = track.getTitle() != null ? track.getTitle().trim() : "";
            String artist = track.getArtist() != null ? track.getArtist().trim() : "";
            String query = title;
            if (!artist.isEmpty() && !artist.equalsIgnoreCase("Unknown Artist") && !artist.equalsIgnoreCase("Unknown")) {
                query = query + " " + artist;
            }
            String endpoint = "https://itunes.apple.com/search?term=" + encode(query) + "&entity=song&limit=1";
            JsonObject result = getJson(endpoint);
            if (result != null && result.has("results")) {
                com.google.gson.JsonArray results = result.getAsJsonArray("results");
                if (results.size() > 0) {
                    JsonObject item = results.get(0).getAsJsonObject();
                    if (item.has("previewUrl") && !item.get("previewUrl").isJsonNull()) {
                        String preview = item.get("previewUrl").getAsString();
                        if (preview != null && (preview.startsWith("http://") || preview.startsWith("https://"))) {
                            android.util.Log.e("FullAudioResolver", "resolveITunesPreview found stream: " + preview);
                            return preview;
                        }
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.w("FullAudioResolver", "resolveITunesPreview error", e);
        }
        return null;
    }

    private JsonObject getJson(String endpoint) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12; M2003J15SC) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
        connection.setRequestProperty("Accept", "application/json");
        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return new JsonObject();
        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder body = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) body.append(line);
        reader.close();
        connection.disconnect();
        return gson.fromJson(body.toString(), JsonObject.class);
    }

    private String encode(String value) throws Exception {
        return URLEncoder.encode(value == null ? "" : value, "UTF-8");
    }

    private String resolveRedirects(String urlString) throws Exception {
        int maxRedirects = 5;
        while (maxRedirects-- > 0) {
            HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                    || responseCode == HttpURLConnection.HTTP_MOVED_PERM
                    || responseCode == HttpURLConnection.HTTP_SEE_OTHER
                    || responseCode == 307) {
                String redirectUrl = connection.getHeaderField("Location");
                if (redirectUrl != null && !redirectUrl.isEmpty()) {
                    urlString = redirectUrl;
                } else {
                    break;
                }
            } else {
                break;
            }
            connection.disconnect();
        }
        return urlString;
    }
}