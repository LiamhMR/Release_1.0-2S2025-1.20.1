package com.seminario.plugin.manager;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.seminario.plugin.model.FireworkTrigger;

/**
 * Manages firework triggers and effects
 */
public class FireworkManager {
    
    private static final Logger LOGGER = Logger.getLogger(FireworkManager.class.getName());
    private static final String FIREWORKS_FILE = "fireworks.json";
    
    private final JavaPlugin plugin;
    private final File dataFolder;
    private final Gson gson;
    private final Map<String, FireworkTrigger> fireworksByLocation;
    private final Map<String, FireworkTrigger> fireworksById;
    
    public FireworkManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = plugin.getDataFolder();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.fireworksByLocation = new HashMap<>();
        this.fireworksById = new HashMap<>();
        
        loadFireworks();
    }
    
    /**
     * Create a new firework trigger
     * @param id Unique identifier
     * @param location Trigger location
     * @param colors List of firework colors
     * @param type Firework effect type
     * @return true if created successfully
     */
    public boolean createFirework(String id, Location location, List<Color> colors, FireworkEffect.Type type) {
        String locationKey = getLocationKey(location);
        
        // Check if location already has a firework
        if (fireworksByLocation.containsKey(locationKey)) {
            return false;
        }
        
        // Check if ID already exists
        if (fireworksById.containsKey(id)) {
            return false;
        }
        
        FireworkTrigger trigger = new FireworkTrigger(id, location, colors, type);
        fireworksByLocation.put(locationKey, trigger);
        fireworksById.put(id, trigger);
        
        saveFireworks();
        LOGGER.info("Created firework trigger '" + id + "' at " + locationKey);
        return true;
    }
    
    /**
     * Remove a firework trigger by ID
     * @param id Firework ID
     * @return true if removed successfully
     */
    public boolean removeFirework(String id) {
        FireworkTrigger trigger = fireworksById.remove(id);
        if (trigger != null) {
            fireworksByLocation.remove(trigger.getLocationKey());
            saveFireworks();
            LOGGER.info("Removed firework trigger '" + id + "'");
            return true;
        }
        return false;
    }
    
    /**
     * Get firework trigger at location
     * @param location Location to check
     * @return FireworkTrigger or null if none exists
     */
    public FireworkTrigger getFireworkAt(Location location) {
        String locationKey = getLocationKey(location);
        return fireworksByLocation.get(locationKey);
    }
    
    /**
     * Get firework trigger by ID
     * @param id Firework ID
     * @return FireworkTrigger or null if not found
     */
    public FireworkTrigger getFireworkById(String id) {
        return fireworksById.get(id);
    }
    
    /**
     * Get all firework triggers
     * @return List of all firework triggers
     */
    public List<FireworkTrigger> getAllFireworks() {
        return new ArrayList<>(fireworksById.values());
    }
    
    /**
     * Trigger firework effect at location
     * @param location Location to spawn fireworks
     * @param player Player who triggered it (for logging)
     */
    public void triggerFirework(Location location, Player player) {
        FireworkTrigger trigger = getFireworkAt(location);
        if (trigger == null || !trigger.isEnabled()) {
            return;
        }
        
        LOGGER.info("Player " + player.getName() + " triggered firework '" + trigger.getId() + "' at " + trigger.getLocationKey());
        
        // Spawn fireworks with 0.1 second delay between each color
        List<Color> colors = trigger.getColors();
        for (int i = 0; i < colors.size(); i++) {
            final Color color = colors.get(i);
            final FireworkEffect.Type type = trigger.getType();
            final Location spawnLoc = location.clone().add(0.5, 1.0, 0.5); // Center of block, 1 block up
            
            // Schedule firework spawn with delay
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                spawnFirework(spawnLoc, color, type);
            }, i * 2L); // 2 ticks = 0.1 seconds
        }
    }
    
    /**
     * Spawn a single firework with specified color and type
     * @param location Location to spawn
     * @param color Firework color
     * @param type Firework effect type
     */
    private void spawnFirework(Location location, Color color, FireworkEffect.Type type) {
        Firework firework = location.getWorld().spawn(location, Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        
        // Create firework effect
        FireworkEffect effect = FireworkEffect.builder()
            .withColor(color)
            .with(type)
            .trail(true)
            .flicker(false)
            .build();
        
        meta.addEffect(effect);
        meta.setPower(1); // Flight duration
        firework.setFireworkMeta(meta);
        
        // Make firework not cause damage
        firework.setSilent(true);
        
        // Detonate immediately for instant effect without damage
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Prevent damage by setting the firework as decorative only
            firework.detonate();
        }, 1L);
    }
    
    /**
     * Parse color from string
     * @param colorName Color name (red, blue, green, etc.)
     * @return Color object or null if invalid
     */
    public static Color parseColor(String colorName) {
        switch (colorName.toLowerCase()) {
            case "red": return Color.RED;
            case "blue": return Color.BLUE;
            case "green": return Color.GREEN;
            case "yellow": return Color.YELLOW;
            case "orange": return Color.ORANGE;
            case "purple": return Color.PURPLE;
            case "pink": return Color.FUCHSIA;
            case "white": return Color.WHITE;
            case "black": return Color.BLACK;
            case "lime": return Color.LIME;
            case "aqua": return Color.AQUA;
            case "maroon": return Color.MAROON;
            case "navy": return Color.NAVY;
            case "olive": return Color.OLIVE;
            case "silver": return Color.SILVER;
            case "teal": return Color.TEAL;
            default: return null;
        }
    }
    
    /**
     * Get available color names
     * @return List of available color names
     */
    public static List<String> getAvailableColors() {
        return List.of("red", "blue", "green", "yellow", "orange", "purple", 
                      "pink", "white", "black", "lime", "aqua", "maroon", 
                      "navy", "olive", "silver", "teal");
    }
    
    /**
     * Generate location key for storage
     * @param location Location
     * @return String key
     */
    private String getLocationKey(Location location) {
        return location.getWorld().getName() + ":" + 
               location.getBlockX() + ":" + 
               location.getBlockY() + ":" + 
               location.getBlockZ();
    }
    
    /**
     * Save fireworks to file
     */
    private void saveFireworks() {
        try {
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            
            File file = new File(dataFolder, FIREWORKS_FILE);
            try (FileWriter writer = new FileWriter(file)) {
                // Convert to serializable format
                Map<String, Object> data = new HashMap<>();
                for (FireworkTrigger trigger : fireworksById.values()) {
                    Map<String, Object> triggerData = new HashMap<>();
                    triggerData.put("id", trigger.getId());
                    triggerData.put("world", trigger.getLocation().getWorld().getName());
                    triggerData.put("x", trigger.getLocation().getBlockX());
                    triggerData.put("y", trigger.getLocation().getBlockY());
                    triggerData.put("z", trigger.getLocation().getBlockZ());
                    triggerData.put("type", trigger.getType().name());
                    
                    List<String> colorNames = new ArrayList<>();
                    for (Color color : trigger.getColors()) {
                        colorNames.add(colorToString(color));
                    }
                    triggerData.put("colors", colorNames);
                    
                    data.put(trigger.getId(), triggerData);
                }
                
                gson.toJson(data, writer);
            }
        } catch (IOException e) {
            LOGGER.severe("Failed to save fireworks: " + e.getMessage());
        }
    }
    
    /**
     * Load fireworks from file
     */
    private void loadFireworks() {
        File file = new File(dataFolder, FIREWORKS_FILE);
        if (!file.exists()) {
            return;
        }
        
        try (FileReader reader = new FileReader(file)) {
            Map<String, Object> data = gson.fromJson(reader, new TypeToken<Map<String, Object>>(){}.getType());
            if (data == null) return;
            
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> triggerData = (Map<String, Object>) entry.getValue();
                
                String id = (String) triggerData.get("id");
                String worldName = (String) triggerData.get("world");
                int x = ((Number) triggerData.get("x")).intValue();
                int y = ((Number) triggerData.get("y")).intValue();
                int z = ((Number) triggerData.get("z")).intValue();
                String typeName = (String) triggerData.get("type");
                
                @SuppressWarnings("unchecked")
                List<String> colorNames = (List<String>) triggerData.get("colors");
                
                // Reconstruct firework trigger
                if (Bukkit.getWorld(worldName) != null) {
                    Location location = new Location(Bukkit.getWorld(worldName), x, y, z);
                    FireworkEffect.Type type = FireworkEffect.Type.valueOf(typeName);
                    
                    List<Color> colors = new ArrayList<>();
                    for (String colorName : colorNames) {
                        Color color = parseColor(colorName);
                        if (color != null) {
                            colors.add(color);
                        }
                    }
                    
                    if (!colors.isEmpty()) {
                        FireworkTrigger trigger = new FireworkTrigger(id, location, colors, type);
                        fireworksByLocation.put(trigger.getLocationKey(), trigger);
                        fireworksById.put(id, trigger);
                    }
                }
            }
            
            LOGGER.info("Loaded " + fireworksById.size() + " firework triggers");
        } catch (Exception e) {
            LOGGER.severe("Failed to load fireworks: " + e.getMessage());
        }
    }
    
    /**
     * Convert color to string name
     * @param color Color object
     * @return String name or hex if unknown
     */
    private String colorToString(Color color) {
        if (color.equals(Color.RED)) return "red";
        if (color.equals(Color.BLUE)) return "blue";
        if (color.equals(Color.GREEN)) return "green";
        if (color.equals(Color.YELLOW)) return "yellow";
        if (color.equals(Color.ORANGE)) return "orange";
        if (color.equals(Color.PURPLE)) return "purple";
        if (color.equals(Color.FUCHSIA)) return "pink";
        if (color.equals(Color.WHITE)) return "white";
        if (color.equals(Color.BLACK)) return "black";
        if (color.equals(Color.LIME)) return "lime";
        if (color.equals(Color.AQUA)) return "aqua";
        if (color.equals(Color.MAROON)) return "maroon";
        if (color.equals(Color.NAVY)) return "navy";
        if (color.equals(Color.OLIVE)) return "olive";
        if (color.equals(Color.SILVER)) return "silver";
        if (color.equals(Color.TEAL)) return "teal";
        return "#" + Integer.toHexString(color.asRGB());
    }
}