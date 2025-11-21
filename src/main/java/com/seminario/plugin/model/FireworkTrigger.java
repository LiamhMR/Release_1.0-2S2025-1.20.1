package com.seminario.plugin.model;

import java.util.List;

import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;

/**
 * Represents a firework trigger zone with customizable colors and effects
 */
public class FireworkTrigger {
    
    private final String id;
    private final Location location;
    private final List<Color> colors;
    private final FireworkEffect.Type type;
    private final boolean enabled;
    
    public FireworkTrigger(String id, Location location, List<Color> colors, FireworkEffect.Type type) {
        this.id = id;
        this.location = location.clone();
        this.colors = colors;
        this.type = type;
        this.enabled = true;
    }
    
    public String getId() {
        return id;
    }
    
    public Location getLocation() {
        return location.clone();
    }
    
    public List<Color> getColors() {
        return colors;
    }
    
    public FireworkEffect.Type getType() {
        return type;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Check if the given location matches this trigger location
     * @param loc Location to check
     * @return true if same block coordinates
     */
    public boolean matches(Location loc) {
        return location.getWorld().equals(loc.getWorld()) &&
               location.getBlockX() == loc.getBlockX() &&
               location.getBlockY() == loc.getBlockY() &&
               location.getBlockZ() == loc.getBlockZ();
    }
    
    /**
     * Get location key for storage
     * @return String representation of location
     */
    public String getLocationKey() {
        return location.getWorld().getName() + ":" + 
               location.getBlockX() + ":" + 
               location.getBlockY() + ":" + 
               location.getBlockZ();
    }
    
    @Override
    public String toString() {
        return "FireworkTrigger{" +
               "id='" + id + '\'' +
               ", location=" + getLocationKey() +
               ", colors=" + colors.size() +
               ", type=" + type +
               ", enabled=" + enabled +
               '}';
    }
}