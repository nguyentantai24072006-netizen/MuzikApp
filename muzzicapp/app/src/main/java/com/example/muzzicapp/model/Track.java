package com.example.muzzicapp.model;

import com.google.gson.annotations.SerializedName;

public class Track {
    @SerializedName(value = "trackId", alternate = {"id"})
    private long id;
    
    @SerializedName(value = "trackName", alternate = {"title"})
    private String title;
    
    @SerializedName(value = "artistName", alternate = {"artist"})
    private String artist;
    
    @SerializedName(value = "artworkUrl100", alternate = {"artworkUrl"})
    private String artworkUrl;
    
    @SerializedName("previewUrl")
    private String previewUrl;

    @SerializedName("zingId")
    private String zingId;

    // Set by a licensed audio backend. iTunes previewUrl is only about 30 seconds.
    @SerializedName("fullAudioUrl")
    private String fullAudioUrl;

    @SerializedName("trackViewUrl")
    private String trackViewUrl;

    @SerializedName(value = "trackTimeMillis", alternate = {"durationMillis"})
    private long durationMillis;
    
    @SerializedName("lyricUrl")
    private String lyricUrl;

    // Getters
    public long getId() { return id; }
    public String getTitle() { return title != null ? title : ""; }
    public String getArtist() { return artist != null ? artist : ""; }
    public String getArtworkUrl() { return artworkUrl; }
    public String getPreviewUrl() { return previewUrl; }
    public String getZingId() { return zingId; }
    public String getLyricUrl() { return lyricUrl; }
    public String getFullAudioUrl() { return fullAudioUrl; }
    public String getTrackViewUrl() { return trackViewUrl; }
    public long getDurationMillis() { return durationMillis; }

    public String getTrackKey() {
        if (zingId != null && !zingId.isEmpty()) return "zing_" + zingId;
        if (id != 0) return "id_" + id;
        String t = title != null ? title.toLowerCase().trim() : "";
        String a = artist != null ? artist.toLowerCase().trim() : "";
        if (!t.isEmpty() || !a.isEmpty()) {
            return t + "_" + a;
        }
        return "track_" + System.identityHashCode(this);
    }

    public String getPlaybackUrl() {
        return fullAudioUrl != null && !fullAudioUrl.trim().isEmpty() ? fullAudioUrl : null;
    }
    
    public boolean isPreviewOnly() {
        return zingId == null && (fullAudioUrl == null || fullAudioUrl.trim().isEmpty());
    }

    // Setters (if needed)
    public void setId(long id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setArtist(String artist) { this.artist = artist; }
    public void setArtworkUrl(String artworkUrl) { this.artworkUrl = artworkUrl; }
    public void setPreviewUrl(String previewUrl) { this.previewUrl = previewUrl; }
    public void setFullAudioUrl(String fullAudioUrl) { this.fullAudioUrl = fullAudioUrl; }
    public void setZingId(String zingId) { this.zingId = zingId; }
    public void setLyricUrl(String lyricUrl) { this.lyricUrl = lyricUrl; }
    public void setDurationMillis(long durationMillis) { this.durationMillis = durationMillis; }
    public void setTrackViewUrl(String trackViewUrl) { this.trackViewUrl = trackViewUrl; }
    
    // Multi-source High-Res Artwork URL resolver:
    // Transforms low-res thumbnails from Zing MP3, iTunes, Deezer, YouTube, Jamendo, etc. into crisp HD artwork.
    public String getHighResArtworkUrl() {
        if (artworkUrl == null || artworkUrl.trim().isEmpty()) {
            return null;
        }
        String url = artworkUrl.trim();

        // 1. Zing MP3 (covers, cover, avatars on zmdcdn.me / zadn.vn / zingmp3.vn):
        // Upgrades low-res thumbnails (w94, w165, w240, w320, w360, w480) to crystal-clear 600x600 HD artwork
        if (url.contains("zmdcdn.me") || url.contains("zadn.vn") || url.contains("zmp3") || url.matches(".*w\\d+_r1x1_\\w+.*")) {
            url = url.replaceAll("w\\d+_r1x1_\\w+", "w600_r1x1_jpeg");
        }
        // 2. iTunes / Apple Music (e.g. 100x100bb.jpg -> 600x600bb.jpg)
        else if (url.contains("mzstatic.com") || url.contains("apple.com") || url.contains("100x100bb")) {
            url = url.replaceAll("\\d+x\\d+bb\\.", "600x600bb.");
        }
        // 3. Deezer (e.g. 250x250-000000-80-0-0.jpg -> 1000x1000-000000-80-0-0.jpg)
        else if (url.contains("dzcdn.net")) {
            url = url.replaceAll("\\d+x\\d+-000000", "1000x1000-000000");
        }
        // 4. Jamendo (e.g. width=300 -> width=600)
        else if (url.contains("jamendo.com") && url.contains("width=")) {
            url = url.replaceAll("width=\\d+", "width=600");
        }
        // 5. YouTube (e.g. default.jpg, mqdefault.jpg -> hqdefault.jpg)
        else if (url.contains("ytimg.com") || url.contains("youtube.com")) {
            url = url.replace("/default.jpg", "/hqdefault.jpg")
                     .replace("/mqdefault.jpg", "/hqdefault.jpg");
        }

        return url;
    }
}
