package com.seminario.plugin.util;

import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.seminario.plugin.model.MenuZone;

/**
 * Utility class for detecting when players are within menu zones
 * Includes vertical tolerance for detecting players "above" zones
 */
public class ZoneDetector {
    
    private static final Logger LOGGER = Logger.getLogger(ZoneDetector.class.getName());
    
    // Vertical tolerance: detect players 1-3 blocks above the zone
    private static final int MIN_VERTICAL_OFFSET = 1;
    private static final int MAX_VERTICAL_OFFSET = 3;
    
    /**
     * Check if a player is within a menu zone (including vertical tolerance)
     * @param player The player to check
     * @param zone The menu zone
     * @return true if player is within the zone area (with vertical tolerance)
     */
    public static boolean isPlayerInZone(Player player, MenuZone zone) {
        Location playerLoc = player.getLocation();
        return isLocationInZone(playerLoc, zone);
    }
    
    /**
     * Check if a location is within a menu zone (including vertical tolerance)
     * @param location The location to check
     * @param zone The menu zone
     * @return true if location is within the zone area (with vertical tolerance)
     */
    public static boolean isLocationInZone(Location location, MenuZone zone) {
        // Check if in same world
        if (!location.getWorld().equals(zone.getWorld())) {
            return false;
        }
        
        // Get zone boundaries
        double minX = Math.min(zone.getPos1().getX(), zone.getPos2().getX());
        double maxX = Math.max(zone.getPos1().getX(), zone.getPos2().getX());
        double minZ = Math.min(zone.getPos1().getZ(), zone.getPos2().getZ());
        double maxZ = Math.max(zone.getPos1().getZ(), zone.getPos2().getZ());
        
        // Zone Y levels
        double minY = Math.min(zone.getPos1().getY(), zone.getPos2().getY());
        double maxY = Math.max(zone.getPos1().getY(), zone.getPos2().getY());
        
        // Player position
        double playerX = location.getX();
        double playerY = location.getY();
        double playerZ = location.getZ();
        
        // Check X and Z boundaries (horizontal)
        boolean inHorizontalBounds = playerX >= minX && playerX <= maxX && 
                                   playerZ >= minZ && playerZ <= maxZ;
        
        if (!inHorizontalBounds) {
            return false;
        }
        
        // Check vertical bounds with tolerance
        // Player should be 1-3 blocks above the zone's max Y level
        double zoneTopY = maxY;
        boolean inVerticalRange = playerY >= (zoneTopY + MIN_VERTICAL_OFFSET) && 
                                playerY <= (zoneTopY + MAX_VERTICAL_OFFSET);
        
        return inVerticalRange;
    }
    
    /**
     * Get the vertical distance between a player and a zone
     * @param player The player
     * @param zone The menu zone  
     * @return Vertical distance (positive if player is above zone)
     */
    public static double getVerticalDistance(Player player, MenuZone zone) {
        Location playerLoc = player.getLocation();
        double maxZoneY = Math.max(zone.getPos1().getY(), zone.getPos2().getY());
        return playerLoc.getY() - maxZoneY;
    }
    
    /**
     * Check if player is horizontally within zone boundaries
     * @param player The player
     * @param zone The menu zone
     * @return true if player is within X/Z boundaries
     */
    public static boolean isPlayerInHorizontalBounds(Player player, MenuZone zone) {
        Location playerLoc = player.getLocation();
        
        if (!playerLoc.getWorld().equals(zone.getWorld())) {
            return false;
        }
        
        double minX = Math.min(zone.getPos1().getX(), zone.getPos2().getX());
        double maxX = Math.max(zone.getPos1().getX(), zone.getPos2().getX());
        double minZ = Math.min(zone.getPos1().getZ(), zone.getPos2().getZ());
        double maxZ = Math.max(zone.getPos1().getZ(), zone.getPos2().getZ());
        
        double playerX = playerLoc.getX();
        double playerZ = playerLoc.getZ();
        
        return playerX >= minX && playerX <= maxX && playerZ >= minZ && playerZ <= maxZ;
    }
    
    /**
     * Get debug information about player position relative to zone
     * @param player The player
     * @param zone The menu zone
     * @return Debug string with position information
     */
    public static String getDebugInfo(Player player, MenuZone zone) {
        Location playerLoc = player.getLocation();
        
        boolean inHorizontal = isPlayerInHorizontalBounds(player, zone);
        double verticalDistance = getVerticalDistance(player, zone);
        boolean inZone = isPlayerInZone(player, zone);
        
        return String.format(
            "Player: %s | Zone: %s | Horizontal: %s | Vertical Distance: %.2f | In Zone: %s | Player Y: %.1f | Zone Y: %.1f-%.1f",
            player.getName(), zone.getName(), inHorizontal, verticalDistance, inZone,
            playerLoc.getY(), zone.getPos1().getY(), zone.getPos2().getY()
        );
    }
    
    /**
     * Set custom vertical tolerance
     */
    private static int customMinOffset = MIN_VERTICAL_OFFSET;
    private static int customMaxOffset = MAX_VERTICAL_OFFSET;
    
    /**
     * Set custom vertical detection range
     * @param minOffset Minimum blocks above zone
     * @param maxOffset Maximum blocks above zone
     */
    public static void setVerticalTolerance(int minOffset, int maxOffset) {
        customMinOffset = minOffset;
        customMaxOffset = maxOffset;
        LOGGER.info("Updated vertical tolerance: " + minOffset + " to " + maxOffset + " blocks above zone");
    }
    
    /**
     * Check if location is in zone with custom vertical tolerance
     * @param location The location to check
     * @param zone The menu zone
     * @param minOffset Minimum blocks above zone
     * @param maxOffset Maximum blocks above zone
     * @return true if within custom range
     */
    public static boolean isLocationInZoneCustom(Location location, MenuZone zone, int minOffset, int maxOffset) {
        if (!location.getWorld().equals(zone.getWorld())) {
            return false;
        }
        
        double minX = Math.min(zone.getPos1().getX(), zone.getPos2().getX());
        double maxX = Math.max(zone.getPos1().getX(), zone.getPos2().getX());
        double minZ = Math.min(zone.getPos1().getZ(), zone.getPos2().getZ());
        double maxZ = Math.max(zone.getPos1().getZ(), zone.getPos2().getZ());
        
        double maxZoneY = Math.max(zone.getPos1().getY(), zone.getPos2().getY());
        
        double playerX = location.getX();
        double playerY = location.getY();
        double playerZ = location.getZ();
        
        boolean inHorizontalBounds = playerX >= minX && playerX <= maxX && 
                                   playerZ >= minZ && playerZ <= maxZ;
        
        if (!inHorizontalBounds) {
            return false;
        }
        
        boolean inVerticalRange = playerY >= (maxZoneY + minOffset) && 
                                playerY <= (maxZoneY + maxOffset);
        
        return inVerticalRange;
    }
}