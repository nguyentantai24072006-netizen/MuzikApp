package com.example.muzzicapp.api;

import android.os.Handler;
import android.os.Looper;

import com.example.muzzicapp.model.Track;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class YouTubeApiService {

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();

    // Fallback Piped API instances
    private static final String[] PIPED_INSTANCES = {
            "https://pipedapi.kavin.rocks",
            "https://api.piped.stream",
            "https://pipedapi.moomoo.me",
            "https://pipedapi.smnz.de"
    };

    public interface YouTubeCallback {
        void onSuccess(List<Track> tracks);
        void onError(Exception e);
    }

    public void searchTracks(String query, YouTubeCallback callback) {
        executor.execute(() -> {
            Exception lastException = null;
            
            for (String instance : PIPED_INSTANCES) {
                try {
                    String encodedQuery = URLEncoder.encode(query, "UTF-8");
                    String urlString = instance + "/search?q=" + encodedQuery + "&filter=music_songs";
                    
                    URL url = new URL(urlString);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(5000);
                    connection.setRequestProperty("Accept", "application/json");

                    int responseCode = connection.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
                        JsonObject response = gson.fromJson(reader, JsonObject.class);
                        reader.close();
                        
                        List<Track> tracks = new ArrayList<>();
                        if (response != null && response.has("items")) {
                            JsonArray items = response.getAsJsonArray("items");
                            for (JsonElement element : items) {
                                JsonObject item = element.getAsJsonObject();
                                if (item.has("type") && item.get("type").getAsString().equals("stream")) {
                                    Track track = new Track();
                                    track.setTitle(item.has("title") ? item.get("title").getAsString() : "Unknown Title");
                                    track.setArtist(item.has("uploaderName") ? item.get("uploaderName").getAsString() : "YouTube");
                                    
                                    if (item.has("thumbnail")) {
                                        track.setArtworkUrl(item.get("thumbnail").getAsString());
                                    }
                                    
                                    if (item.has("duration")) {
                                        track.setDurationMillis(item.get("duration").getAsLong() * 1000);
                                    }
                                    
                                    // Extract Video ID
                                    String videoUrl = item.get("url").getAsString();
                                    String videoId = videoUrl.replace("/watch?v=", "");
                                    
                                    // We will use FullAudioResolver or our own method to get stream from videoId
                                    // But since FullAudioResolver doesn't know about Piped, we can set the fullUrl as a special format
                                    track.setFullAudioUrl("piped://" + videoId);
                                    tracks.add(track);
                                }
                            }
                        }
                        
                        if (!tracks.isEmpty()) {
                            mainHandler.post(() -> callback.onSuccess(tracks));
                            return; // Stop trying other instances
                        }
                    }
                } catch (Exception e) {
                    lastException = e;
                }
            }
            
            // If all failed
            final Exception finalException = lastException != null ? lastException : new Exception("Không tìm thấy kết quả");
            mainHandler.post(() -> callback.onError(finalException));
        });
    }
}
