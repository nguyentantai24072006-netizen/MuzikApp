package com.example.muzzicapp.model;

import java.util.List;

public class ZingModels {

    public static class ZingResponse<T> {
        public int err;
        public String msg;
        public T data;
        public long timestamp;
        }

    public static class ZingSearchData {
        public List<ZingSong> items;
    }

    public static class ZingStreamingData {
        // ZingMP3 streaming response often has "128", "320" as keys to URLs
        public String _128; // Usually returned as "128" in JSON. We might need a custom deserializer or
                            // just use generic Map/JsonElement for streaming
    }

    public static class ZingSong {
        public String encodeId;
        public String title;
        public String alias;
        public String artistsNames;
        public String thumbnailM;
        public boolean isOffical;
        public boolean hasLyric;
    }
}
