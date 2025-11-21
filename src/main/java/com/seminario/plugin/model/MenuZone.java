package com.seminario.plugin.model;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.serialization.ConfigurationSerializable;

/**
 * Represents a menu zone defined by two corner locations
 * This zone will trigger menu actions when players enter it
 */
public class MenuZone implements ConfigurationSerializable {
    
    private final String name;
    private final Location pos1;
    private final Location pos2;
    private final World world;
    private MenuType menuType;
    
    // Teleport data for CHESTPORT menu type
    private Location teleportLocation;
    private String teleportWorldName;
    
    // Fixed direction for SLIDE menu type (+X, -X, +Z, -Z)
    private String slideDirection;
    
    // Fixed position for SLIDE menu type (para evitar que se mueva con el jugador)
    private Location slideFixedLocation;
    private String slideFixedFacing; // Direction as string (NORTH, SOUTH, EAST, WEST)
    
    // FIXSLIDE specific data
    private String linkedSlideZone; // Nombre de la zona SLIDE de la que comparte slides
    private Location fixSlideRenderLocation; // Posición fija de renderizado permanente
    private String fixSlideDirection; // Dirección fija (+X, -X, +Z, -Z)
    private Location nextButtonLocation; // Ubicación del botón "siguiente"
    private Location backButtonLocation; // Ubicación del botón "anterior"
    
    // General zone control
    private boolean disabled; // Si la zona está deshabilitada
    
    public MenuZone(String name, Location pos1, Location pos2) {
        this(name, pos1, pos2, null);
    }
    
    public MenuZone(String name, Location pos1, Location pos2, MenuType menuType) {
        this.name = name;
        this.pos1 = pos1.clone();
        this.pos2 = pos2.clone();
        this.world = pos1.getWorld();
        this.menuType = menuType;
        
        if (!pos1.getWorld().equals(pos2.getWorld())) {
            throw new IllegalArgumentException("Both positions must be in the same world");
        }
    }
    
    /**
     * Check if a location is within this menu zone
     * @param location The location to check
     * @return true if the location is within the zone
     */
    public boolean contains(Location location) {
        if (!location.getWorld().equals(world)) {
            return false;
        }
        
        double minX = Math.min(pos1.getX(), pos2.getX());
        double maxX = Math.max(pos1.getX(), pos2.getX());
        double minY = Math.min(pos1.getY(), pos2.getY());
        double maxY = Math.max(pos1.getY(), pos2.getY());
        double minZ = Math.min(pos1.getZ(), pos2.getZ());
        double maxZ = Math.max(pos1.getZ(), pos2.getZ());
        
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();
        
        return x >= minX && x <= maxX && 
               y >= minY && y <= maxY && 
               z >= minZ && z <= maxZ;
    }
    
    // Getters
    public String getName() {
        return name;
    }
    
    public Location getPos1() {
        return pos1.clone();
    }
    
    public Location getPos2() {
        return pos2.clone();
    }
    
    public World getWorld() {
        return world;
    }
    
    public MenuType getMenuType() {
        return menuType;
    }
    
    // Setter for menu type
    public void setMenuType(MenuType menuType) {
        this.menuType = menuType;
    }
    
    public boolean hasMenuType() {
        return menuType != null;
    }
    
    /**
     * Get the teleport location for CHESTPORT menu type
     * @return The teleport location or null if not set
     */
    public Location getTeleportLocation() {
        return teleportLocation != null ? teleportLocation.clone() : null;
    }
    
    /**
     * Set the teleport location for CHESTPORT menu type
     * @param location The location to teleport to
     */
    public void setTeleportLocation(Location location) {
        if (location != null) {
            this.teleportLocation = location.clone();
            this.teleportWorldName = location.getWorld().getName();
        } else {
            this.teleportLocation = null;
            this.teleportWorldName = null;
        }
    }
    
    /**
     * Get the teleport world name
     * @return The world name or null if not set
     */
    public String getTeleportWorldName() {
        return teleportWorldName;
    }
    
    /**
     * Check if this zone has teleport data configured
     * @return true if teleport location is set
     */
    public boolean hasTeleportLocation() {
        return teleportLocation != null && teleportWorldName != null;
    }
    
