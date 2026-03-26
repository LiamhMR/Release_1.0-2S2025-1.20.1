package com.seminario.plugin.model;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
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
        data.put("startLocation", serializeLocation(startLocation));
        data.put("checkpointLocation", serializeLocation(checkpointLocation));
        data.put("preparationLocation", serializeLocation(preparationLocation));
        data.put("enemySpawnPos1", serializeLocation(enemySpawnPos1));
        data.put("enemySpawnPos2", serializeLocation(enemySpawnPos2));
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

        world.startLocation = deserializeLocation(data.get("startLocation"));
        world.preparationLocation = deserializeLocation(data.get("preparationLocation"));
        world.checkpointLocation = deserializeLocation(data.get("checkpointLocation"));
        world.enemySpawnPos1 = deserializeLocation(data.get("enemySpawnPos1"));
        world.enemySpawnPos2 = deserializeLocation(data.get("enemySpawnPos2"));

        return world;
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
}