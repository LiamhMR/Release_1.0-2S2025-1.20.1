package com.seminario.plugin.manager;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;

import com.seminario.plugin.model.Survey;
import com.seminario.plugin.model.SurveySession;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Manages surveys and survey sessions
 */
public class SurveyManager {
    
    private static final Logger logger = Logger.getLogger(SurveyManager.class.getName());
    
    private final JavaPlugin plugin;
    private final Map<String, Survey> surveys;
    private final Map<Player, SurveySession> activeSessions;
    private final File surveysFile;
    private final Map<Integer, ItemStack> faceMaps; // Cache for face map items
    private String defaultSurveyName; // Default survey for post-test
    private Location defaultSurveyLocation; // Location where default survey is conducted
    
    // Face textures for ratings 1-5
    private static final String[] FACE_TEXTURES = {
        "face1.png", // Very sad (1)
        "face2.png", // Sad (2)
        "face3.png", // Neutral (3)
        "face4.png", // Happy (4)
        "face5.png"  // Very happy (5)
    };
    
    public SurveyManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.surveys = new HashMap<>();
        this.activeSessions = new HashMap<>();
        this.surveysFile = new File(plugin.getDataFolder(), "surveys.yml");
        this.faceMaps = new HashMap<>();
        
        loadSurveys();
        loadFaceImages();
        logger.info("SurveyManager initialized with " + surveys.size() + " surveys");
    }
    
    /**
     * Create a new survey
     */
    public boolean createSurvey(String name) {
        if (surveys.containsKey(name.toLowerCase())) {
            return false;
        }
        
        Survey survey = new Survey(name);
        surveys.put(name.toLowerCase(), survey);
        saveSurveys();
        
        logger.info("Created new survey: " + name);
        return true;
    }
    
    /**
     * Get a survey by name
     */
    public Survey getSurvey(String name) {
        return surveys.get(name.toLowerCase());
    }
    
    /**
     * Check if a survey exists
     */
    public boolean surveyExists(String name) {
        return surveys.containsKey(name.toLowerCase());
    }
    
    /**
     * Add a question to a survey
     */
    public boolean addQuestion(String surveyName, String question) {
        Survey survey = getSurvey(surveyName);
        if (survey == null) {
            return false;
        }
        
        survey.addQuestion(question);
        saveSurveys();
        
        logger.info("Added question to survey " + surveyName + ": " + question);
        return true;
    }
    
    /**
     * Edit a question in a survey
     */
    public boolean editQuestion(String surveyName, int questionNumber, String newQuestion) {
        Survey survey = getSurvey(surveyName);
        if (survey == null) {
            return false;
        }
        
        int index = questionNumber - 1; // Convert to 0-based index
        if (index < 0 || index >= survey.getQuestionCount()) {
            return false;
        }
        
        survey.editQuestion(index, newQuestion);
        saveSurveys();
        
        logger.info("Edited question " + questionNumber + " in survey " + surveyName + ": " + newQuestion);
        return true;
    }
    
    /**
     * Start a survey session for a player
     */
    public boolean startSurvey(Player player, String surveyName) {
        Survey survey = getSurvey(surveyName);
        if (survey == null) {
            return false;
        }
        
        if (survey.getQuestionCount() == 0) {
            player.sendMessage(Component.text("Esta encuesta no tiene preguntas.", NamedTextColor.RED));
            return false;
        }
        
        // End any existing session
        endSurvey(player);
        
        // Create new session
        Location baseLocation = player.getLocation().clone().add(3, 0, 0); // 3 blocks in front
        SurveySession session = new SurveySession(player, survey, baseLocation);
        activeSessions.put(player, session);
        
        // Set up the survey display
        setupSurveyDisplay(session);
        
        player.sendMessage(Component.text("¡Encuesta iniciada: " + survey.getName() + "!", NamedTextColor.GREEN));
        player.sendMessage(Component.text("Pregunta 1 de " + survey.getQuestionCount(), NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Selecciona la cara que mejor represente tu respuesta.", NamedTextColor.GRAY));
        
        logger.info("Started survey session for " + player.getName() + ": " + surveyName);
        return true;
    }
    
    /**
     * End a survey session for a player
     */
    public void endSurvey(Player player) {
        SurveySession session = activeSessions.remove(player);
        if (session != null) {
            session.cleanup();
            logger.info("Ended survey session for " + player.getName());
        }
    }
    
    /**
     * Handle player response to a survey question
     */
    public void handleResponse(Player player, int rating) {
        SurveySession session = activeSessions.get(player);
        if (session == null || !session.isActive()) {
            return;
        }
        
        // Add response
        session.addResponse(rating);
        
        // Check if survey is complete
        if (session.isComplete()) {
            completeSurvey(session);
        } else {
            // Move to next question
            session.nextQuestion();
            updateSurveyDisplay(session);
            
            player.sendMessage(Component.text("Pregunta " + (session.getCurrentQuestionIndex() + 1) + 
                                            " de " + session.getTotalQuestions(), NamedTextColor.YELLOW));
        }
    }
    
    /**
     * Complete a survey and save responses
     */
    private void completeSurvey(SurveySession session) {
        Survey survey = session.getSurvey();
        Player player = session.getPlayer();
        
        // Save player responses
        survey.setPlayerResponse(player.getName(), session.getResponses());
        saveSurveys();
        
        // Clean up display
        session.cleanup();
        activeSessions.remove(player);
        
        // Send completion message
        player.sendMessage(Component.text("¡Encuesta completada! Gracias por tu participación.", NamedTextColor.GREEN));
        player.sendMessage(Component.text("Tus respuestas han sido guardadas.", NamedTextColor.GRAY));
        
        // If this was a default survey (post-test), teleport back to spawn
        if (survey.getName().equals(defaultSurveyName)) {
            player.sendMessage(Component.text("Regresando al spawn en 3 segundos...", NamedTextColor.YELLOW));
            
            // Teleport back to spawn after a delay
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    // Get spawn location
                    org.bukkit.Location spawnLocation = player.getWorld().getSpawnLocation();
                    player.teleport(spawnLocation);
                    player.sendMessage(Component.text("¡Bienvenido de vuelta al spawn!", NamedTextColor.GREEN));
                }
            }, 60L); // 3 second delay
        }
        
        logger.info("Completed survey " + survey.getName() + " for player " + player.getName());
    }
    
    /**
     * Set up the visual display for a survey
     */
    private void setupSurveyDisplay(SurveySession session) {
        Location base = session.getBaseLocation().clone();
        
        // Create item frames with faces (1-5 rating scale)
        for (int i = 0; i < 5; i++) {
            Location frameLocation = base.clone().add(i * 2, 1, 0); // 2 blocks apart, 1 block high
            
            // Spawn item frame
            ItemFrame frame = (ItemFrame) frameLocation.getWorld().spawnEntity(frameLocation, EntityType.ITEM_FRAME);
            frame.setFacingDirection(session.getPlayer().getFacing().getOppositeFace()); // Face the player
            
            // Create face item (using maps would be ideal, but we'll use colored items for now)
            ItemStack faceItem = createFaceItem(i + 1);
            frame.setItem(faceItem);
            frame.setFixed(true); // Prevent item removal
            
            session.addFaceFrame(frame);
        }
        
        // Create holographic question display
        createQuestionHologram(session);
    }
    
    /**
     * Update the survey display for the next question
     */
    private void updateSurveyDisplay(SurveySession session) {
        // Remove old hologram
        for (ArmorStand stand : session.getHologramStands()) {
            if (stand != null && stand.isValid()) {
                stand.remove();
            }
        }
        session.getHologramStands().clear();
        
        // Create new hologram for current question
        createQuestionHologram(session);
    }
    
    /**
     * Create hologram with the current question
     */
    private void createQuestionHologram(SurveySession session) {
        String question = session.getCurrentQuestion();
        if (question == null) return;
        
        Location hologramLocation = session.getBaseLocation().clone().add(4, 0, 0); // 2 blocks lower than before
        
        // Create armor stand for hologram
        ArmorStand hologram = (ArmorStand) hologramLocation.getWorld().spawnEntity(hologramLocation, EntityType.ARMOR_STAND);
        hologram.setVisible(false);
        hologram.setGravity(false);
        hologram.setCanPickupItems(false);
        hologram.setCustomNameVisible(true);
        hologram.customName(Component.text(question, NamedTextColor.GOLD));
        
        session.addHologramStand(hologram);
        
        // Add rating scale labels
        String[] ratingLabels = {"😄", "😊", "😐", "😞", "😢"};
        for (int i = 0; i < 5; i++) {
            Location labelLocation = session.getBaseLocation().clone().add(i * 2, 1.8, 0); // Closer to item frames
            ArmorStand label = (ArmorStand) labelLocation.getWorld().spawnEntity(labelLocation, EntityType.ARMOR_STAND);
            label.setVisible(false);
            label.setGravity(false);
            label.setCanPickupItems(false);
            label.setCustomNameVisible(true);
            label.customName(Component.text(ratingLabels[i] + " " + (i + 1), NamedTextColor.WHITE));
            
            session.addHologramStand(label);
        }
    }
    
    /**
     * Create face item for rating using custom face images
     */
    private ItemStack createFaceItem(int rating) {
        // Try to get cached face map
        ItemStack cachedMap = faceMaps.get(rating);
        if (cachedMap != null) {
            return cachedMap.clone();
        }
        
        // Fallback to colored items if map loading failed
        Material[] faceMaterials = {
            Material.RED_CONCRETE,    // Very sad (1)
            Material.ORANGE_CONCRETE, // Sad (2)
            Material.YELLOW_CONCRETE, // Neutral (3)
            Material.LIME_CONCRETE,   // Happy (4)
            Material.GREEN_CONCRETE   // Very happy (5)
        };
        
        return new ItemStack(faceMaterials[rating - 1]);
    }
    
    /**
     * Load face images from resources and create map items
     */
    private void loadFaceImages() {
        logger.info("Loading face images for surveys...");
        
        for (int i = 1; i <= 5; i++) {
            try {
                String imagePath = FACE_TEXTURES[i - 1];
                BufferedImage image = loadImageFromResources(imagePath);
                
                if (image != null) {
                    ItemStack mapItem = createMapFromImage(image, i);
                    if (mapItem != null) {
                        faceMaps.put(i, mapItem);
                        logger.info("Loaded face image " + i + " (" + imagePath + ")");
                    }
                }
            } catch (Exception e) {
                logger.warning("Failed to load face image " + i + ": " + e.getMessage());
            }
        }
        
        logger.info("Loaded " + faceMaps.size() + " face images successfully");
    }
    
    /**
     * Load image from plugin resources
     */
    private BufferedImage loadImageFromResources(String imagePath) {
        try {
            InputStream imageStream = plugin.getResource(imagePath);
            if (imageStream == null) {
                logger.warning("Could not find image resource: " + imagePath);
                return null;
            }
            
            BufferedImage image = ImageIO.read(imageStream);
            imageStream.close();
            
            return image;
        } catch (IOException e) {
            logger.severe("Error loading image " + imagePath + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Create a map item from a BufferedImage
     */
    private ItemStack createMapFromImage(BufferedImage image, int rating) {
        try {
            // Create new map
            ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
            MapMeta mapMeta = (MapMeta) mapItem.getItemMeta();
            
            // Create map view
            MapView mapView = Bukkit.createMap(Bukkit.getWorlds().get(0));
            
            // Clear existing renderers
            for (MapRenderer renderer : mapView.getRenderers()) {
                mapView.removeRenderer(renderer);
            }
            
            // Add our custom renderer
            mapView.addRenderer(new FaceImageRenderer(image));
            
            // Set map view to item
            mapMeta.setMapView(mapView);
            
            // Set display name
            String[] ratingNames = {
                "§c§lMuy Insatisfecho", // 1
                "§6§lInsatisfecho",     // 2
                "§e§lNeutral",          // 3
                "§a§lSatisfecho",       // 4
                "§2§lMuy Satisfecho"    // 5
            };
            
            mapMeta.setDisplayName(ratingNames[rating - 1]);
            mapItem.setItemMeta(mapMeta);
            
            return mapItem;
        } catch (Exception e) {
            logger.severe("Error creating map from image for rating " + rating + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Custom map renderer for face images
     */
    private class FaceImageRenderer extends MapRenderer {
        private final BufferedImage image;
        private boolean rendered = false;
        
        public FaceImageRenderer(BufferedImage image) {
            this.image = scaleImage(image, 128, 128); // Map size is 128x128
        }
        
        @Override
        public void render(MapView map, MapCanvas canvas, Player player) {
            if (rendered) return;
            
            // Draw the image on the map canvas
            canvas.drawImage(0, 0, image);
            rendered = true;
        }
        
        /**
         * Scale image to fit map dimensions
         */
        private BufferedImage scaleImage(BufferedImage original, int width, int height) {
            BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            scaled.getGraphics().drawImage(original.getScaledInstance(width, height, BufferedImage.SCALE_SMOOTH), 0, 0, null);
            return scaled;
        }
    }
    
    /**
     * Check if player has an active survey session
     */
    public boolean hasActiveSession(Player player) {
        return activeSessions.containsKey(player);
    }
    
    /**
     * Get active session for player
     */
    public SurveySession getActiveSession(Player player) {
        return activeSessions.get(player);
    }
    
    /**
     * Get all surveys
     */
    public Map<String, Survey> getAllSurveys() {
        return new HashMap<>(surveys);
    }
    
    /**
     * Load surveys from file
     */
    private void loadSurveys() {
        if (!surveysFile.exists()) {
            return;
        }
        
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(surveysFile);
            
            for (String key : config.getKeys(false)) {
                Survey survey = (Survey) config.get(key);
                if (survey != null) {
                    surveys.put(key.toLowerCase(), survey);
                }
            }
            
            logger.info("Loaded " + surveys.size() + " surveys from file");
        } catch (Exception e) {
            logger.severe("Error loading surveys: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Save surveys to file
     */
    private void saveSurveys() {
        try {
            if (!surveysFile.getParentFile().exists()) {
                surveysFile.getParentFile().mkdirs();
            }
            
            YamlConfiguration config = new YamlConfiguration();
            
            for (Map.Entry<String, Survey> entry : surveys.entrySet()) {
                config.set(entry.getKey(), entry.getValue());
            }
            
            config.save(surveysFile);
            logger.info("Saved " + surveys.size() + " surveys to file");
        } catch (IOException e) {
            logger.severe("Error saving surveys: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Shutdown and cleanup all active sessions
     */
    public void shutdown() {
        logger.info("Shutting down Survey system...");
        
        // End all active sessions
        for (Player player : activeSessions.keySet()) {
            endSurvey(player);
        }
        
        // Save surveys
        saveSurveys();
        
        logger.info("Survey system shut down complete");
    }
    
    /**
     * Set default survey for post-test
     */
    public boolean setDefaultSurvey(String surveyName, Location location) {
        if (!surveys.containsKey(surveyName)) {
            return false;
        }
        
        this.defaultSurveyName = surveyName;
        this.defaultSurveyLocation = location.clone();
        saveSurveys(); // Save the default survey configuration
        return true;
    }
    
    /**
     * Get default survey name
     */
    public String getDefaultSurveyName() {
        return defaultSurveyName;
    }
    
    /**
     * Get default survey location
     */
    public Location getDefaultSurveyLocation() {
        return defaultSurveyLocation != null ? defaultSurveyLocation.clone() : null;
    }
    
    /**
     * Check if default survey is set
     */
    public boolean hasDefaultSurvey() {
        return defaultSurveyName != null && surveys.containsKey(defaultSurveyName) && defaultSurveyLocation != null;
    }
    
    /**
     * Start default survey for a player (used for post-test)
     */
    public boolean startDefaultSurvey(Player player) {
        if (!hasDefaultSurvey()) {
            return false;
        }
        
        // Teleport player to default survey location
        player.teleport(defaultSurveyLocation);
        
        // Start the survey
        return startSurvey(player, defaultSurveyName);
    }
    
    /**
     * Create a post-test item for a player (random face map)
     */
    public ItemStack createPostTestItem() {
        // Get a random face (1-5)
        int randomFace = (int) (Math.random() * 5) + 1;
        ItemStack faceMap = faceMaps.get(randomFace);
        
        if (faceMap != null) {
            // Clone and modify the item
            ItemStack postTestItem = faceMap.clone();
            
            // Set custom name and lore
            var meta = postTestItem.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text("Post-Test", NamedTextColor.YELLOW));
                meta.lore(java.util.Arrays.asList(
                    Component.text("Haz clic secundario para", NamedTextColor.GRAY),
                    Component.text("realizar la encuesta final", NamedTextColor.GRAY),
                    Component.text("¡Comparte tu experiencia!", NamedTextColor.GREEN)
                ));
                postTestItem.setItemMeta(meta);
                return postTestItem;
            }
        }
        
        // Fallback to a simple map item if face maps failed
        ItemStack fallbackItem = new ItemStack(Material.MAP);
        var meta = fallbackItem.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Post-Test", NamedTextColor.YELLOW));
            meta.lore(java.util.Arrays.asList(
                Component.text("Haz clic secundario para", NamedTextColor.GRAY),
                Component.text("realizar la encuesta final", NamedTextColor.GRAY),
                Component.text("¡Comparte tu experiencia!", NamedTextColor.GREEN)
            ));
            fallbackItem.setItemMeta(meta);
        }
        
        return fallbackItem;
    }
}