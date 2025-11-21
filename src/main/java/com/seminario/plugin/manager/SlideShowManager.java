package com.seminario.plugin.manager;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import com.seminario.plugin.config.ConfigManager;
import com.seminario.plugin.model.MenuType;
import com.seminario.plugin.model.MenuZone;
import com.seminario.plugin.model.PlayerSlideSession;
import com.seminario.plugin.model.Slide;
import com.seminario.plugin.util.ImageDownloader;
import com.seminario.plugin.util.SlideScreenRenderer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Manages active slideshow presentations for players
 * Handles session creation, slide navigation, and cleanup
 */
public class SlideShowManager {
    
    private static final Logger LOGGER = Logger.getLogger(SlideShowManager.class.getName());
    private static final int MAX_CONCURRENT_PROCESSING = 2; // Maximum slides being processed at once
    
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final SlideManager slideManager;
    private final ImageDownloader imageDownloader;
    
    // Active sessions: playerId -> PlayerSlideSession
    private final Map<UUID, PlayerSlideSession> activeSessions;
    
    // Zone sessions: zoneName -> Set of player UUIDs
    private final Map<String, Map<UUID, PlayerSlideSession>> zoneSessions;
    
    // Track slides being processed: zoneName+slideNumber -> boolean
    private final Map<String, Set<Integer>> processingSlides;
    
    public SlideShowManager(JavaPlugin plugin, ConfigManager configManager, SlideManager slideManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.slideManager = slideManager;
        this.imageDownloader = new ImageDownloader(plugin);
        this.activeSessions = new HashMap<>();
        this.zoneSessions = new HashMap<>();
        this.processingSlides = new HashMap<>();
    }
    
