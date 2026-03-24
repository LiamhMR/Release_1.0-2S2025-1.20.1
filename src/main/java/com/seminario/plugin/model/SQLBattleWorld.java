package com.seminario.plugin.model;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.configuration.serialization.ConfigurationSerializable;

/**
 * Stores per-world SQL Battle scenario configuration.
 */
public class SQLBattleWorld implements ConfigurationSerializable {

    private String worldName;
    private boolean active;
    private long createdTimestamp;
    private Location startLocation;
    private Location checkpointLocation;
    private Location preparationLocation;
    private Location enemySpawnPos1;
    private Location enemySpawnPos2;

    public SQLBattleWorld() {
        this.active = true;
        this.createdTimestamp = System.currentTimeMillis();
    }

    public SQLBattleWorld(String worldName) {
        this.worldName = worldName;
        this.active = true;
        this.createdTimestamp = System.currentTimeMillis();
    }

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public long getCreatedTimestamp() {
        return createdTimestamp;
    }

    public void setCreatedTimestamp(long createdTimestamp) {
        this.createdTimestamp = createdTimestamp;
    }

    public Location getStartLocation() {
        return startLocation;
    }

    public void setStartLocation(Location startLocation) {
        this.startLocation = startLocation;
    }

    public Location getPreparationLocation() {
        return preparationLocation;
    }

    public Location getCheckpointLocation() {
        return checkpointLocation;
    }

    public void setCheckpointLocation(Location checkpointLocation) {
        this.checkpointLocation = checkpointLocation;
    }

    public void setPreparationLocation(Location preparationLocation) {
        this.preparationLocation = preparationLocation;
    }

    public Location getEnemySpawnPos1() {
        return enemySpawnPos1;
    }

    public void setEnemySpawnPos1(Location enemySpawnPos1) {
        this.enemySpawnPos1 = enemySpawnPos1;
    }

    public Location getEnemySpawnPos2() {
        return enemySpawnPos2;
    }

    public void setEnemySpawnPos2(Location enemySpawnPos2) {
        this.enemySpawnPos2 = enemySpawnPos2;
    }

    public boolean hasStartLocation() {
        return startLocation != null && startLocation.getWorld() != null;
    }

    public boolean hasPreparationLocation() {
        return preparationLocation != null && preparationLocation.getWorld() != null;
    }

    public boolean hasCheckpointLocation() {
        return checkpointLocation != null && checkpointLocation.getWorld() != null;
    }

    public boolean hasEnemySpawnZone() {
        return enemySpawnPos1 != null && enemySpawnPos2 != null
            && enemySpawnPos1.getWorld() != null && enemySpawnPos2.getWorld() != null;
    }

    public boolean isConfigured() {
        return hasStartLocation() && hasCheckpointLocation() && hasPreparationLocation() && hasEnemySpawnZone();
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> data = new HashMap<>();
        data.put("worldName", worldName);
        data.put("active", active);
        data.put("createdTimestamp", createdTimestamp);
        data.put("startLocation", startLocation);
        data.put("checkpointLocation", checkpointLocation);
        data.put("preparationLocation", preparationLocation);
        data.put("enemySpawnPos1", enemySpawnPos1);
        data.put("enemySpawnPos2", enemySpawnPos2);
        return data;
    }

    public static SQLBattleWorld deserialize(Map<String, Object> data) {
        SQLBattleWorld world = new SQLBattleWorld();
        world.worldName = (String) data.get("worldName");
        world.active = (Boolean) data.getOrDefault("active", true);

        Object timestampObj = data.get("createdTimestamp");
        if (timestampObj instanceof Long) {
            world.createdTimestamp = (Long) timestampObj;
        } else if (timestampObj instanceof Integer) {
            world.createdTimestamp = ((Integer) timestampObj).longValue();
        } else {
            world.createdTimestamp = System.currentTimeMillis();
        }

        Object startObj = data.get("startLocation");
        if (startObj instanceof Location) {
            world.startLocation = (Location) startObj;
        }

        Object prepObj = data.get("preparationLocation");
        if (prepObj instanceof Location) {
            world.preparationLocation = (Location) prepObj;
        }

        Object checkpointObj = data.get("checkpointLocation");
        if (checkpointObj instanceof Location) {
            world.checkpointLocation = (Location) checkpointObj;
        }

        Object pos1Obj = data.get("enemySpawnPos1");
        if (pos1Obj instanceof Location) {
            world.enemySpawnPos1 = (Location) pos1Obj;
        }

        Object pos2Obj = data.get("enemySpawnPos2");
        if (pos2Obj instanceof Location) {
            world.enemySpawnPos2 = (Location) pos2Obj;
        }

        return world;
    }
}