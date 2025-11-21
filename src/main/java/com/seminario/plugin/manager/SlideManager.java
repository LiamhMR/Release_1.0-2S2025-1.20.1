package com.seminario.plugin.manager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import com.seminario.plugin.model.Slide;

/**
 * Manages slideshow presentations for menu zones
 * Handles CRUD operations for slides and their persistence
 */
public class SlideManager {
    
    private final JavaPlugin plugin;
    private final Logger logger;
    private final File slidesFile;
    private FileConfiguration slidesConfig;
    
    // Map: zoneName -> List of slides ordered by slide number
    private final Map<String, List<Slide>> zoneSlides;
    
    public SlideManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.slidesFile = new File(plugin.getDataFolder(), "slides.yml");
        this.zoneSlides = new HashMap<>();
        
        loadSlides();
        clearAllMapIds(); // Limpiar IDs de mapas para forzar regeneración en nuevo formato
    }
    
    /**
     * Clear all cached map IDs to force regeneration
     */
    private void clearAllMapIds() {
        int clearedCount = 0;
        for (List<Slide> slides : zoneSlides.values()) {
            for (Slide slide : slides) {
                if (slide.hasMapIds()) {
                    slide.clearMapIds();
                    clearedCount++;
                }
            }
        }
        if (clearedCount > 0) {
            saveSlides();
            logger.info("Cleared map IDs for " + clearedCount + " slides - will regenerate in 12x9 format");
        }
    }
    
    /**
     * Load slides configuration from file
     */
    private void loadSlides() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        
        if (!slidesFile.exists()) {
            try {
                slidesFile.createNewFile();
                logger.info("Created new slides.yml file");
            } catch (IOException e) {
                logger.severe("Could not create slides.yml file: " + e.getMessage());
                return;
            }
        }
        
        slidesConfig = YamlConfiguration.loadConfiguration(slidesFile);
        loadSlidesFromConfig();
    }
    
    /**
     * Load all slides from configuration
     */
    private void loadSlidesFromConfig() {
        zoneSlides.clear();
        
        ConfigurationSection zonesSection = slidesConfig.getConfigurationSection("zones");
        if (zonesSection == null) {
            logger.info("No slides found in configuration");
            return;
        }
        
        for (String zoneName : zonesSection.getKeys(false)) {
            try {
                ConfigurationSection zoneSection = zonesSection.getConfigurationSection(zoneName);
                if (zoneSection != null) {
                    List<Slide> slides = new ArrayList<>();
                    
                    ConfigurationSection slidesSection = zoneSection.getConfigurationSection("slides");
                    if (slidesSection != null) {
                        for (String slideKey : slidesSection.getKeys(false)) {
                            ConfigurationSection slideSection = slidesSection.getConfigurationSection(slideKey);
                            if (slideSection != null) {
                                Map<String, Object> slideData = new HashMap<>();
                                for (String key : slideSection.getKeys(true)) {
                                    slideData.put(key, slideSection.get(key));
                                }
                                
                                Slide slide = Slide.deserialize(slideData);
                                slides.add(slide);
                            }
                        }
                    }
                    
                    // Sort slides by slide number
                    slides.sort(Comparator.comparingInt(Slide::getSlideNumber));
                    zoneSlides.put(zoneName, slides);
                    logger.info("Loaded " + slides.size() + " slides for zone: " + zoneName);
                }
            } catch (Exception e) {
                logger.warning("Failed to load slides for zone '" + zoneName + "': " + e.getMessage());
            }
        }
        
        logger.info("Loaded slides for " + zoneSlides.size() + " zones");
    }
    
    /**
     * Save slides configuration to file
     */
    public void saveSlides() {
        try {
            // Clear existing slides section
            slidesConfig.set("zones", null);
            
            // Save all zone slides
            for (Map.Entry<String, List<Slide>> entry : zoneSlides.entrySet()) {
                String zoneName = entry.getKey();
                List<Slide> slides = entry.getValue();
                
                String zonePath = "zones." + zoneName;
                
                for (int i = 0; i < slides.size(); i++) {
                    Slide slide = slides.get(i);
                    String slidePath = zonePath + ".slides.slide" + (i + 1);
                    
                    Map<String, Object> slideData = slide.serialize();
                    for (Map.Entry<String, Object> dataEntry : slideData.entrySet()) {
                        slidesConfig.set(slidePath + "." + dataEntry.getKey(), dataEntry.getValue());
                    }
                }
            }
            
            slidesConfig.save(slidesFile);
            logger.info("Saved slides configuration");
        } catch (IOException e) {
            logger.severe("Could not save slides.yml: " + e.getMessage());
        }
    }
    
    /**
     * Add a new slide to a zone
     * @param zoneName The zone name
     * @param url The image URL
     * @return The created slide or null if failed
     */
    public Slide addSlide(String zoneName, String url) {
        List<Slide> slides = zoneSlides.computeIfAbsent(zoneName, k -> new ArrayList<>());
        
        int slideNumber = slides.size() + 1;
        Slide newSlide = new Slide(url, slideNumber);
        slides.add(newSlide);
        
        saveSlides();
        logger.info("Added slide #" + slideNumber + " to zone '" + zoneName + "': " + url);
        return newSlide;
    }
    
    /**
     * Edit an existing slide
     * @param zoneName The zone name
     * @param slideNumber The slide number (1-based)
     * @param newUrl The new image URL
     * @return true if updated successfully
     */
    public boolean editSlide(String zoneName, int slideNumber, String newUrl) {
        List<Slide> slides = zoneSlides.get(zoneName);
        if (slides == null || slideNumber < 1 || slideNumber > slides.size()) {
            return false;
        }
        
        // Remove old slide and create new one with same number
        slides.remove(slideNumber - 1);
        Slide newSlide = new Slide(newUrl, slideNumber);
        slides.add(slideNumber - 1, newSlide);
        
        saveSlides();
        logger.info("Edited slide #" + slideNumber + " in zone '" + zoneName + "': " + newUrl);
        return true;
    }
    
    /**
     * Delete a slide
     * @param zoneName The zone name
     * @param slideNumber The slide number (1-based)
     * @return true if deleted successfully
     */
    public boolean deleteSlide(String zoneName, int slideNumber) {
        List<Slide> slides = zoneSlides.get(zoneName);
        if (slides == null || slideNumber < 1 || slideNumber > slides.size()) {
            return false;
        }
        
        Slide removedSlide = slides.remove(slideNumber - 1);
        
        // Renumber remaining slides
        for (int i = slideNumber - 1; i < slides.size(); i++) {
            Slide slide = slides.get(i);
            // Create a new slide with updated number
            Slide renumberedSlide = new Slide(slide.getUrl(), i + 1, 
                slide.getMapIds(), slide.getCachedImagePath(), slide.getLastUpdated());
            slides.set(i, renumberedSlide);
        }
        
        saveSlides();
        logger.info("Deleted slide #" + slideNumber + " from zone '" + zoneName + "': " + removedSlide.getUrl());
        return true;
    }
    
    /**
     * Get all slides for a zone
     * @param zoneName The zone name
     * @return List of slides (empty if none)
     */
    public List<Slide> getSlides(String zoneName) {
        List<Slide> slides = zoneSlides.get(zoneName);
        return slides != null ? new ArrayList<>(slides) : new ArrayList<>();
    }
    
    /**
     * Get a specific slide
     * @param zoneName The zone name
     * @param slideNumber The slide number (1-based)
     * @return The slide or null if not found
     */
    public Slide getSlide(String zoneName, int slideNumber) {
        List<Slide> slides = zoneSlides.get(zoneName);
        if (slides == null || slideNumber < 1 || slideNumber > slides.size()) {
            return null;
        }
        return slides.get(slideNumber - 1);
    }
    
    /**
     * Get total slide count for a zone
     * @param zoneName The zone name
     * @return Number of slides
     */
    public int getSlideCount(String zoneName) {
        List<Slide> slides = zoneSlides.get(zoneName);
        return slides != null ? slides.size() : 0;
    }
    
    /**
     * Check if a zone has any slides
     * @param zoneName The zone name
     * @return true if has slides
     */
    public boolean hasSlides(String zoneName) {
        return getSlideCount(zoneName) > 0;
    }
    
    /**
     * Get all zone names that have slides
     * @return Set of zone names
     */
    public Set<String> getZonesWithSlides() {
        return new HashSet<>(zoneSlides.keySet());
    }
    
    /**
     * Remove all slides for a zone
     * @param zoneName The zone name
     * @return Number of slides removed
     */
    public int clearSlides(String zoneName) {
        List<Slide> slides = zoneSlides.remove(zoneName);
        if (slides != null) {
            saveSlides();
            logger.info("Cleared " + slides.size() + " slides from zone: " + zoneName);
            return slides.size();
        }
        return 0;
    }
    
    /**
     * Clear all slide caches (force regeneration)
     * This removes map IDs from all slides, forcing them to be regenerated
     */
    public void clearAllSlideCache() {
        int totalCleared = 0;
        for (List<Slide> slides : zoneSlides.values()) {
            for (Slide slide : slides) {
                if (slide.hasMapIds()) {
                    slide.clearMapIds();
                    totalCleared++;
                }
            }
        }
        if (totalCleared > 0) {
            saveSlides();
            logger.info("Cleared cache for " + totalCleared + " slides (will regenerate on next view)");
        }
    }
    
    /**
     * Reload slides from file
     */
    public void reload() {
        loadSlides();
    }
}