    /**
     * Get the fixed slide direction for SLIDE menu type
     * @return The direction (+X, -X, +Z, -Z) or null if not set
     */
    public String getSlideDirection() {
        return slideDirection;
    }
    
    /**
     * Set the fixed slide direction for SLIDE menu type
     * @param direction The direction (+X, -X, +Z, -Z) or null to clear
     */
    public void setSlideDirection(String direction) {
        this.slideDirection = direction;
    }
    
    /**
     * Check if this zone has a fixed slide direction
     * @return true if slide direction is set
     */
    public boolean hasSlideDirection() {
        return slideDirection != null;
    }
    
    /**
     * Get the fixed slide position for SLIDE menu type
     * @return The location or null if not set
     */
    public Location getSlideFixedLocation() {
        return slideFixedLocation != null ? slideFixedLocation.clone() : null;
    }
    
    /**
     * Set the fixed slide position for SLIDE menu type
     * @param location The location where the slide should be rendered
     */
    public void setSlideFixedLocation(Location location) {
        this.slideFixedLocation = location != null ? location.clone() : null;
    }
    
    /**
     * Get the fixed slide facing direction
     * @return The facing direction (NORTH, SOUTH, EAST, WEST) or null if not set
     */
    public String getSlideFixedFacing() {
        return slideFixedFacing;
    }
    
    /**
     * Set the fixed slide facing direction
     * @param facing The facing direction (NORTH, SOUTH, EAST, WEST)
     */
    public void setSlideFixedFacing(String facing) {
        this.slideFixedFacing = facing;
    }
    
    /**
     * Check if this zone has a fixed slide position
     * @return true if slideFixedLocation is set
     */
    public boolean hasSlideFixedLocation() {
        return slideFixedLocation != null && slideFixedFacing != null;
    }
    
    /**
     * Clear the fixed slide position
     */
    public void clearSlideFixedLocation() {
        this.slideFixedLocation = null;
        this.slideFixedFacing = null;
    }
    
    // ==================== FIXSLIDE Methods ====================
    
    /**
     * Get the linked SLIDE zone name for FIXSLIDE type
     * @return The zone name or null if not set
     */
    public String getLinkedSlideZone() {
        return linkedSlideZone;
    }
    
    /**
     * Set the linked SLIDE zone for FIXSLIDE type
     * @param zoneName The SLIDE zone name to link
     */
    public void setLinkedSlideZone(String zoneName) {
        this.linkedSlideZone = zoneName;
    }
    
    /**
     * Get the fixed render location for FIXSLIDE
     * @return The location or null if not set
     */
    public Location getFixSlideRenderLocation() {
        return fixSlideRenderLocation != null ? fixSlideRenderLocation.clone() : null;
    }
    
    /**
     * Set the fixed render location for FIXSLIDE
     * @param location The location where the presentation renders permanently
     */
    public void setFixSlideRenderLocation(Location location) {
        this.fixSlideRenderLocation = location != null ? location.clone() : null;
    }
    
    /**
     * Get the fixed direction for FIXSLIDE
     * @return The direction (+X, -X, +Z, -Z) or null if not set
     */
    public String getFixSlideDirection() {
        return fixSlideDirection;
    }
    
    /**
     * Set the fixed direction for FIXSLIDE
     * @param direction The direction (+X, -X, +Z, -Z)
     */
    public void setFixSlideDirection(String direction) {
        this.fixSlideDirection = direction;
    }
    
    /**
     * Get the next button location for FIXSLIDE
     * @return The location or null if not set
     */
    public Location getNextButtonLocation() {
        return nextButtonLocation != null ? nextButtonLocation.clone() : null;
    }
    
    /**
     * Set the next button location for FIXSLIDE
     * @param location The location where the next button appears
     */
    public void setNextButtonLocation(Location location) {
        this.nextButtonLocation = location != null ? location.clone() : null;
    }
    
    /**
     * Get the back button location for FIXSLIDE
     * @return The location or null if not set
     */
    public Location getBackButtonLocation() {
        return backButtonLocation != null ? backButtonLocation.clone() : null;
    }
    
    /**
     * Set the back button location for FIXSLIDE
     * @param location The location where the back button appears
     */
    public void setBackButtonLocation(Location location) {
        this.backButtonLocation = location != null ? location.clone() : null;
    }
    
