package com.seminario.plugin.model;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
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
        data.put("checkpointLocation", serializeLocation(checkpointLocation));
        data.put("entryLocation", serializeLocation(entryLocation));
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
        level.levelNumber = asInt(data.get("levelNumber"), 0);

        String difficultyName = String.valueOf(data.getOrDefault("difficulty", SQLDifficulty.BASIC.name()));
        try {
            level.difficulty = SQLDifficulty.valueOf(difficultyName);
        } catch (IllegalArgumentException ex) {
            level.difficulty = SQLDifficulty.BASIC;
        }

        level.checkpointLocation = deserializeLocation(data.get("checkpointLocation"));
        level.entryLocation = deserializeLocation(data.get("entryLocation"));
        level.challenge = (String) data.get("challenge");
        level.expectedQuery = (String) data.get("expectedQuery");
        level.hint1 = (String) data.get("hint1");
        level.hint2 = (String) data.get("hint2");
        level.hint3 = (String) data.get("hint3");
        level.hasEntry = (Boolean) data.getOrDefault("hasEntry", level.entryLocation != null);
        return level;
    }

    private static Map<String, Object> serializeLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        Map<String, Object> map = new HashMap<>();
        map.put("world", location.getWorld().getName());
        map.put("x", location.getX());
        map.put("y", location.getY());
        map.put("z", location.getZ());
        map.put("yaw", location.getYaw());
        map.put("pitch", location.getPitch());
        return map;
    }

    private static Location deserializeLocation(Object obj) {
        if (obj == null) {
            return null;
        }

        if (obj instanceof Location) {
            return ((Location) obj).clone();
        }

        if (!(obj instanceof Map<?, ?>)) {
            return null;
        }

        Map<?, ?> map = (Map<?, ?>) obj;
        String worldName = String.valueOf(map.get("world"));
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }

        double x = asDouble(map.get("x"), 0.0D);
        double y = asDouble(map.get("y"), 0.0D);
        double z = asDouble(map.get("z"), 0.0D);
        float yaw = (float) asDouble(map.get("yaw"), 0.0D);
        float pitch = (float) asDouble(map.get("pitch"), 0.0D);

        return new Location(world, x, y, z, yaw, pitch);
    }

    private static int asInt(Object value, int defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static double asDouble(Object value, double defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    @Override
    public String toString() {
        return String.format("SQLLevel{level=%d, difficulty=%s, complete=%s}", 
                           levelNumber, difficulty.getDisplayName(), isComplete());
    }
}