package com.seminario.plugin.manager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import com.seminario.plugin.config.ConfigManager;
import com.seminario.plugin.model.MenuType;
import com.seminario.plugin.model.MenuZone;
import com.seminario.plugin.model.Slide;
import com.seminario.plugin.util.ImageDownloader;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Manages fixed slide presentations that render permanently at a specific location
 * FIXSLIDE zones share slides with linked SLIDE zones but render independently
 */
public class FixSlideManager {
    
    private static final Logger LOGGER = Logger.getLogger(FixSlideManager.class.getName());
    private static final double BUTTON_SIZE = 0.5;
    private static final int MAX_CONCURRENT_PROCESSING = 2; // Maximum slides being processed at once
    
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final SlideManager slideManager;
    private final ImageDownloader imageDownloader;
    
    // fixslide zone name -> current slide index
    private final Map<String, Integer> currentSlideIndex;
    
    // fixslide zone name -> list of item frames
    private final Map<String, List<ItemFrame>> activeScreens;
    
    // fixslide zone name -> button entities (next, back)
    private final Map<String, ItemFrame> nextButtons;
    private final Map<String, ItemFrame> backButtons;
    
    // fixslide zone name -> hologram entities
    private final Map<String, ArmorStand> nextHolograms;
    private final Map<String, ArmorStand> backHolograms;
    
    // Track slides currently being processed to prevent duplicate processing
    // fixslide zone name -> set of slide numbers being processed
    private final Map<String, Set<Integer>> processingSlides;
    
    public FixSlideManager(JavaPlugin plugin, ConfigManager configManager, SlideManager slideManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.slideManager = slideManager;
        this.imageDownloader = new ImageDownloader(plugin);
        this.currentSlideIndex = new HashMap<>();
        this.activeScreens = new HashMap<>();
        this.nextButtons = new HashMap<>();
        this.backButtons = new HashMap<>();
        this.nextHolograms = new HashMap<>();
        this.backHolograms = new HashMap<>();
        this.processingSlides = new HashMap<>();
    }
    
    /**
     * Initialize all FIXSLIDE zones
     * Called on plugin enable
     */
    public void initializeAllFixSlides() {
        LOGGER.info("Initializing all FIXSLIDE presentations...");
        
        // Clean up any existing FixSlide entities first
        cleanupExistingFixSlideEntities();
        
        for (MenuZone zone : configManager.getAllMenuZones().values()) {
            if (zone.hasMenuType() && zone.getMenuType() == MenuType.FIXSLIDE && !zone.isDisabled()) {
                if (zone.hasFixSlideRenderLocation()) {
                    initializeFixSlide(zone);
                } else {
                    LOGGER.warning("FIXSLIDE zone '" + zone.getName() + "' does not have render location configured");
                }
            }
        }
        
        LOGGER.info("FIXSLIDE initialization complete");
    }
    
