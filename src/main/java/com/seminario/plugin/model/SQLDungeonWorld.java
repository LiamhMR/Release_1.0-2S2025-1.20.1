package com.seminario.plugin.model;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.bukkit.configuration.serialization.ConfigurationSerializable;

/**
 * Represents a SQL Dungeon world with levels and configuration
 */
public class SQLDungeonWorld implements ConfigurationSerializable {
    
    private String worldName;
    private Map<Integer, SQLLevel> levels;
    private boolean isActive;
    private long createdTimestamp;
    
    public SQLDungeonWorld() {
        // Default constructor for YAML deserialization
        this.levels = new TreeMap<>();
    }
    
    public SQLDungeonWorld(String worldName) {
        this.worldName = worldName;
        this.levels = new TreeMap<>();
        this.isActive = true;
        this.createdTimestamp = System.currentTimeMillis();
    }
    
    // Getters and Setters
    public String getWorldName() {
        return worldName;
    }
    
    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }
    
    public Map<Integer, SQLLevel> getLevels() {
        return levels;
    }
    
    public void setLevels(Map<Integer, SQLLevel> levels) {
        this.levels = levels != null ? levels : new TreeMap<>();
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    public void setActive(boolean active) {
        isActive = active;
    }
    
    public long getCreatedTimestamp() {
        return createdTimestamp;
    }
    
    public void setCreatedTimestamp(long createdTimestamp) {
        this.createdTimestamp = createdTimestamp;
    }
    
    // Level Management Methods
    
    /**
     * Add a new SQL level to the dungeon
     * @param level The SQL level to add
     * @return true if added successfully, false if level number already exists
     */
    public boolean addLevel(SQLLevel level) {
        if (levels.containsKey(level.getLevelNumber())) {
            return false;
        }
        levels.put(level.getLevelNumber(), level);
        return true;
    }
    
    /**
     * Remove a level from the dungeon
     * @param levelNumber The level number to remove
     * @return true if removed, false if level didn't exist
     */
    public boolean removeLevel(int levelNumber) {
        return levels.remove(levelNumber) != null;
    }
    
    /**
     * Get a specific level
     * @param levelNumber The level number
     * @return SQLLevel or null if not found
     */
    public SQLLevel getLevel(int levelNumber) {
        return levels.get(levelNumber);
    }
    
    /**
     * Check if a level exists
     * @param levelNumber The level number to check
     * @return true if level exists
     */
    public boolean hasLevel(int levelNumber) {
        return levels.containsKey(levelNumber);
    }
    
    /**
     * Get the total number of levels
     * @return Number of levels
     */
    public int getLevelCount() {
        return levels.size();
    }
    
    /**
     * Get the number of complete levels (fully configured)
     * @return Number of complete levels
     */
    public int getCompleteLevelCount() {
        return (int) levels.values().stream().filter(SQLLevel::isComplete).count();
    }
    
    /**
     * Get the next level number that should be played
     * @return Next level number or -1 if no complete levels
     */
    public int getNextLevelNumber() {
        return levels.keySet().stream()
                .filter(num -> levels.get(num).isComplete())
                .findFirst()
                .orElse(-1);
    }
    
    /**
     * Check if the dungeon is ready to be played
     * @return true if has at least one complete level
     */
    public boolean isPlayable() {
        return getCompleteLevelCount() > 0;
    }
    
    /**
     * Get a summary string of the dungeon status
     * @return Summary string
     */
    public String getSummary() {
        return String.format("SQLDungeon '%s': %d levels (%d complete), %s", 
                           worldName, 
                           getLevelCount(), 
                           getCompleteLevelCount(),
                           isActive ? "active" : "inactive");
    }
    
    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> data = new HashMap<>();
        data.put("worldName", worldName);
        data.put("levels", levels);
        data.put("isActive", isActive);
        data.put("createdTimestamp", createdTimestamp);
        return data;
    }
    
    @SuppressWarnings("unchecked")
    public static SQLDungeonWorld deserialize(Map<String, Object> data) {
        SQLDungeonWorld world = new SQLDungeonWorld();
        world.worldName = (String) data.get("worldName");
        
        // Handle levels map conversion properly
        Object levelsObj = data.get("levels");
        if (levelsObj instanceof Map) {
            Map<?, ?> levelsMap = (Map<?, ?>) levelsObj;
            world.levels = new TreeMap<>();
            
            for (Map.Entry<?, ?> entry : levelsMap.entrySet()) {
                Integer levelNum = null;
                
                // Handle different key types (Integer or String)
                if (entry.getKey() instanceof Integer) {
                    levelNum = (Integer) entry.getKey();
                } else if (entry.getKey() instanceof String) {
                    try {
                        levelNum = Integer.parseInt((String) entry.getKey());
                    } catch (NumberFormatException e) {
                        continue; // Skip invalid entries
                    }
                }
                
                if (levelNum == null) {
                    continue;
                }

                Object levelObj = entry.getValue();
                if (levelObj instanceof SQLLevel) {
                    world.levels.put(levelNum, (SQLLevel) levelObj);
                } else if (levelObj instanceof Map<?, ?>) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> levelData = (Map<String, Object>) levelObj;
                    SQLLevel parsed = SQLLevel.deserialize(levelData);
                    world.levels.put(levelNum, parsed);
                }
            }
        } else {
            world.levels = new TreeMap<>();
        }
        
        world.isActive = (Boolean) data.getOrDefault("isActive", true);
        
        // Handle timestamp conversion
        Object timestampObj = data.get("createdTimestamp");
        if (timestampObj instanceof Long) {
            world.createdTimestamp = (Long) timestampObj;
        } else if (timestampObj instanceof Integer) {
            world.createdTimestamp = ((Integer) timestampObj).longValue();
        } else {
            world.createdTimestamp = System.currentTimeMillis();
        }
        
        return world;
    }
    
    @Override
    public String toString() {
        return getSummary();
    }
}