package com.seminario.plugin.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.serialization.ConfigurationSerializable;

/**
 * Represents a single slide in a slideshow presentation
 * Contains URL, slide number, and map rendering information for 2x2 display
 */
public class Slide implements ConfigurationSerializable {
    
    private final String url;
    private final int slideNumber;
    private List<Integer> mapIds; // List of 4 map IDs for 2x2 display
    private String cachedImagePath;
    private long lastUpdated;
    
    public Slide(String url, int slideNumber) {
        this.url = url;
        this.slideNumber = slideNumber;
        this.mapIds = null;
        this.lastUpdated = System.currentTimeMillis();
    }
    
    public Slide(String url, int slideNumber, List<Integer> mapIds, String cachedImagePath, long lastUpdated) {
        this.url = url;
        this.slideNumber = slideNumber;
        this.mapIds = mapIds;
        this.cachedImagePath = cachedImagePath;
        this.lastUpdated = lastUpdated;
    }
    
    // Getters
    public String getUrl() {
        return url;
    }
    
    public int getSlideNumber() {
        return slideNumber;
    }
    
    public List<Integer> getMapIds() {
        return mapIds;
    }
    
    public String getCachedImagePath() {
        return cachedImagePath;
    }
    
    public long getLastUpdated() {
        return lastUpdated;
    }
    
    // Setters
    public void setMapIds(List<Integer> mapIds) {
        this.mapIds = mapIds;
        updateTimestamp();
    }
    
    public void setCachedImagePath(String cachedImagePath) {
        this.cachedImagePath = cachedImagePath;
        updateTimestamp();
    }
    
    public void updateTimestamp() {
        this.lastUpdated = System.currentTimeMillis();
    }
    
    public void clearMapIds() {
        this.mapIds = null;
        updateTimestamp();
    }
    
    // Utility methods
    public boolean hasMapIds() {
        return mapIds != null && mapIds.size() == 108; // Sistema 12x9 (108 mapas)
    }
    
    public boolean needsProcessing() {
        return !hasMapIds();
    }
    
    @Override
    public String toString() {
        return "Slide{" +
            "slideNumber=" + slideNumber +
            ", url='" + url + '\'' +
            ", hasMapIds=" + hasMapIds() +
            ", lastUpdated=" + lastUpdated +
            '}';
    }
    
    // ConfigurationSerializable implementation
    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> data = new HashMap<>();
        data.put("url", url);
        data.put("slideNumber", slideNumber);
        data.put("lastUpdated", lastUpdated);
        
        if (mapIds != null && !mapIds.isEmpty()) {
            data.put("mapIds", mapIds);
        }
        
        if (cachedImagePath != null) {
            data.put("cachedImagePath", cachedImagePath);
        }
        
        return data;
    }
    
    public static Slide deserialize(Map<String, Object> data) {
        String url = (String) data.get("url");
        int slideNumber = (Integer) data.get("slideNumber");
        long lastUpdated = data.containsKey("lastUpdated") ? 
            ((Number) data.get("lastUpdated")).longValue() : System.currentTimeMillis();
        
        @SuppressWarnings("unchecked")
        List<Integer> mapIds = (List<Integer>) data.get("mapIds");
        String cachedImagePath = (String) data.get("cachedImagePath");
        
        return new Slide(url, slideNumber, mapIds, cachedImagePath, lastUpdated);
    }
}