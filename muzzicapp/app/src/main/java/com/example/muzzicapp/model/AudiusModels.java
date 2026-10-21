package com.example.muzzicapp.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class AudiusModels {

    public static class AudiusResponse {
        @SerializedName("data")
        public List<AudiusTrack> data;
    }

    public static class AudiusTrack {
        @SerializedName("id")
        public String id;

        @SerializedName("title")
        public String title;

        @SerializedName("duration")
        public long duration;

        @SerializedName("user")
        public AudiusUser user;
        
        @SerializedName("artwork")
        public AudiusArtwork artwork;
    }

    public static class AudiusUser {
        @SerializedName("name")
        public String name;
    }
    
    public static class AudiusArtwork {
        @SerializedName("480x480")
        public String artwork480;
        
        @SerializedName("1000x1000")
        public String artwork1000;
        
        @SerializedName("150x150")
        public String artwork150;
    }
}
