package com.example.muzzicapp.api;

import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SoundCloudResolver {
    private static final String TAG = "SoundCloudResolver";
    private static final String DEFAULT_CLIENT_ID = "vI5BsvpTIlavDLl7RDbbcFAPg8kls8Bg";
    private static String cachedClientId = DEFAULT_CLIENT_ID;
    private static final Gson gson = new Gson();

    private static final OkHttpClient client;

    static {
        Dns smartDns = new Dns() {
            @Override
            public List<InetAddress> lookup(String hostname) throws UnknownHostException {
                if ("api-v2.soundcloud.com".equalsIgnoreCase(hostname)) {
                    try {
                        List<InetAddress> addresses = new ArrayList<>();
                        // Prioritize nearby CloudFront Anycast IPs that respond in 3ms in Vietnam
                        addresses.add(InetAddress.getByAddress("api-v2.soundcloud.com", new byte[]{3, (byte) 165, 50, 18}));
                        addresses.add(InetAddress.getByAddress("api-v2.soundcloud.com", new byte[]{3, (byte) 165, 50, 65}));
                        addresses.add(InetAddress.getByAddress("api-v2.soundcloud.com", new byte[]{3, (byte) 165, 50, 102}));
                        addresses.add(InetAddress.getByAddress("api-v2.soundcloud.com", new byte[]{3, (byte) 165, 50, 26}));
                        try {
                            addresses.addAll(Dns.SYSTEM.lookup(hostname));
                        } catch (Exception ignored) {}
                        return addresses;
                    } catch (Exception ignored) {}
                }
                return Dns.SYSTEM.lookup(hostname);
            }
        };

        client = new OkHttpClient.Builder()
                .dns(smartDns)
                .protocols(Arrays.asList(okhttp3.Protocol.HTTP_1_1))
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .followRedirects(true)
                .eventListener(new okhttp3.EventListener() {
                    @Override
                    public void connectStart(okhttp3.Call call, java.net.InetSocketAddress inetSocketAddress, java.net.Proxy proxy) {
                        Log.e(TAG, "connectStart: " + inetSocketAddress);
                    }
                    @Override
                    public void connectFailed(okhttp3.Call call, java.net.InetSocketAddress inetSocketAddress, java.net.Proxy proxy, okhttp3.Protocol protocol, IOException ioe) {
                        Log.e(TAG, "connectFailed: " + inetSocketAddress + " error: " + ioe.getMessage());
                    }
                    @Override
                    public void secureConnectStart(okhttp3.Call call) {
                        Log.e(TAG, "secureConnectStart");
                    }
                    @Override
                    public void secureConnectEnd(okhttp3.Call call, okhttp3.Handshake handshake) {
                        Log.e(TAG, "secureConnectEnd TLS: " + (handshake != null ? handshake.tlsVersion() : "null"));
                    }
                })
                .build();
    }

    public static String resolve(String query) {
        return resolve(query, 0);
    }

    public static String resolve(String query, long targetDurationMs) {
        if (query == null || query.trim().isEmpty()) return null;
        query = query.replace('_', ' ').trim();
        try {
            Log.e(TAG, "Resolving SoundCloud query: " + query + ", targetDuration: " + targetDurationMs);
            String url = querySoundCloud(query, cachedClientId, targetDurationMs);
            if (url != null) return url;
        } catch (Exception e) {
            Log.e(TAG, "SoundCloud resolve error", e);
        }
        return null;
    }

    private static String querySoundCloud(String query, String clientId, long targetDurationMs) {
        try {
            String encoded = URLEncoder.encode(query, "UTF-8");
            String searchUrl = "https://api-v2.soundcloud.com/search/tracks?q=" + encoded + "&client_id=" + clientId + "&limit=12";
            JsonObject res = getJson(searchUrl);
            if (res == null || !res.has("collection")) {
                Log.w(TAG, "SoundCloud search returned no collection for: " + query);
                return null;
            }

            JsonArray collection = res.getAsJsonArray("collection");
            Log.e(TAG, "SoundCloud search found " + collection.size() + " tracks for: " + query);
            
            final String queryLower = query.toLowerCase();
            final boolean wantsRemix = queryLower.contains("remix");
            List<JsonObject> candidateTracks = new ArrayList<>();
            for (JsonElement el : collection) {
                if (el.isJsonObject()) candidateTracks.add(el.getAsJsonObject());
            }
            if (candidateTracks.size() > 1) {
                java.util.Collections.sort(candidateTracks, (a, b) -> {
                    int scoreA = scoreTrack(a, queryLower, wantsRemix, targetDurationMs);
                    int scoreB = scoreTrack(b, queryLower, wantsRemix, targetDurationMs);
                    return Integer.compare(scoreB, scoreA); // Higher score first
                });
            }

            for (JsonObject track : candidateTracks) {
                if (track.has("media") && track.getAsJsonObject("media").has("transcodings")) {
                    JsonArray transcodings = track.getAsJsonObject("media").getAsJsonArray("transcodings");
                    String progressiveUrl = null;
                    String hlsUrl = null;
                    for (JsonElement tEl : transcodings) {
                        if (!tEl.isJsonObject()) continue;
                        JsonObject t = tEl.getAsJsonObject();
                        String protocol = t.has("format") && t.getAsJsonObject("format").has("protocol")
                                ? t.getAsJsonObject("format").get("protocol").getAsString() : "";
                        if ("progressive".equalsIgnoreCase(protocol)) {
                            progressiveUrl = t.get("url").getAsString();
                            break;
                        } else if ("hls".equalsIgnoreCase(protocol) && hlsUrl == null) {
                            hlsUrl = t.get("url").getAsString();
                        }
                    }

                    String targetTrans = progressiveUrl != null ? progressiveUrl : hlsUrl;
                    if (targetTrans != null) {
                        JsonObject streamInfo = getJson(targetTrans + "?client_id=" + clientId);
                        if (streamInfo != null && streamInfo.has("url")) {
                            String streamUrl = streamInfo.get("url").getAsString();
                            Log.e(TAG, "Successfully resolved SoundCloud stream URL: " + streamUrl);
                            return streamUrl;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "querySoundCloud error", e);
        }
        return null;
    }

    private static JsonObject getJson(String endpoint) {
        try {
            Request request = new Request.Builder()
                    .url(endpoint)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 12; M2003J15SC) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Origin", "https://soundcloud.com")
                    .header("Referer", "https://soundcloud.com/")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.code() == 401 || response.code() == 403) {
                    Log.w(TAG, "SoundCloud returned HTTP " + response.code() + ", client ID may be expired. Refreshing...");
                    String newId = fetchClientId();
                    if (newId != null && !newId.equals(cachedClientId)) {
                        cachedClientId = newId;
                        String updatedEndpoint = endpoint.replaceAll("client_id=[a-zA-Z0-9_-]+", "client_id=" + newId);
                        Request retryReq = request.newBuilder().url(updatedEndpoint).build();
                        try (Response retryResp = client.newCall(retryReq).execute()) {
                            if (retryResp.isSuccessful() && retryResp.body() != null) {
                                return gson.fromJson(retryResp.body().string(), JsonObject.class);
                            }
                        } catch (Exception ignored) {}
                    }
                    return null;
                }
                if (!response.isSuccessful() || response.body() == null) {
                    Log.w(TAG, "SoundCloud HTTP " + response.code() + " for: " + endpoint);
                    return null;
                }
                String bodyStr = response.body().string();
                return gson.fromJson(bodyStr, JsonObject.class);
            }
        } catch (Exception e) {
            Log.e(TAG, "SoundCloud getJson error for " + endpoint + ": " + e.getMessage());
            return null;
        }
    }

    private static String fetchClientId() {
        try {
            Request request = new Request.Builder()
                    .url("https://soundcloud.com")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 12; M2003J15SC) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    .build();
            String html;
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return null;
                html = response.body().string();
            }

            Matcher m = Pattern.compile("src=\"(https://a-v2\\.sndcdn\\.com/assets/[^\"]+\\.js)\"").matcher(html);
            List<String> scriptUrls = new ArrayList<>();
            while (m.find()) scriptUrls.add(m.group(1));

            for (int i = scriptUrls.size() - 1; i >= 0 && i >= scriptUrls.size() - 4; i--) {
                Request jsReq = new Request.Builder().url(scriptUrls.get(i)).build();
                try (Response jsResp = client.newCall(jsReq).execute()) {
                    if (jsResp.isSuccessful() && jsResp.body() != null) {
                        String js = jsResp.body().string();
                        Matcher idMatcher = Pattern.compile("client_id[:=]\"?([a-zA-Z0-9]{32})\"?").matcher(js);
                        if (idMatcher.find()) {
                            return idMatcher.group(1);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static int scoreTrack(JsonObject t, String queryLower, boolean wantsRemix, long targetDurationMs) {
        String title = t.has("title") && !t.get("title").isJsonNull() ? t.get("title").getAsString().toLowerCase() : "";
        JsonObject user = t.has("user") && t.get("user").isJsonObject() ? t.getAsJsonObject("user") : null;
        String username = user != null && user.has("username") && !user.get("username").isJsonNull()
                ? user.get("username").getAsString().toLowerCase() : "";

        int score = 0;

        // 1. Strictly penalize unwanted remixes, covers, sped-up, mixes, flips if user didn't ask
        if (!wantsRemix) {
            if (title.contains("remix") || title.contains("cover") || title.contains("sped up")
                    || title.contains("speed up") || title.contains("slowed") || title.contains("reverb")
                    || title.contains("nightcore") || title.contains("karaoke") || title.contains("instrumental")
                    || title.contains("mix") || title.contains("flip") || title.contains("mashup")
                    || title.contains("bass boost") || title.contains("bootleg") || title.contains("edit")
                    || title.contains("tribute") || title.contains("orchestral") || title.contains("acoustic")
                    || title.contains("live") || title.contains("parody")) {
                score -= 130;
            }
        }

        // 2. Penalize raw NCS / instrumental tags if user didn't specifically search for NCS
        if (!queryLower.contains("ncs") && (title.contains("[ncs") || title.contains("ncs release"))) {
            score -= 40;
        }

        // 3. For Spectre / Alan Walker: Strongly prefer the vocal hit "The Spectre"
        if (queryLower.contains("spectre") && title.contains("the spectre")) {
            score += 50;
        }

        // 4. Target duration matching: crucial for unedited studio master and perfect synced lyrics
        if (targetDurationMs > 30000) {
            long candDur = t.has("duration") && !t.get("duration").isJsonNull() ? t.get("duration").getAsLong() : 0;
            if (candDur > 0) {
                long diff = Math.abs(candDur - targetDurationMs);
                if (diff <= 3500) {
                    score += 130; // Perfect master match!
                } else if (diff <= 7500) {
                    score += 65;
                } else if (diff > 16000) {
                    score -= 50; // Noticeably altered length
                }
            }
        }

        // 5. Boost exact or high relevance keyword matches
        if (title.contains(queryLower)) {
            score += 30;
        }

        // 6. Prefer official/original markers
        if (title.contains("original") || title.contains("official")) {
            score += 25;
        }

        // 7. Popularity weight (authentic hit uploads have massive play counts)
        long plays = t.has("playback_count") && !t.get("playback_count").isJsonNull() ? t.get("playback_count").getAsLong() : 0;
        if (plays > 1000000) {
            score += 25;
        } else if (plays > 100000) {
            score += 15;
        } else if (plays > 10000) {
            score += 5;
        }

        // 8. Normal song duration (between 2:00 and 5:30)
        long durSec = t.has("duration") && !t.get("duration").isJsonNull() ? t.get("duration").getAsLong() / 1000 : 0;
        if (durSec >= 120 && durSec <= 330) {
            score += 10;
        } else if (durSec > 0 && durSec < 60) {
            score -= 80; // Short preview / snippet
        }

        return score;
    }
}