    /**
     * Start a slideshow session for a player in a zone
     * @param player The player
     * @param zoneName The zone name
     * @return true if session started successfully
     */
    public boolean startSlideshow(Player player, String zoneName) {
        // Verify zone exists and is slide type
        MenuZone zone = configManager.getMenuZone(zoneName);
        if (zone == null) {
            LOGGER.warning("Attempted to start slideshow in non-existent zone: " + zoneName);
            return false;
        }
        
        if (!zone.hasMenuType() || zone.getMenuType() != MenuType.SLIDE) {
            player.sendMessage(Component.text("Esta zona no está configurada para presentaciones.", NamedTextColor.RED));
            return false;
        }
        
        // Get slides for the zone
        List<Slide> slides = slideManager.getSlides(zoneName);
        if (slides.isEmpty()) {
            player.sendMessage(Component.text("Esta zona no tiene slides configurados.", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Los administradores pueden agregar slides con: /sm slide " + zoneName + " add <url>", NamedTextColor.GRAY));
            return false;
        }
        
        // Check if player already has an active session
        if (activeSessions.containsKey(player.getUniqueId())) {
            stopSlideshow(player);
        }
        
        // Create new session
        PlayerSlideSession session = new PlayerSlideSession(player, zoneName, slides);
        session.setActive(true);
        
        // Store session
        activeSessions.put(player.getUniqueId(), session);
        zoneSessions.computeIfAbsent(zoneName, k -> new HashMap<>()).put(player.getUniqueId(), session);
        
        // Show first slide
        showCurrentSlide(player, session);
        
        // Send welcome message
        player.sendMessage(Component.text("¡Presentación iniciada!", NamedTextColor.GREEN));
        player.sendMessage(Component.text("Zona: " + zoneName, NamedTextColor.GRAY));
        player.sendMessage(Component.text(session.getProgressString(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("Usa los botones a los lados para navegar", NamedTextColor.YELLOW));
        
        LOGGER.info("Started slideshow for " + player.getName() + " in zone " + zoneName + " (" + slides.size() + " slides)");
        return true;
    }
    
    /**
     * Stop slideshow session for a player
     * @param player The player
     * @return true if session was stopped
     */
    public boolean stopSlideshow(Player player) {
        PlayerSlideSession session = activeSessions.remove(player.getUniqueId());
        if (session == null) {
            return false;
        }
        
        // Remove from zone sessions
        Map<UUID, PlayerSlideSession> zoneSessionMap = zoneSessions.get(session.getZoneName());
        if (zoneSessionMap != null) {
            zoneSessionMap.remove(player.getUniqueId());
            if (zoneSessionMap.isEmpty()) {
                zoneSessions.remove(session.getZoneName());
            }
        }
        
        // Remove slide screen
        SlideScreenRenderer.removeSlideScreen(player);
        
        // Restore original inventory
        restorePlayerInventory(player, session);
        
        // Send farewell message
        player.sendMessage(Component.text("Presentación finalizada.", NamedTextColor.GRAY));
        
        LOGGER.info("Stopped slideshow for " + player.getName() + " in zone " + session.getZoneName());
        return true;
    }
    
    /**
     * Navigate to next slide for a player
     * @param player The player
     * @return true if advanced to next slide
     */
    public boolean nextSlide(Player player) {
        PlayerSlideSession session = activeSessions.get(player.getUniqueId());
        if (session == null || !session.isActive()) {
            return false;
        }
        
        // Block navigation if slide is being processed
        if (session.isProcessingSlide()) {
            player.sendMessage(Component.text("Espera a que termine de procesarse la diapositiva actual...", NamedTextColor.YELLOW));
            return false;
        }
        
        if (session.nextSlide()) {
            showCurrentSlide(player, session);
            player.sendMessage(Component.text(session.getProgressString(), NamedTextColor.GRAY));
            return true;
        } else {
            // Reached end of presentation
            player.sendMessage(Component.text("¡Fin de la presentación!", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Sal de la zona para finalizar o usa el botón rojo para retroceder.", NamedTextColor.GRAY));
            return false;
        }
    }
    
    /**
     * Navigate to previous slide for a player
     * @param player The player  
     * @return true if went to previous slide
     */
    public boolean previousSlide(Player player) {
        PlayerSlideSession session = activeSessions.get(player.getUniqueId());
        if (session == null || !session.isActive()) {
            return false;
        }
        
        // Block navigation if slide is being processed
        if (session.isProcessingSlide()) {
            player.sendMessage(Component.text("Espera a que termine de procesarse la diapositiva actual...", NamedTextColor.YELLOW));
            return false;
        }
        
        if (session.previousSlide()) {
            showCurrentSlide(player, session);
            player.sendMessage(Component.text(session.getProgressString(), NamedTextColor.GRAY));
            return true;
        } else {
            player.sendMessage(Component.text("Ya estás en el primer slide.", NamedTextColor.YELLOW));
            return false;
        }
    }
    
    /**
     * Show the current slide to a player
     * @param player The player
     * @param session The slide session
     */
    private void showCurrentSlide(Player player, PlayerSlideSession session) {
        Slide currentSlide = session.getCurrentSlide();
        if (currentSlide == null) {
            player.sendMessage(Component.text("Error: No se pudo cargar el slide actual.", NamedTextColor.RED));
            return;
        }
        
        // Check if slide has processed maps
        if (!currentSlide.hasMapIds()) {
            // Need to process image
            processSlideImage(player, session, currentSlide);
            return;
        }
        
        // Create map items from existing map IDs
        LOGGER.info("Creating map items from " + currentSlide.getMapIds().size() + " map IDs for player " + player.getName());
        List<ItemStack> mapItems = com.seminario.plugin.util.SlideMapRenderer4x4.createMapItemsFromIds(currentSlide.getMapIds());
        LOGGER.info("Created " + mapItems.size() + " map items for player " + player.getName());
        
        if (mapItems.size() != 176) {
            player.sendMessage(Component.text("Error: No se pudieron cargar los mapas del slide (se esperaban 176, se obtuvieron " + mapItems.size() + ").", NamedTextColor.RED));
            player.sendMessage(Component.text("Regenerando slide...", NamedTextColor.YELLOW));
            // Clear the old map IDs and reprocess
            currentSlide.clearMapIds();
            processSlideImage(player, session, currentSlide);
            return;
        }
        
        // Create or update slide screen
        try {
            // Get fixed direction and position from zone if available
            MenuZone zone = configManager.getMenuZone(session.getZoneName());
            String fixedDirection = (zone != null && zone.hasSlideDirection()) ? zone.getSlideDirection() : null;
            Location fixedLocation = (zone != null && zone.hasSlideFixedLocation()) ? zone.getSlideFixedLocation() : null;
            
            if (SlideScreenRenderer.hasActiveScreen(player)) {
                boolean updated = SlideScreenRenderer.updateSlideScreen(player, mapItems);
                if (!updated) {
                    LOGGER.warning("Failed to update slide screen for " + player.getName() + ", recreating...");
                    SlideScreenRenderer.removeSlideScreen(player);
                    SlideScreenRenderer.createSlideScreen(player, mapItems, fixedDirection, fixedLocation);
                }
            } else {
                SlideScreenRenderer.createSlideScreen(player, mapItems, fixedDirection, fixedLocation);
            }
        } catch (Exception e) {
            player.sendMessage(Component.text("Error al mostrar slide: " + e.getMessage(), NamedTextColor.RED));
            LOGGER.severe("Error creating slide screen for " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
            return;
        }
        
        // Send slide info
        player.sendMessage(Component.text("Slide " + session.getCurrentSlideNumber() + "/" + session.getTotalSlides(), NamedTextColor.GREEN));
        if (currentSlide.getUrl() != null) {
            player.sendMessage(Component.text("URL: " + currentSlide.getUrl(), NamedTextColor.GRAY));
        }
    }
    
    /**
     * Process slide image asynchronously
     * @param player The player
     * @param session The slide session
     * @param slide The slide to process
     */
    private void processSlideImage(Player player, PlayerSlideSession session, Slide slide) {
        final String zoneName = session.getZoneName();
        final int slideNumber = slide.getSlideNumber();
        
        // Check if already being processed
        Set<Integer> processing = processingSlides.get(zoneName);
        if (processing != null && processing.contains(slideNumber)) {
            LOGGER.fine("Slide " + slideNumber + " for zone '" + zoneName + "' is already being processed, skipping...");
            return;
        }
        
        // Check if we're at processing limit to prevent OutOfMemoryError
        int totalProcessing = processingSlides.values().stream()
            .mapToInt(Set::size)
            .sum();
        
        if (totalProcessing >= MAX_CONCURRENT_PROCESSING) {
            player.sendMessage(Component.text("⚠ Límite de procesamiento alcanzado (" + totalProcessing + "/" + MAX_CONCURRENT_PROCESSING + ")", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Por favor espera a que termine de procesarse otro slide.", NamedTextColor.GRAY));
            return;
        }
        
        // Mark session as processing
        session.setProcessingSlide(true);
        
        // Mark as processing
        processingSlides.computeIfAbsent(zoneName, k -> new HashSet<>()).add(slideNumber);
        
        player.sendMessage(Component.text("Procesando imagen del slide " + slide.getSlideNumber() + "... (" + (totalProcessing + 1) + "/" + MAX_CONCURRENT_PROCESSING + ")", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Por favor espera, no puedes navegar hasta que termine.", NamedTextColor.GRAY));
        
        String cacheFilename = imageDownloader.generateCacheFilename(slide.getUrl(), slide.getSlideNumber());
        
        CompletableFuture<BufferedImage> imageFuture = imageDownloader.downloadAndProcessImage(slide.getUrl(), cacheFilename);
        
        imageFuture.thenAccept(processedImage -> {
            // Run on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    // Split image into 16x11 segments
                    BufferedImage[] segments = imageDownloader.splitImageFor16x11Maps(processedImage);
                    
                    // Create map items
                    List<ItemStack> mapItems = com.seminario.plugin.util.SlideMapRenderer4x4.createMapItemsFor16x11Display(segments);
                    
                    // Store map IDs in slide
                    List<Integer> mapIds = com.seminario.plugin.util.SlideMapRenderer4x4.getMapIds(mapItems);
                    slide.setMapIds(mapIds);
                    slide.setCachedImagePath(cacheFilename);
                    slide.updateTimestamp();
                    
                    // Save updated slide info
                    slideManager.saveSlides();
                    
                    // Remove from processing set
                    Set<Integer> proc = processingSlides.get(zoneName);
                    if (proc != null) {
                        proc.remove(slideNumber);
                        if (proc.isEmpty()) {
                            processingSlides.remove(zoneName);
                        }
                    }
                    
                    // Unblock navigation
                    session.setProcessingSlide(false);
                    
                    // Show the slide if player is still in session
                    PlayerSlideSession currentSession = activeSessions.get(player.getUniqueId());
                    if (currentSession != null && currentSession.equals(session)) {
                        if (SlideScreenRenderer.hasActiveScreen(player)) {
                            SlideScreenRenderer.updateSlideScreen(player, mapItems);
                        } else {
                            SlideScreenRenderer.createSlideScreen(player, mapItems);
                        }
                        player.sendMessage(Component.text("¡Slide " + slide.getSlideNumber() + " listo!", NamedTextColor.GREEN));
                    }
                    
                } catch (OutOfMemoryError oom) {
                    LOGGER.severe("========================================");
                    LOGGER.severe("OUT OF MEMORY ERROR!");
                    LOGGER.severe("Failed to process slide " + slide.getSlideNumber() + " for player " + player.getName());
                    LOGGER.severe("The image is too large or server has insufficient RAM.");
                    LOGGER.severe("========================================");
                    oom.printStackTrace();
                    
                    player.sendMessage(Component.text("⚠ ERROR: No hay suficiente RAM para procesar este slide.", NamedTextColor.RED));
                    player.sendMessage(Component.text("La imagen es demasiado grande. Contacta a un administrador.", NamedTextColor.YELLOW));
                    
                    // Clean up processing flag
                    Set<Integer> proc = processingSlides.get(zoneName);
                    if (proc != null) {
                        proc.remove(slideNumber);
                        if (proc.isEmpty()) {
                            processingSlides.remove(zoneName);
                        }
                    }
                    
                    // Unblock navigation
                    session.setProcessingSlide(false);
                    
                    // Force garbage collection
                    System.gc();
                    
                } catch (Exception e) {
                    LOGGER.severe("Error processing slide image: " + e.getMessage());
                    player.sendMessage(Component.text("Error al procesar la imagen del slide.", NamedTextColor.RED));
                    
                    // Remove from processing set on error
                    Set<Integer> proc = processingSlides.get(zoneName);
                    if (proc != null) {
                        proc.remove(slideNumber);
                        if (proc.isEmpty()) {
                            processingSlides.remove(zoneName);
                        }
                    }
                    
                    // Unblock navigation on error
                    session.setProcessingSlide(false);
                }
            });
        }).exceptionally(throwable -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                LOGGER.warning("Failed to download image for slide " + slide.getSlideNumber() + ": " + throwable.getMessage());
                player.sendMessage(Component.text("Error al descargar la imagen del slide.", NamedTextColor.RED));
                player.sendMessage(Component.text("URL: " + slide.getUrl(), NamedTextColor.GRAY));
                
                // Remove from processing set on error
                Set<Integer> proc = processingSlides.get(zoneName);
                if (proc != null) {
                    proc.remove(slideNumber);
                    if (proc.isEmpty()) {
                        processingSlides.remove(zoneName);
                    }
                }
                
                // Unblock navigation on error
                session.setProcessingSlide(false);
            });
            return null;
        });
    }
    
    /**
     * Restore player's original inventory
     * @param player The player
     * @param session The slide session
     */
    private void restorePlayerInventory(Player player, PlayerSlideSession session) {
        ItemStack[] originalHotbar = session.getOriginalHotbar();
        for (int i = 0; i < Math.min(9, originalHotbar.length); i++) {
            player.getInventory().setItem(i, originalHotbar[i]);
        }
    }
    
    /**
     * Check if player has an active slideshow session
     * @param player The player
     * @return true if has active session
     */
    public boolean hasActiveSession(Player player) {
        PlayerSlideSession session = activeSessions.get(player.getUniqueId());
        return session != null && session.isActive();
    }
    
    /**
     * Get active session for a player
     * @param player The player
     * @return PlayerSlideSession or null if none
     */
    public PlayerSlideSession getActiveSession(Player player) {
        return activeSessions.get(player.getUniqueId());
    }
    
    /**
     * Handle player entering a zone
     * @param player The player
     * @param zoneName The zone name
     */
    public void onPlayerEnterZone(Player player, String zoneName) {
        MenuZone zone = configManager.getMenuZone(zoneName);
        if (zone != null && zone.hasMenuType() && zone.getMenuType() == MenuType.SLIDE) {
            if (!hasActiveSession(player)) {
                startSlideshow(player, zoneName);
            }
        }
    }
    
    /**
     * Handle player leaving a zone
     * @param player The player
     * @param zoneName The zone name
     */
    public void onPlayerLeaveZone(Player player, String zoneName) {
        PlayerSlideSession session = activeSessions.get(player.getUniqueId());
        if (session != null && session.getZoneName().equals(zoneName)) {
            stopSlideshow(player);
        }
    }
    
    /**
     * Clean up all sessions for a player (on disconnect)
     * @param player The player
     */
    public void cleanupPlayerSessions(Player player) {
        stopSlideshow(player);
    }
    
    /**
     * Get number of active sessions
     * @return Number of active sessions
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }
    
    /**
     * Get number of active sessions in a zone
     * @param zoneName The zone name
     * @return Number of active sessions in zone
     */
    public int getActiveSessionsInZone(String zoneName) {
        Map<UUID, PlayerSlideSession> zoneSessionMap = zoneSessions.get(zoneName);
        return zoneSessionMap != null ? zoneSessionMap.size() : 0;
    }
}