    /**
     * Check if this FIXSLIDE has the render location configured
     * @return true if fixSlideRenderLocation is set
     */
    public boolean hasFixSlideRenderLocation() {
        return fixSlideRenderLocation != null && fixSlideDirection != null;
    }
    
    // ==================== Zone Control Methods ====================
    
    /**
     * Check if this zone is disabled
     * @return true if disabled
     */
    public boolean isDisabled() {
        return disabled;
    }
    
    /**
     * Set the disabled state of this zone
     * @param disabled true to disable, false to enable
     */
    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }
    
    // ConfigurationSerializable implementation for YAML storage
    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("world", world.getName());
        data.put("pos1.x", pos1.getX());
        data.put("pos1.y", pos1.getY());
        data.put("pos1.z", pos1.getZ());
        data.put("pos2.x", pos2.getX());
        data.put("pos2.y", pos2.getY());
        data.put("pos2.z", pos2.getZ());
        if (menuType != null) {
            data.put("menuType", menuType.getName());
        }
        
        // Serialize teleport data for CHESTPORT
        if (teleportLocation != null) {
            data.put("teleport.x", teleportLocation.getX());
            data.put("teleport.y", teleportLocation.getY());
            data.put("teleport.z", teleportLocation.getZ());
            data.put("teleport.world", teleportWorldName);
        }
        
        // Serialize slide direction for SLIDE
        if (slideDirection != null) {
            data.put("slideDirection", slideDirection);
        }
        
        // Serialize slide fixed position for SLIDE
        if (slideFixedLocation != null) {
            data.put("slideFixedLocation.x", slideFixedLocation.getX());
            data.put("slideFixedLocation.y", slideFixedLocation.getY());
            data.put("slideFixedLocation.z", slideFixedLocation.getZ());
            data.put("slideFixedLocation.yaw", slideFixedLocation.getYaw());
            data.put("slideFixedLocation.pitch", slideFixedLocation.getPitch());
        }
        if (slideFixedFacing != null) {
            data.put("slideFixedFacing", slideFixedFacing);
        }
        
        // Serialize FIXSLIDE data
        if (linkedSlideZone != null) {
            data.put("linkedSlideZone", linkedSlideZone);
        }
        if (fixSlideRenderLocation != null) {
            data.put("fixSlideRenderLocation.x", fixSlideRenderLocation.getX());
            data.put("fixSlideRenderLocation.y", fixSlideRenderLocation.getY());
            data.put("fixSlideRenderLocation.z", fixSlideRenderLocation.getZ());
            data.put("fixSlideRenderLocation.yaw", fixSlideRenderLocation.getYaw());
            data.put("fixSlideRenderLocation.pitch", fixSlideRenderLocation.getPitch());
        }
        if (fixSlideDirection != null) {
            data.put("fixSlideDirection", fixSlideDirection);
        }
        if (nextButtonLocation != null) {
            data.put("nextButtonLocation.x", nextButtonLocation.getX());
            data.put("nextButtonLocation.y", nextButtonLocation.getY());
            data.put("nextButtonLocation.z", nextButtonLocation.getZ());
        }
        if (backButtonLocation != null) {
            data.put("backButtonLocation.x", backButtonLocation.getX());
            data.put("backButtonLocation.y", backButtonLocation.getY());
            data.put("backButtonLocation.z", backButtonLocation.getZ());
        }
        
        // Serialize disabled state
        data.put("disabled", disabled);
        
        return data;
    }
    
    public static MenuZone deserialize(Map<String, Object> data) {
        String name = (String) data.get("name");
        String worldName = (String) data.get("world");
        
        World world = org.bukkit.Bukkit.getWorld(worldName);
        if (world == null) {
            throw new IllegalArgumentException("World '" + worldName + "' not found");
        }
        
        Location pos1 = new Location(world, 
            (Double) data.get("pos1.x"),
            (Double) data.get("pos1.y"),
            (Double) data.get("pos1.z"));
            
        Location pos2 = new Location(world,
            (Double) data.get("pos2.x"),
            (Double) data.get("pos2.y"),
            (Double) data.get("pos2.z"));
        
        // Load menu type if present
        MenuType menuType = null;
        String menuTypeStr = (String) data.get("menuType");
        if (menuTypeStr != null) {
            menuType = MenuType.fromString(menuTypeStr);
        }
        
        MenuZone zone = new MenuZone(name, pos1, pos2, menuType);
        
        // Load teleport data if present
        if (data.containsKey("teleport.x") && data.containsKey("teleport.world")) {
            String teleportWorldName = (String) data.get("teleport.world");
            World teleportWorld = org.bukkit.Bukkit.getWorld(teleportWorldName);
            if (teleportWorld != null) {
                Location teleportLoc = new Location(teleportWorld,
                    (Double) data.get("teleport.x"),
                    (Double) data.get("teleport.y"),
                    (Double) data.get("teleport.z"));
                zone.setTeleportLocation(teleportLoc);
            }
        }
        
        // Load slide direction if present
        if (data.containsKey("slideDirection")) {
            zone.setSlideDirection((String) data.get("slideDirection"));
        }
        
        // Load slide fixed position if present
        if (data.containsKey("slideFixedLocation.x")) {
            Location fixedLoc = new Location(world,
                (Double) data.get("slideFixedLocation.x"),
                (Double) data.get("slideFixedLocation.y"),
                (Double) data.get("slideFixedLocation.z"));
            if (data.containsKey("slideFixedLocation.yaw")) {
                fixedLoc.setYaw(((Double) data.get("slideFixedLocation.yaw")).floatValue());
            }
            if (data.containsKey("slideFixedLocation.pitch")) {
                fixedLoc.setPitch(((Double) data.get("slideFixedLocation.pitch")).floatValue());
            }
            zone.setSlideFixedLocation(fixedLoc);
        }
        if (data.containsKey("slideFixedFacing")) {
            zone.setSlideFixedFacing((String) data.get("slideFixedFacing"));
        }
        
        // Load FIXSLIDE data if present
        if (data.containsKey("linkedSlideZone")) {
            zone.setLinkedSlideZone((String) data.get("linkedSlideZone"));
        }
        if (data.containsKey("fixSlideRenderLocation.x")) {
            Location renderLoc = new Location(world,
                (Double) data.get("fixSlideRenderLocation.x"),
                (Double) data.get("fixSlideRenderLocation.y"),
                (Double) data.get("fixSlideRenderLocation.z"));
            if (data.containsKey("fixSlideRenderLocation.yaw")) {
                renderLoc.setYaw(((Double) data.get("fixSlideRenderLocation.yaw")).floatValue());
            }
            if (data.containsKey("fixSlideRenderLocation.pitch")) {
                renderLoc.setPitch(((Double) data.get("fixSlideRenderLocation.pitch")).floatValue());
            }
            zone.setFixSlideRenderLocation(renderLoc);
        }
        if (data.containsKey("fixSlideDirection")) {
            zone.setFixSlideDirection((String) data.get("fixSlideDirection"));
        }
        if (data.containsKey("nextButtonLocation.x")) {
            Location nextBtn = new Location(world,
                (Double) data.get("nextButtonLocation.x"),
                (Double) data.get("nextButtonLocation.y"),
                (Double) data.get("nextButtonLocation.z"));
            zone.setNextButtonLocation(nextBtn);
        }
        if (data.containsKey("backButtonLocation.x")) {
            Location backBtn = new Location(world,
                (Double) data.get("backButtonLocation.x"),
                (Double) data.get("backButtonLocation.y"),
                (Double) data.get("backButtonLocation.z"));
            zone.setBackButtonLocation(backBtn);
        }
        
        // Load disabled state
        if (data.containsKey("disabled")) {
            zone.setDisabled((Boolean) data.get("disabled"));
        }
            
        return zone;
    }
    
    @Override
    public String toString() {
        String typeStr = menuType != null ? menuType.getName() : "none";
        return String.format("MenuZone{name='%s', world='%s', type='%s', pos1=[%.1f,%.1f,%.1f], pos2=[%.1f,%.1f,%.1f]}", 
            name, world.getName(), typeStr,
            pos1.getX(), pos1.getY(), pos1.getZ(),
            pos2.getX(), pos2.getY(), pos2.getZ());
    }
}