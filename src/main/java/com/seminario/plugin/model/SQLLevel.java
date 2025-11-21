package com.seminario.plugin.model;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.configuration.serialization.ConfigurationSerializable;

/**
 * Represents a SQL level in a dungeon with position, difficulty, and challenge data
 */
public class SQLLevel implements ConfigurationSerializable {
    
    private int levelNumber;
    private SQLDifficulty difficulty;
    private Location checkpointLocation;
    private Location entryLocation;
    private String challenge;
    private String expectedQuery;
    private String hint1;
    private String hint2;
    private String hint3;
    private boolean hasEntry;
    
    public SQLLevel() {
        // Default constructor for YAML deserialization
    }
    
    public SQLLevel(int levelNumber, SQLDifficulty difficulty, Location checkpointLocation) {
        this.levelNumber = levelNumber;
        this.difficulty = difficulty;
        this.checkpointLocation = checkpointLocation;
        this.hasEntry = false;
    }
    
    // Getters and Setters
    public int getLevelNumber() {
        return levelNumber;
    }
    
    public void setLevelNumber(int levelNumber) {
        this.levelNumber = levelNumber;
    }
    
    public SQLDifficulty getDifficulty() {
        return difficulty;
    }
    
    public void setDifficulty(SQLDifficulty difficulty) {
        this.difficulty = difficulty;
    }
    
    public Location getCheckpointLocation() {
        return checkpointLocation;
    }
    
    public void setCheckpointLocation(Location checkpointLocation) {
        this.checkpointLocation = checkpointLocation;
    }
    
    public Location getEntryLocation() {
        return entryLocation;
    }
    
    public void setEntryLocation(Location entryLocation) {
        this.entryLocation = entryLocation;
        this.hasEntry = (entryLocation != null);
    }
    
    public boolean hasEntry() {
        return hasEntry && entryLocation != null;
    }
    
    public String getChallenge() {
        return challenge;
    }
    
    public void setChallenge(String challenge) {
        this.challenge = challenge;
    }
    
    public String getExpectedQuery() {
        return expectedQuery;
    }
    
    public void setExpectedQuery(String expectedQuery) {
        this.expectedQuery = expectedQuery;
    }
    
    public String getHint1() {
        return hint1;
    }
    
    public void setHint1(String hint1) {
        this.hint1 = hint1;
    }
    
    public String getHint2() {
        return hint2;
    }
    
    public void setHint2(String hint2) {
        this.hint2 = hint2;
    }
    
    public String getHint3() {
        return hint3;
    }
    
    public void setHint3(String hint3) {
        this.hint3 = hint3;
    }
    
    /**
     * Get available hints based on difficulty
     * @return Array of available hints (null entries for unavailable hints)
     */
    public String[] getAvailableHints() {
        int hintsCount = difficulty.getHintsAvailable();
        String[] hints = new String[3];
        
        if (hintsCount >= 1) hints[0] = hint1;
        if (hintsCount >= 2) hints[1] = hint2;
        if (hintsCount >= 3) hints[2] = hint3;
        
        return hints;
    }
    
    /**
     * Check if the level is fully configured
     * @return true if level has all required data
     */
    public boolean isComplete() {
        return challenge != null && !challenge.isEmpty() &&
               expectedQuery != null && !expectedQuery.isEmpty() &&
               hasEntry();
    }
    
    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> data = new HashMap<>();
        data.put("levelNumber", levelNumber);
        data.put("difficulty", difficulty.name());
        data.put("checkpointLocation", checkpointLocation);
        data.put("entryLocation", entryLocation);
        data.put("challenge", challenge);
        data.put("expectedQuery", expectedQuery);
        data.put("hint1", hint1);
        data.put("hint2", hint2);
        data.put("hint3", hint3);
        data.put("hasEntry", hasEntry);
        return data;
    }
    
    @SuppressWarnings("unchecked")
    public static SQLLevel deserialize(Map<String, Object> data) {
        SQLLevel level = new SQLLevel();
        level.levelNumber = (Integer) data.get("levelNumber");
        level.difficulty = SQLDifficulty.valueOf((String) data.get("difficulty"));
        level.checkpointLocation = (Location) data.get("checkpointLocation");
        level.entryLocation = (Location) data.get("entryLocation");
        level.challenge = (String) data.get("challenge");
        level.expectedQuery = (String) data.get("expectedQuery");
        level.hint1 = (String) data.get("hint1");
        level.hint2 = (String) data.get("hint2");
        level.hint3 = (String) data.get("hint3");
        level.hasEntry = (Boolean) data.getOrDefault("hasEntry", false);
        return level;
    }
    
    @Override
    public String toString() {
        return String.format("SQLLevel{level=%d, difficulty=%s, complete=%s}", 
                           levelNumber, difficulty.getDisplayName(), isComplete());
    }
}