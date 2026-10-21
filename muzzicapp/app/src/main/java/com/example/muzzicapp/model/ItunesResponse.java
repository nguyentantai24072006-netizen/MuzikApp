package com.example.muzzicapp.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class ItunesResponse {
    @SerializedName("resultCount")
    private int resultCount;
    
    @SerializedName("results")
    private List<Track> results;
    
    public int getResultCount() { return resultCount; }
    public List<Track> getResults() { return results; }
}
