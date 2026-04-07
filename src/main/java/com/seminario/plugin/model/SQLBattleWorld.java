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
    private Location worldEntryLocation;
    private Location startLocation;
    private Location waveStartLocation;
    private Location checkpointLocation;
    private Location preparationLocation;
    private Location summonZonePos1;
    private Location summonZonePos2;
    private Location enemySpawnPos1;
    private Location enemySpawnPos2;
    private Location entryZonePos1;
    private Location entryZonePos2;

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

    public Location getWorldEntryLocation() {
        return worldEntryLocation;
    }

    public void setWorldEntryLocation(Location worldEntryLocation) {
        this.worldEntryLocation = worldEntryLocation;
    }

    public Location getStartLocation() {
        return waveStartLocation != null ? waveStartLocation : startLocation;
    }

    public void setStartLocation(Location startLocation) {
        this.startLocation = startLocation;
        this.waveStartLocation = startLocation;
    }

    public Location getWaveStartLocation() {
        return waveStartLocation != null ? waveStartLocation : startLocation;
    }

    public void setWaveStartLocation(Location waveStartLocation) {
        this.waveStartLocation = waveStartLocation;
        this.startLocation = waveStartLocation;
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

    public Location getSummonZonePos1() {
        return summonZonePos1;
    }

    public void setSummonZonePos1(Location summonZonePos1) {
        this.summonZonePos1 = summonZonePos1;
    }

    public Location getSummonZonePos2() {
        return summonZonePos2;
    }

    public void setSummonZonePos2(Location summonZonePos2) {
        this.summonZonePos2 = summonZonePos2;
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
        Location location = getStartLocation();
        return location != null && location.getWorld() != null;
    }

    public boolean hasWorldEntryLocation() {
        return worldEntryLocation != null && worldEntryLocation.getWorld() != null;
    }

    public boolean hasWaveStartLocation() {
        Location location = getWaveStartLocation();
        return location != null && location.getWorld() != null;
    }

    public boolean hasPreparationLocation() {
        return preparationLocation != null && preparationLocation.getWorld() != null;
    }

    public boolean hasCheckpointLocation() {
        return checkpointLocation != null && checkpointLocation.getWorld() != null;
    }

    public boolean hasSummonZone() {
        return summonZonePos1 != null && summonZonePos2 != null
            && summonZonePos1.getWorld() != null && summonZonePos2.getWorld() != null;
    }

    public boolean hasEnemySpawnZone() {
        return enemySpawnPos1 != null && enemySpawnPos2 != null
            && enemySpawnPos1.getWorld() != null && enemySpawnPos2.getWorld() != null;
    }

    public Location getEntryZonePos1() {
        return entryZonePos1;
    }

    public void setEntryZonePos1(Location entryZonePos1) {
        this.entryZonePos1 = entryZonePos1;
    }

    public Location getEntryZonePos2() {
        return entryZonePos2;
    }

    public void setEntryZonePos2(Location entryZonePos2) {
        this.entryZonePos2 = entryZonePos2;
    }

    public boolean hasEntryZone() {
        return entryZonePos1 != null && entryZonePos2 != null
            && entryZonePos1.getWorld() != null && entryZonePos2.getWorld() != null;
    }

    public boolean isConfigured() {
        return hasWaveStartLocation() && hasCheckpointLocation() && hasPreparationLocation() && hasEnemySpawnZone();
    }

    public boolean isExpandedConfigured() {
        return isConfigured() && (hasWorldEntryLocation() || hasEntryZone()) && hasSummonZone();
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> data = new HashMap<>();
        data.put("worldName", worldName);
        data.put("active", active);
        data.put("createdTimestamp", createdTimestamp);
        data.put("worldEntryLocation", serializeLocation(worldEntryLocation));
        data.put("startLocation", serializeLocation(getStartLocation()));
        data.put("waveStartLocation", serializeLocation(getWaveStartLocation()));
        data.put("checkpointLocation", serializeLocation(checkpointLocation));
        data.put("preparationLocation", serializeLocation(preparationLocation));
        data.put("summonZonePos1", serializeLocation(summonZonePos1));
        data.put("summonZonePos2", serializeLocation(summonZonePos2));
        data.put("enemySpawnPos1", serializeLocation(enemySpawnPos1));
        data.put("enemySpawnPos2", serializeLocation(enemySpawnPos2));
        data.put("entryZonePos1", serializeLocation(entryZonePos1));
        data.put("entryZonePos2", serializeLocation(entryZonePos2));
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

        world.worldEntryLocation = deserializeLocation(data.get("worldEntryLocation"));
        world.startLocation = deserializeLocation(data.get("startLocation"));
        world.waveStartLocation = deserializeLocation(data.get("waveStartLocation"));
        if (world.waveStartLocation == null) {
            world.waveStartLocation = world.startLocation;
        }
        world.preparationLocation = deserializeLocation(data.get("preparationLocation"));
        world.checkpointLocation = deserializeLocation(data.get("checkpointLocation"));
        world.summonZonePos1 = deserializeLocation(data.get("summonZonePos1"));
        world.summonZonePos2 = deserializeLocation(data.get("summonZonePos2"));
        world.enemySpawnPos1 = deserializeLocation(data.get("enemySpawnPos1"));
        world.enemySpawnPos2 = deserializeLocation(data.get("enemySpawnPos2"));
        world.entryZonePos1 = deserializeLocation(data.get("entryZonePos1"));
        world.entryZonePos2 = deserializeLocation(data.get("entryZonePos2"));

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