    /**
     * Clean up existing FixSlide entities in the world to prevent duplicates
     */
    private void cleanupExistingFixSlideEntities() {
        LOGGER.info("Cleaning up existing FixSlide entities to prevent duplicates...");
        
        for (MenuZone zone : configManager.getAllMenuZones().values()) {
            if (zone.hasMenuType() && zone.getMenuType() == MenuType.FIXSLIDE) {
                // Clean up button locations
                Location nextLoc = zone.getNextButtonLocation();
                Location backLoc = zone.getBackButtonLocation();
                Location renderLoc = zone.getFixSlideRenderLocation();
                
                if (nextLoc != null) {
                    cleanupEntitiesAtLocation(nextLoc, "Next button");
                }
                
                if (backLoc != null) {
                    cleanupEntitiesAtLocation(backLoc, "Back button");
                }
                
                if (renderLoc != null) {
                    // Clean up screen area (16x11 blocks)
                    for (int x = -8; x <= 8; x++) {
                        for (int y = -5; y <= 5; y++) {
                            Location screenLoc = renderLoc.clone().add(x, y, 0);
                            cleanupEntitiesAtLocation(screenLoc, "Screen frame");
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Clean up entities at a specific location
     */
    private void cleanupEntitiesAtLocation(Location loc, String entityType) {
        if (loc == null || loc.getWorld() == null) return;
        
        loc.getWorld().getNearbyEntities(loc, 1.5, 1.5, 1.5).forEach(entity -> {
            if (entity instanceof org.bukkit.entity.ItemFrame || entity instanceof org.bukkit.entity.ArmorStand) {
                entity.remove();
            }
        });
    }

    /**
     * Initialize a single FIXSLIDE zone
     */
    public void initializeFixSlide(MenuZone zone) {
        if (!zone.hasMenuType() || zone.getMenuType() != MenuType.FIXSLIDE) {
            return;
        }
        
        if (zone.isDisabled()) {
            LOGGER.info("Skipping disabled FIXSLIDE zone: " + zone.getName());
            return;
        }
        
        String linkedZone = zone.getLinkedSlideZone();
        if (linkedZone == null) {
            LOGGER.warning("FIXSLIDE zone '" + zone.getName() + "' has no linked SLIDE zone");
            return;
        }
        
        // Get slides from linked zone
        List<Slide> slides = slideManager.getSlides(linkedZone);
        if (slides.isEmpty()) {
            LOGGER.info("FIXSLIDE zone '" + zone.getName() + "' has no slides (linked zone: " + linkedZone + ")");
            return;
        }
        
        // Initialize at first slide
        currentSlideIndex.put(zone.getName(), 0);
        
        // Render first slide (will process if needed)
        Slide firstSlide = slides.get(0);
        LOGGER.info("Initializing FIXSLIDE '" + zone.getName() + "' with slide 1/" + slides.size());
        renderFixSlide(zone, firstSlide);
        
        // Create navigation buttons
        createFixSlideButtons(zone);
        
        LOGGER.info("Initialized FIXSLIDE zone: " + zone.getName() + " (" + slides.size() + " slides)");
    }
    
    /**
     * Render a slide for a FIXSLIDE zone
     */
    private void renderFixSlide(MenuZone zone, Slide slide) {
        // Remove existing screen
        removeFixSlideScreen(zone.getName());
        
        if (!slide.hasMapIds()) {
            // Check if already being processed
            Set<Integer> processing = processingSlides.get(zone.getName());
            if (processing != null && processing.contains(slide.getSlideNumber())) {
                LOGGER.fine("Slide " + slide.getSlideNumber() + " for FIXSLIDE '" + zone.getName() + "' is already being processed, skipping...");
                return;
            }
            
            // Check if we're at processing limit to prevent OutOfMemoryError
            int totalProcessing = processingSlides.values().stream()
                .mapToInt(Set::size)
                .sum();
            
            if (totalProcessing >= MAX_CONCURRENT_PROCESSING) {
                LOGGER.warning("Processing limit reached (" + totalProcessing + "/" + MAX_CONCURRENT_PROCESSING + "). Slide " + 
                    slide.getSlideNumber() + " for FIXSLIDE '" + zone.getName() + "' will be processed later.");
                LOGGER.warning("Increase server RAM or reduce image sizes to process more slides concurrently.");
                return;
            }
            
            LOGGER.warning("Slide " + slide.getSlideNumber() + " for FIXSLIDE '" + zone.getName() + "' not processed yet");
            LOGGER.info("Processing slide " + slide.getSlideNumber() + " now... (" + (totalProcessing + 1) + "/" + MAX_CONCURRENT_PROCESSING + ")");
            
            // Mark as processing
            processingSlides.computeIfAbsent(zone.getName(), k -> new HashSet<>()).add(slide.getSlideNumber());
            
            // Process the slide asynchronously
            processSlideForFixSlide(zone, slide);
            return;
        }
        
        // Get map items
        List<ItemStack> mapItems = com.seminario.plugin.util.SlideMapRenderer4x4.createMapItemsFromIds(slide.getMapIds());
        if (mapItems.size() != 176) {
            LOGGER.warning("Expected 176 maps, got " + mapItems.size() + " for FIXSLIDE " + zone.getName());
            return;
        }
        
        try {
            // Create screen at fixed location with fixed direction
            Location renderLoc = zone.getFixSlideRenderLocation();
            String direction = zone.getFixSlideDirection();
            
            if (renderLoc == null) {
                LOGGER.warning("FIXSLIDE '" + zone.getName() + "' has no render location set");
                return;
            }
            
            List<ItemFrame> frames = com.seminario.plugin.util.SlideScreenRenderer.createFixSlideScreen(
                renderLoc, mapItems, direction);
            
            activeScreens.put(zone.getName(), frames);
            
            // Get players within 3 chunks of the FIXSLIDE location
            List<Player> nearbyPlayers = getPlayersNearby(renderLoc, 3);
            
            if (nearbyPlayers.isEmpty()) {
                LOGGER.info("No players nearby to render maps for FIXSLIDE " + zone.getName());
            } else {
                // Render maps for each nearby player individually
                // Wait a few ticks for clients to load the frames first
                org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    for (Player nearbyPlayer : nearbyPlayers) {
                        if (nearbyPlayer.isOnline()) {
                            for (ItemFrame frame : frames) {
                                if (frame != null && frame.isValid()) {
                                    ItemStack mapItem = frame.getItem();
                                    if (mapItem != null && mapItem.getType() == org.bukkit.Material.FILLED_MAP) {
                                        org.bukkit.inventory.meta.MapMeta mapMeta = (org.bukkit.inventory.meta.MapMeta) mapItem.getItemMeta();
                                        if (mapMeta != null && mapMeta.getMapView() != null) {
                                            nearbyPlayer.sendMap(mapMeta.getMapView());
                                        }
                                    }
                                }
                            }
                        }
                    }
                    LOGGER.info("Rendered " + frames.size() + " maps for " + nearbyPlayers.size() + " nearby players (renderFixSlide)");
                }, 5L);
            }
            
            LOGGER.info("Rendered slide " + slide.getSlideNumber() + " for FIXSLIDE " + zone.getName());
        } catch (Exception e) {
            LOGGER.severe("Error rendering FIXSLIDE " + zone.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Process a slide image asynchronously for FIXSLIDE
     * Optimizado para liberar memoria progresivamente durante el procesamiento
     */
    private void processSlideForFixSlide(MenuZone zone, Slide slide) {
        String cacheFilename = imageDownloader.generateCacheFilename(slide.getUrl(), slide.getSlideNumber());
        
        // Get the linked SLIDE zone name to use as folder name
        String linkedZoneName = zone.getLinkedSlideZone();
        
        java.util.concurrent.CompletableFuture<java.awt.image.BufferedImage> imageFuture = 
            imageDownloader.downloadAndProcessImage(slide.getUrl(), cacheFilename, linkedZoneName, slide.getSlideNumber());
        
        imageFuture.thenAccept(downloadedImage -> {
            // Run on main thread
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    LOGGER.info("Processing slide " + slide.getSlideNumber() + " for FIXSLIDE " + zone.getName() + "...");
                    
                    // Split image into 16x11 segments (esto ya libera downloadedImage internamente)
                    java.awt.image.BufferedImage[] segments = imageDownloader.splitImageFor16x11Maps(downloadedImage);
                    
                    // Liberar downloadedImage inmediatamente después de dividir
                    downloadedImage.flush();
                    
                    // Create map items (esto ya libera cada segmento internamente)
                    List<ItemStack> mapItems = com.seminario.plugin.util.SlideMapRenderer4x4.createMapItemsFor16x11Display(segments);
                    
                    // Liberar array de segmentos
                    for (int i = 0; i < segments.length; i++) {
                        if (segments[i] != null) {
                            segments[i].flush();
                        }
                    }
                    
                    // Store map IDs in slide
                    List<Integer> mapIds = com.seminario.plugin.util.SlideMapRenderer4x4.getMapIds(mapItems);
                    slide.setMapIds(mapIds);
                    slide.setCachedImagePath(cacheFilename);
                    slide.updateTimestamp();
                    
                    // Save updated slide info
                    slideManager.saveSlides();
                    
                    LOGGER.info("Slide " + slide.getSlideNumber() + " processed successfully for FIXSLIDE " + zone.getName() + " (memory optimized)");
                    
                    // Remove from processing set
                    Set<Integer> processing = processingSlides.get(zone.getName());
                    if (processing != null) {
                        processing.remove(slide.getSlideNumber());
                        if (processing.isEmpty()) {
                            processingSlides.remove(zone.getName());
                        }
                    }
                    
                    // Sugerir garbage collection después de procesar slide completo
                    System.gc();
                    
                    // Now render the slide directly (don't call renderFixSlide recursively to avoid loop)
                    try {
                        List<ItemStack> renderedMaps = com.seminario.plugin.util.SlideMapRenderer4x4.createMapItemsFromIds(slide.getMapIds());
                        if (renderedMaps.size() != 176) {
                            LOGGER.warning("Expected 176 maps, got " + renderedMaps.size() + " for FIXSLIDE " + zone.getName());
                            return;
                        }
                        
                        Location renderLoc = zone.getFixSlideRenderLocation();
                        String direction = zone.getFixSlideDirection();
                        
                        if (renderLoc == null) {
                            LOGGER.warning("FIXSLIDE '" + zone.getName() + "' has no render location set");
                            return;
                        }
                        
                        List<ItemFrame> frames = com.seminario.plugin.util.SlideScreenRenderer.createFixSlideScreen(
                            renderLoc, renderedMaps, direction);
                        
                        activeScreens.put(zone.getName(), frames);
                        
                        // Get players within 3 chunks of the FIXSLIDE location
                        List<Player> nearbyPlayers = getPlayersNearby(renderLoc, 3);
                        
                        if (nearbyPlayers.isEmpty()) {
                            LOGGER.info("No players nearby to render maps for FIXSLIDE " + zone.getName());
                        } else {
                            // Render maps for each nearby player individually
                            // Wait a few ticks for clients to load the frames first
                            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                for (Player nearbyPlayer : nearbyPlayers) {
                                    if (nearbyPlayer.isOnline()) {
                                        for (ItemFrame frame : frames) {
                                            if (frame != null && frame.isValid()) {
                                                ItemStack mapItem = frame.getItem();
                                                if (mapItem != null && mapItem.getType() == org.bukkit.Material.FILLED_MAP) {
                                                    org.bukkit.inventory.meta.MapMeta mapMeta = (org.bukkit.inventory.meta.MapMeta) mapItem.getItemMeta();
                                                    if (mapMeta != null && mapMeta.getMapView() != null) {
                                                        nearbyPlayer.sendMap(mapMeta.getMapView());
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                LOGGER.info("Rendered " + frames.size() + " maps for " + nearbyPlayers.size() + " nearby players (processSlideForFixSlide)");
                            }, 5L);
                        }
                        
                        LOGGER.info("Rendered slide " + slide.getSlideNumber() + " for FIXSLIDE " + zone.getName());
                    } catch (Exception renderEx) {
                        LOGGER.severe("Error rendering processed FIXSLIDE: " + renderEx.getMessage());
                        renderEx.printStackTrace();
                    }
                    
                } catch (OutOfMemoryError oom) {
                    LOGGER.severe("========================================");
                    LOGGER.severe("OUT OF MEMORY ERROR!");
                    LOGGER.severe("Failed to process slide " + slide.getSlideNumber() + " for FIXSLIDE " + zone.getName());
                    LOGGER.severe("The image is too large or server has insufficient RAM.");
                    LOGGER.severe("========================================");
                    oom.printStackTrace();
                    
                    // Clean up processing flag
                    Set<Integer> processing = processingSlides.get(zone.getName());
                    if (processing != null) {
                        processing.remove(slide.getSlideNumber());
                        if (processing.isEmpty()) {
                            processingSlides.remove(zone.getName());
                        }
                    }
                    
                    // Force garbage collection to try to recover
                    System.gc();
                    
                    // Notify online admins
                    for (Player onlinePlayer : org.bukkit.Bukkit.getOnlinePlayers()) {
                        if (onlinePlayer.isOp()) {
                            onlinePlayer.sendMessage(Component.text("⚠ ERROR: No hay suficiente RAM para procesar slide " + 
                                slide.getSlideNumber() + " de FIXSLIDE '" + zone.getName() + "'", NamedTextColor.RED));
                            onlinePlayer.sendMessage(Component.text("Considera aumentar la memoria del servidor o usar imágenes más pequeñas.", NamedTextColor.YELLOW));
                        }
                    }
                    
                } catch (Exception e) {
                    LOGGER.severe("Error processing slide image for FIXSLIDE: " + e.getMessage());
                    e.printStackTrace();
                    
                    // Remove from processing set on error
                    Set<Integer> processing = processingSlides.get(zone.getName());
                    if (processing != null) {
                        processing.remove(slide.getSlideNumber());
                        if (processing.isEmpty()) {
                            processingSlides.remove(zone.getName());
                        }
                    }
                }
            });
        }).exceptionally(throwable -> {
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                LOGGER.warning("Failed to download image for FIXSLIDE slide " + slide.getSlideNumber() + ": " + throwable.getMessage());
                
                // Remove from processing set on error
                Set<Integer> processing = processingSlides.get(zone.getName());
                if (processing != null) {
                    processing.remove(slide.getSlideNumber());
                    if (processing.isEmpty()) {
                        processingSlides.remove(zone.getName());
                    }
                }
            });
            return null;
        });
    }
    
    /**
     * Create navigation buttons for FIXSLIDE
     */
    private void createFixSlideButtons(MenuZone zone) {
        // Remove existing buttons
        removeFixSlideButtons(zone.getName());
        
        Location nextLoc = zone.getNextButtonLocation();
        Location backLoc = zone.getBackButtonLocation();
        
        if (nextLoc != null) {
            ItemFrame nextButton = createButton(nextLoc, Material.LIME_CONCRETE, true);
            nextButtons.put(zone.getName(), nextButton);
            
            // Make button visible to all online players
            for (Player onlinePlayer : org.bukkit.Bukkit.getOnlinePlayers()) {
                onlinePlayer.showEntity(plugin, nextButton);
            }
            
            ArmorStand nextHolo = createHologram(nextLoc.clone().add(0, BUTTON_SIZE + 0.3, 0), "Siguiente →");
            nextHolograms.put(zone.getName(), nextHolo);
            
            // Make hologram visible to all online players
            for (Player onlinePlayer : org.bukkit.Bukkit.getOnlinePlayers()) {
                onlinePlayer.showEntity(plugin, nextHolo);
            }
        }
        
        if (backLoc != null) {
            ItemFrame backButton = createButton(backLoc, Material.RED_CONCRETE, false);
            backButtons.put(zone.getName(), backButton);
            
            // Make button visible to all online players
            for (Player onlinePlayer : org.bukkit.Bukkit.getOnlinePlayers()) {
                onlinePlayer.showEntity(plugin, backButton);
            }
            
            ArmorStand backHolo = createHologram(backLoc.clone().add(0, BUTTON_SIZE + 0.3, 0), "← Anterior");
            backHolograms.put(zone.getName(), backHolo);
            
            // Make hologram visible to all online players
            for (Player onlinePlayer : org.bukkit.Bukkit.getOnlinePlayers()) {
                onlinePlayer.showEntity(plugin, backHolo);
            }
        }
    }
    
    /**
     * Create a button item frame
     */
    private ItemFrame createButton(Location loc, Material material, boolean isNext) {
        // Spawn item frame
        ItemFrame frame = loc.getWorld().spawn(loc, ItemFrame.class);
        frame.setItem(new ItemStack(material));
        frame.setVisible(false);
        frame.setFixed(true);
        frame.setPersistent(true);
        
        return frame;
    }
    
    /**
     * Create a hologram armor stand
     */
    private ArmorStand createHologram(Location loc, String text) {
        ArmorStand stand = loc.getWorld().spawn(loc, ArmorStand.class);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setMarker(true);
        stand.setCustomNameVisible(true);
        stand.customName(Component.text(text, NamedTextColor.YELLOW));
        stand.setPersistent(true);
        
        return stand;
    }
    
    /**
     * Handle next slide for a FIXSLIDE zone
     */
    public boolean nextSlide(String zoneName) {
        MenuZone zone = configManager.getMenuZone(zoneName);
        if (zone == null || zone.getMenuType() != MenuType.FIXSLIDE) {
            return false;
        }
        
        String linkedZone = zone.getLinkedSlideZone();
        List<Slide> slides = slideManager.getSlides(linkedZone);
        
        if (slides.isEmpty()) {
            return false;
        }
        
        int currentIndex = currentSlideIndex.getOrDefault(zoneName, 0);
        if (currentIndex >= slides.size() - 1) {
            LOGGER.info("Already at last slide for FIXSLIDE " + zoneName);
            return false;
        }
        
        int newIndex = currentIndex + 1;
        currentSlideIndex.put(zoneName, newIndex);
        renderFixSlide(zone, slides.get(newIndex));
        
        // Wait for renderFixSlide to complete, then sync maps to all players
        // (renderFixSlide already has its own delay, but this ensures navigation completes)
        
        LOGGER.info("FIXSLIDE " + zoneName + " advanced to slide " + (newIndex + 1) + "/" + slides.size());
        return true;
    }
    
    /**
     * Handle previous slide for a FIXSLIDE zone
     */
    public boolean previousSlide(String zoneName) {
        MenuZone zone = configManager.getMenuZone(zoneName);
        if (zone == null || zone.getMenuType() != MenuType.FIXSLIDE) {
            return false;
        }
        
        String linkedZone = zone.getLinkedSlideZone();
        List<Slide> slides = slideManager.getSlides(linkedZone);
        
        if (slides.isEmpty()) {
            return false;
        }
        
        int currentIndex = currentSlideIndex.getOrDefault(zoneName, 0);
        if (currentIndex <= 0) {
            LOGGER.info("Already at first slide for FIXSLIDE " + zoneName);
            return false;
        }
        
        int newIndex = currentIndex - 1;
        currentSlideIndex.put(zoneName, newIndex);
        renderFixSlide(zone, slides.get(newIndex));
        
        // Wait for renderFixSlide to complete, then sync maps to all players
        // (renderFixSlide already has its own delay, but this ensures navigation completes)
        
        LOGGER.info("FIXSLIDE " + zoneName + " went back to slide " + (newIndex + 1) + "/" + slides.size());
        return true;
    }
    
    /**
     * Handle button click for FIXSLIDE
     */
    public boolean handleButtonClick(ItemFrame clickedFrame, Player player) {
        // Find which FIXSLIDE zone this button belongs to
        for (Map.Entry<String, ItemFrame> entry : nextButtons.entrySet()) {
            if (entry.getValue().equals(clickedFrame)) {
                return nextSlide(entry.getKey());
            }
        }
        
        for (Map.Entry<String, ItemFrame> entry : backButtons.entrySet()) {
            if (entry.getValue().equals(clickedFrame)) {
                return previousSlide(entry.getKey());
            }
        }
        
        return false;
    }
    
    /**
     * Refresh a FIXSLIDE zone (when slides are updated)
     */
    public void refreshFixSlide(String zoneName) {
        MenuZone zone = configManager.getMenuZone(zoneName);
        if (zone == null) {
            return;
        }
        
        removeFixSlide(zoneName);
        initializeFixSlide(zone);
    }
    
    /**
     * Refresh only the buttons for a FIXSLIDE zone (optimized - doesn't re-render screen)
     */
    public void refreshButtons(String zoneName) {
        MenuZone zone = configManager.getMenuZone(zoneName);
        if (zone == null) {
            return;
        }
        
        // Only recreate buttons, don't touch the screen
        removeFixSlideButtons(zoneName);
        createFixSlideButtons(zone);
        
        LOGGER.info("Refreshed buttons for FIXSLIDE zone: " + zoneName);
    }
    
    /**
     * Remove a FIXSLIDE zone completely
     */
    public void removeFixSlide(String zoneName) {
        removeFixSlideScreen(zoneName);
        removeFixSlideButtons(zoneName);
        currentSlideIndex.remove(zoneName);
    }
    
    /**
     * Get players within specified chunk radius of a location
     */
    private List<Player> getPlayersNearby(Location location, int chunkRadius) {
        List<Player> nearbyPlayers = new java.util.ArrayList<>();
        if (location == null || location.getWorld() == null) {
            return nearbyPlayers;
        }
        
        int centerChunkX = location.getChunk().getX();
        int centerChunkZ = location.getChunk().getZ();
        
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(location.getWorld())) {
                int playerChunkX = player.getLocation().getChunk().getX();
                int playerChunkZ = player.getLocation().getChunk().getZ();
                
                int chunkDistanceX = Math.abs(playerChunkX - centerChunkX);
                int chunkDistanceZ = Math.abs(playerChunkZ - centerChunkZ);
                
                if (chunkDistanceX <= chunkRadius && chunkDistanceZ <= chunkRadius) {
                    nearbyPlayers.add(player);
                }
            }
        }
        
        return nearbyPlayers;
    }
    
    /**
     * Remove the screen for a FIXSLIDE zone
     */
    private void removeFixSlideScreen(String zoneName) {
        List<ItemFrame> frames = activeScreens.remove(zoneName);
        if (frames != null) {
            // Remove frames from server (they're naturally visible, no need to hide first)
            for (ItemFrame frame : frames) {
                if (frame != null && frame.isValid()) {
                    frame.remove();
                }
            }
        }
    }
    
    /**
     * Remove buttons for a FIXSLIDE zone
     */
    private void removeFixSlideButtons(String zoneName) {
        ItemFrame nextBtn = nextButtons.remove(zoneName);
        if (nextBtn != null && nextBtn.isValid()) {
            nextBtn.remove();
        }
        
        ItemFrame backBtn = backButtons.remove(zoneName);
        if (backBtn != null && backBtn.isValid()) {
            backBtn.remove();
        }
        
        ArmorStand nextHolo = nextHolograms.remove(zoneName);
        if (nextHolo != null && nextHolo.isValid()) {
            nextHolo.remove();
        }
        
        ArmorStand backHolo = backHolograms.remove(zoneName);
        if (backHolo != null && backHolo.isValid()) {
            backHolo.remove();
        }
    }
    
    /**
     * Cleanup all FIXSLIDE zones
     */
    public void cleanupAll() {
        LOGGER.info("Cleaning up all FIXSLIDE zones...");
        
        for (String zoneName : activeScreens.keySet()) {
            removeFixSlide(zoneName);
        }
        
        LOGGER.info("FIXSLIDE cleanup complete");
    }
    
    /**
     * Get number of active FIXSLIDE zones
     */
    public int getActiveFixSlideCount() {
        return activeScreens.size();
    }
    
    /**
     * Send map data to player for all existing FIXSLIDE frames
     * Called when a player joins to ensure they receive map rendering data
     */
    public void showAllFramesToPlayer(Player player) {
        int totalFrames = 0;
        int totalMaps = 0;
        int zonesRendered = 0;
        
        // Send map data for FIXSLIDE zones that are within 3 chunks of the player
        for (String zoneName : activeScreens.keySet()) {
            MenuZone zone = configManager.getMenuZone(zoneName);
            if (zone != null && zone.getFixSlideRenderLocation() != null) {
                Location renderLoc = zone.getFixSlideRenderLocation();
                
                // Check if player is within 3 chunks of this FIXSLIDE
                if (player.getWorld().equals(renderLoc.getWorld())) {
                    int playerChunkX = player.getLocation().getChunk().getX();
                    int playerChunkZ = player.getLocation().getChunk().getZ();
                    int zoneChunkX = renderLoc.getChunk().getX();
                    int zoneChunkZ = renderLoc.getChunk().getZ();
                    
                    int chunkDistanceX = Math.abs(playerChunkX - zoneChunkX);
                    int chunkDistanceZ = Math.abs(playerChunkZ - zoneChunkZ);
                    
                    if (chunkDistanceX <= 3 && chunkDistanceZ <= 3) {
                        // Player is nearby, send maps
                        List<ItemFrame> frames = activeScreens.get(zoneName);
                        if (frames != null) {
                            for (ItemFrame frame : frames) {
                                if (frame != null && frame.isValid()) {
                                    totalFrames++;
                                    
                                    ItemStack mapItem = frame.getItem();
                                    if (mapItem != null && mapItem.getType() == org.bukkit.Material.FILLED_MAP) {
                                        frame.setItem(mapItem.clone(), false);
                                        totalMaps++;
                                        
                                        org.bukkit.inventory.meta.MapMeta mapMeta = (org.bukkit.inventory.meta.MapMeta) mapItem.getItemMeta();
                                        if (mapMeta != null && mapMeta.getMapView() != null) {
                                            player.sendMap(mapMeta.getMapView());
                                        }
                                    }
                                }
                            }
                            zonesRendered++;
                        }
                    }
                }
            }
        }
        
        if (totalMaps > 0) {
            LOGGER.info("Sent " + totalMaps + " map updates to player " + player.getName() + 
                " (" + zonesRendered + " FIXSLIDE zones, " + totalFrames + " frames)");
        }
    }
}
