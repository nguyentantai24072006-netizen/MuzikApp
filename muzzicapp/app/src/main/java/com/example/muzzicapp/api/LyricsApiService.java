package com.example.muzzicapp.api;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LyricsApiService {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface LyricsCallback {
        void onSuccess(LyricsResult result);
        void onError(Exception e);
    }

    public static class LyricsResult {
        public final String plainLyrics;
        public final String syncedLyrics;
        public final boolean isInstrumental;

        public LyricsResult(String plainLyrics, String syncedLyrics) {
            this(plainLyrics, syncedLyrics, false);
        }

        public LyricsResult(String plainLyrics, String syncedLyrics, boolean isInstrumental) {
            this.plainLyrics = plainLyrics == null ? "" : plainLyrics;
            this.syncedLyrics = syncedLyrics == null ? "" : syncedLyrics;
            this.isInstrumental = isInstrumental;
        }
    }

    public void fetchLyrics(com.example.muzzicapp.model.Track track, LyricsCallback callback) {
        if (track == null) {
            callback.onError(new Exception("Track null"));
            return;
        }
        executor.execute(() -> {
            try {
                LyricsResult result = fetchLyricsForTrackInternal(track);
                if (result != null) {
                    mainHandler.post(() -> callback.onSuccess(result));
                } else {
                    mainHandler.post(() -> callback.onError(new Exception("Không tìm thấy lời bài hát")));
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public void fetchLyrics(String artist, String title, LyricsCallback callback) {
        fetchLyrics(artist, title, 0, callback);
    }

    public void fetchLyrics(String artist, String title, long targetDurationMs, LyricsCallback callback) {
        executor.execute(() -> {
            try {
                LyricsResult result = fetchLyricsInternal(artist, title, targetDurationMs);
                if (result != null) {
                    mainHandler.post(() -> callback.onSuccess(result));
                } else {
                    mainHandler.post(() -> callback.onError(new Exception("Không tìm thấy lời bài hát")));
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    private LyricsResult fetchLyricsForTrackInternal(com.example.muzzicapp.model.Track track) {
        // 1. Direct lyricUrl from track (e.g. from Zing suggestions)
        if (track.getLyricUrl() != null && !track.getLyricUrl().trim().isEmpty()) {
            try {
                String lrc = httpGet(track.getLyricUrl().trim());
                if (lrc != null && lrc.contains("[") && lrc.contains("]")) {
                    return new LyricsResult("", lrc, false);
                }
            } catch (Exception ignored) {}
        }

        // 2. Fetch via ZingId
        if (track.getZingId() != null && !track.getZingId().trim().isEmpty()) {
            LyricsResult zingRes = fetchZingLyricsById(track.getZingId().trim());
            if (zingRes != null && !zingRes.syncedLyrics.trim().isEmpty()) {
                return zingRes;
            }
        }

        // 3. Fallback to searching Zing by title & artist with multi-query strategies & track enrichment
        String cleanTitle = cleanTitle(track.getTitle());
        String cleanArtist = cleanArtist(track.getArtist());
        LyricsResult zingSug = searchZingLyrics(cleanTitle, cleanArtist, track.getDurationMillis(), track);
        if (zingSug != null && !zingSug.syncedLyrics.trim().isEmpty()) {
            return zingSug;
        }

        // 4. Fallback to searching by title & artist on LRCLIB
        return fetchLyricsInternal(cleanArtist, cleanTitle, track.getDurationMillis(), false);
    }

    public static String cleanTitle(String title) {
        if (title == null) return "";
        return title.replaceAll("(?i)\\[(official|audio|mv|video|lyrics?|hd).*?\\]", "")
                .replaceAll("(?i)\\((official|audio|mv|video|lyrics?|hd).*?\\)", "")
                .replaceAll("\\(.*?\\)", "")
                .trim();
    }

    public static String cleanArtist(String artist) {
        if (artist == null) return "";
        String a = artist.trim();
        if (a.equalsIgnoreCase("Unknown Artist") || a.equalsIgnoreCase("Unknown")) return "";
        return a;
    }

    private LyricsResult fetchZingLyricsById(String zingId) {
        try {
            String endpoint = "https://api-zingmp3-vercel.vercel.app/api/lyric?id=" + URLEncoder.encode(zingId, "UTF-8");
            String jsonStr = httpGet(endpoint);
            if (jsonStr == null) return null;
            JSONObject json = new JSONObject(jsonStr);
            if (!json.has("data") || json.isNull("data")) return null;
            JSONObject data = json.getJSONObject("data");

            // Check direct .lrc file link
            if (data.has("file") && !data.isNull("file")) {
                String fileUrl = data.getString("file");
                if (fileUrl.startsWith("http://") || fileUrl.startsWith("https://")) {
                    String lrc = httpGet(fileUrl);
                    if (lrc != null && lrc.contains("[") && lrc.contains("]")) {
                        return new LyricsResult("", lrc, false);
                    }
                }
            }

            // Check sentences with word timestamps
            if (data.has("sentences") && !data.isNull("sentences")) {
                JSONArray sentences = data.getJSONArray("sentences");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < sentences.length(); i++) {
                    JSONObject sent = sentences.getJSONObject(i);
                    if (sent.has("words") && !sent.isNull("words")) {
                        JSONArray words = sent.getJSONArray("words");
                        if (words.length() > 0) {
                            long startTime = words.getJSONObject(0).optLong("startTime", 0);
                            StringBuilder lineText = new StringBuilder();
                            for (int w = 0; w < words.length(); w++) {
                                lineText.append(words.getJSONObject(w).optString("data", "")).append(" ");
                            }
                            long min = startTime / 60000;
                            long sec = (startTime % 60000) / 1000;
                            long ms = (startTime % 1000) / 10;
                            sb.append(String.format(java.util.Locale.US, "[%02d:%02d.%02d]%s\n", min, sec, ms, lineText.toString().trim()));
                        }
                    }
                }
                if (sb.length() > 0) {
                    return new LyricsResult("", sb.toString(), false);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private LyricsResult searchZingLyrics(String cleanTitle, String cleanArtist, long targetDurationMs, com.example.muzzicapp.model.Track track) {
        if (cleanTitle == null || cleanTitle.trim().isEmpty()) return null;

        java.util.List<String> candidateQueries = new java.util.ArrayList<>();
        String normTitle = cleanTitle.trim();
        String normArtist = cleanArtist != null ? cleanArtist.trim() : "";

        // Query 1: Title + Artist
        if (!normArtist.isEmpty()) {
            candidateQueries.add((normTitle + " " + normArtist).trim());
        }
        // Query 2: Title only (Zing autocomplete works most reliably with title alone)
        candidateQueries.add(normTitle);

        // Query 3: Title + first 2 words of artist (e.g. "Son Tung" instead of full name)
        if (!normArtist.isEmpty()) {
            String[] artistParts = normArtist.split("\\s+");
            if (artistParts.length >= 2) {
                String shortArtist = artistParts[0] + " " + artistParts[1];
                candidateQueries.add((normTitle + " " + shortArtist).trim());
                candidateQueries.add((shortArtist + " " + normTitle).trim());
            } else if (artistParts.length == 1) {
                candidateQueries.add((artistParts[0] + " " + normTitle).trim());
            }
        }

        // Only enforce duration difference if targetSec is a realistic full song (> 45s)
        // If targetSec <= 45s (e.g. 30s preview), do not reject 3-4 minute studio songs
        double targetSec = (targetDurationMs > 45000) ? (targetDurationMs / 1000.0) : 0;
        LyricsResult bestResult = null;
        double bestDiff = Double.MAX_VALUE;

        for (String q : candidateQueries) {
            try {
                String url = "https://ac.zingmp3.vn/v1/web/ac-suggestions?query=" + URLEncoder.encode(q, "UTF-8");
                String jsonStr = httpGet(url);
                if (jsonStr == null) continue;
                JSONObject json = new JSONObject(jsonStr);
                if (!json.has("data") || json.isNull("data")) continue;
                JSONObject data = json.getJSONObject("data");
                if (!data.has("items") || data.isNull("items")) continue;
                JSONArray items = data.getJSONArray("items");

                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    if (item.has("suggestions") && !item.isNull("suggestions")) {
                        JSONArray sugs = item.getJSONArray("suggestions");
                        for (int s = 0; s < sugs.length(); s++) {
                            JSONObject sug = sugs.getJSONObject(s);
                            if (sug.optInt("type", 0) == 1) { // Type 1: Song
                                String sugTitle = sug.optString("title", "");
                                if (!isTitleMatching(sugTitle, cleanTitle)) {
                                    continue; // Reject fuzzy/wrong songs
                                }

                                // Verify artist matching if artist was provided
                                if (!normArtist.isEmpty()) {
                                    String sugArtist = "";
                                    if (sug.has("artists") && !sug.isNull("artists")) {
                                        JSONArray artistsArr = sug.getJSONArray("artists");
                                        StringBuilder sb = new StringBuilder();
                                        for (int a = 0; a < artistsArr.length(); a++) {
                                            JSONObject aObj = artistsArr.getJSONObject(a);
                                            if (aObj.has("name")) {
                                                if (sb.length() > 0) sb.append(", ");
                                                sb.append(aObj.optString("name", ""));
                                            }
                                        }
                                        sugArtist = sb.toString();
                                    }
                                    if (!sugArtist.isEmpty() && !MusicApiService.isArtistMatch(sugArtist, normArtist)) {
                                        continue; // Reject cover by another artist
                                    }
                                }

                                // Reject Remix / Short / Cover / Speed Up if original song doesn't want them
                                String lowerSug = sugTitle.toLowerCase(java.util.Locale.ROOT);
                                String lowerTarget = cleanTitle.toLowerCase(java.util.Locale.ROOT);
                                if (!lowerTarget.contains("remix") && lowerSug.contains("remix")) continue;
                                if (!lowerTarget.contains("short") && lowerSug.contains("short")) continue;
                                if (!lowerTarget.contains("speed up") && lowerSug.contains("speed up")) continue;
                                if (!lowerTarget.contains("cover") && lowerSug.contains("cover")) continue;
                                if (!lowerTarget.contains("karaoke") && lowerSug.contains("karaoke")) continue;
                                if (!lowerTarget.contains("piano") && lowerSug.contains("piano")) continue;

                                long dur = sug.optLong("duration", 0);
                                double diff = targetSec > 0 && dur > 0 ? Math.abs(dur - targetSec) : 0;
                                if (targetSec > 0 && dur > 0 && diff > 15.0) {
                                    continue; // Reject wrong versions with huge duration discrepancy
                                }

                                String zingId = sug.optString("id", null);
                                String lyricLink = sug.optString("lyricLink", null);

                                if (lyricLink != null && lyricLink.startsWith("http")) {
                                    String lrc = httpGet(lyricLink);
                                    if (lrc != null && lrc.contains("[") && lrc.contains("]")) {
                                        LyricsResult r = new LyricsResult("", lrc, false);
                                        if (track != null) {
                                            if (zingId != null && !zingId.isEmpty()) track.setZingId(zingId);
                                            track.setLyricUrl(lyricLink);
                                        }
                                        if (targetSec <= 0 || diff <= 3.0) return r;
                                        if (diff < bestDiff) {
                                            bestDiff = diff;
                                            bestResult = r;
                                        }
                                    }
                                }
                                if (zingId != null && !zingId.isEmpty()) {
                                    LyricsResult res = fetchZingLyricsById(zingId);
                                    if (res != null && !res.syncedLyrics.trim().isEmpty()) {
                                        if (track != null) {
                                            track.setZingId(zingId);
                                            if (lyricLink != null && lyricLink.startsWith("http")) {
                                                track.setLyricUrl(lyricLink);
                                            }
                                        }
                                        if (targetSec <= 0 || diff <= 3.0) return res;
                                        if (diff < bestDiff) {
                                            bestDiff = diff;
                                            bestResult = res;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (bestResult != null) return bestResult;
            } catch (Exception ignored) {}
        }
        return bestResult;
    }

    private LyricsResult fetchLyricsInternal(String artist, String title, long targetDurationMs) {
        return fetchLyricsInternal(artist, title, targetDurationMs, true);
    }

    private LyricsResult fetchLyricsInternal(String artist, String title, long targetDurationMs, boolean includeZingSearch) {
        String cleanTitle = cleanTitle(title);
        String cleanArtist = cleanArtist(artist);

        // 1. Prioritize Zing suggestions + Zing lyric API for SYNCED lyrics (official publisher master lyrics)
        if (includeZingSearch) {
            LyricsResult zingSug = searchZingLyrics(cleanTitle, cleanArtist, targetDurationMs, null);
            if (zingSug != null && !zingSug.syncedLyrics.trim().isEmpty()) {
                return zingSug;
            }
        }

        // 2. Fallback to LRCLIB search for SYNCED lyrics (international songs or non-Zing tracks)
        String searchQuery = (cleanTitle + " " + cleanArtist).trim();
        LyricsResult lrclibSynced = trySearchSynced(searchQuery, targetDurationMs, cleanTitle);
        if (lrclibSynced != null) {
            return lrclibSynced;
        }

        // 3. Try LRCLIB exact get endpoint for SYNCED lyrics
        LyricsResult exact = tryGetEndpoint(cleanArtist, cleanTitle);
        if (exact != null && !exact.syncedLyrics.trim().isEmpty()) {
            return exact;
        }

        // 4. Try LRCLIB search query with cleanTitle only for SYNCED lyrics
        if (!cleanTitle.isEmpty() && !cleanTitle.equalsIgnoreCase(searchQuery)) {
            LyricsResult titleSynced = trySearchSynced(cleanTitle, targetDurationMs, cleanTitle);
            if (titleSynced != null) {
                return titleSynced;
            }
        }

        // 5. Fallback to PLAIN lyrics if no synced lyrics could be found anywhere
        if (exact != null && !exact.plainLyrics.trim().isEmpty()) {
            return exact;
        }
        LyricsResult plainFallback = trySearchPlain(searchQuery);
        if (plainFallback != null) {
            return plainFallback;
        }

        // 6. Instrumental candidate
        if (exact != null && exact.isInstrumental) {
            return exact;
        }

        return null;
    }

    private LyricsResult tryGetEndpoint(String artist, String title) {
        try {
            String urlStr = "https://lrclib.net/api/get?artist_name=" + URLEncoder.encode(artist, "UTF-8")
                    + "&track_name=" + URLEncoder.encode(title, "UTF-8");
            String jsonStr = httpGet(urlStr);
            if (jsonStr == null) return null;

            JSONObject obj = new JSONObject(jsonStr);
            return parseObject(obj);
        } catch (Exception ignored) {
            return null;
        }
    }

    private LyricsResult trySearchSynced(String query, long targetDurationMs, String cleanTitle) {
        try {
            String urlStr = "https://lrclib.net/api/search?q=" + URLEncoder.encode(query, "UTF-8");
            String jsonStr = httpGet(urlStr);
            if (jsonStr == null) return null;

            JSONArray arr = new JSONArray(jsonStr);
            LyricsResult bestResult = null;
            double bestDiff = Double.MAX_VALUE;
            double targetSec = targetDurationMs > 45000 ? (targetDurationMs / 1000.0) : 0;

            for (int i = 0; i < Math.min(arr.length(), 20); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String trackName = obj.optString("trackName", "");
                if (!cleanTitle.isEmpty() && !isTitleMatching(trackName, cleanTitle)) {
                    continue; // Skip mismatched songs
                }
                LyricsResult r = parseObject(obj);
                if (r != null && !r.syncedLyrics.trim().isEmpty()) {
                    double candSec = obj.optDouble("duration", 0);
                    if (targetSec > 0 && candSec > 0) {
                        double diff = Math.abs(candSec - targetSec);
                        if (diff < bestDiff) {
                            bestDiff = diff;
                            bestResult = r;
                            // Exact duration match within ±2.5 seconds (exact studio/MV track)
                            if (diff <= 2.5) {
                                return r;
                            }
                        }
                    } else if (bestResult == null) {
                        bestResult = r;
                    }
                }
            }
            if (bestResult != null) return bestResult;
        } catch (Exception ignored) {}
        return null;
    }

    public static boolean isTitleMatching(String candidate, String target) {
        if (candidate == null || target == null) return false;
        String c = normalizeForComparison(candidate);
        String t = normalizeForComparison(target);
        if (c.isEmpty() || t.isEmpty()) return false;
        if (c.equals(t)) return true;
        if (c.contains(t) || t.contains(c)) {
            String[] cw = c.split(" ");
            String[] tw = t.split(" ");
            int matches = 0;
            for (String w : cw) {
                for (String twItem : tw) {
                    if (w.equals(twItem)) {
                        matches++;
                        break;
                    }
                }
            }
            int minLen = Math.min(cw.length, tw.length);
            return matches >= Math.max(1, (int) Math.ceil(minLen * 0.75));
        }
        return false;
    }

    private static String normalizeForComparison(String str) {
        if (str == null) return "";
        String s = java.text.Normalizer.normalize(str, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(java.util.Locale.ROOT);
        return s.replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
    }

    private LyricsResult trySearchPlain(String query) {
        try {
            String urlStr = "https://lrclib.net/api/search?q=" + URLEncoder.encode(query, "UTF-8");
            String jsonStr = httpGet(urlStr);
            if (jsonStr == null) return null;

            JSONArray arr = new JSONArray(jsonStr);
            LyricsResult instrumentalCandidate = null;
            for (int i = 0; i < Math.min(arr.length(), 6); i++) {
                JSONObject obj = arr.getJSONObject(i);
                LyricsResult r = parseObject(obj);
                if (r != null) {
                    if (!r.isInstrumental && !r.plainLyrics.trim().isEmpty()) {
                        return r;
                    } else if (r.isInstrumental && instrumentalCandidate == null) {
                        instrumentalCandidate = r;
                    }
                }
            }
            return instrumentalCandidate;
        } catch (Exception ignored) {}
        return null;
    }

    private LyricsResult parseObject(JSONObject obj) {
        if (obj == null) return null;
        boolean isInstrumental = obj.optBoolean("instrumental", false);
        String plain = cleanString(obj, "plainLyrics");
        String synced = cleanString(obj, "syncedLyrics");

        if (isInstrumental && plain.isEmpty() && synced.isEmpty()) {
            return new LyricsResult("Bài hát không lời (Instrumental)", "", true);
        }

        if (!plain.isEmpty() || !synced.isEmpty()) {
            return new LyricsResult(plain, synced, isInstrumental);
        }
        return null;
    }

    private String cleanString(JSONObject obj, String key) {
        if (obj.isNull(key)) return "";
        String val = obj.optString(key, "").trim();
        if ("null".equalsIgnoreCase(val)) return "";
        return val;
    }

    private String httpGet(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile");
            conn.setRequestProperty("Accept-Encoding", "gzip, deflate");

            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_OK) {
                java.io.InputStream in = conn.getInputStream();
                java.io.PushbackInputStream pb = new java.io.PushbackInputStream(in, 2);
                byte[] header = new byte[2];
                int read = pb.read(header);
                if (read == 2 && ((header[0] & 0xff) == 0x1f) && ((header[1] & 0xff) == 0x8b)) {
                    pb.unread(header);
                    in = new java.util.zip.GZIPInputStream(pb);
                } else if (read > 0) {
                    pb.unread(header, 0, read);
                    in = pb;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                reader.close();
                return sb.toString();
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }
}
