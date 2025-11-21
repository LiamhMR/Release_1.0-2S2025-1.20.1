package com.seminario.plugin.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * 16x11 screen system with item frames to display high-resolution images
 * Total resolution: 2048x1408 pixels (176 maps of 128x128)
 */
public class SlideScreenRenderer {
    
    // Screen configuration
    private static final int GRID_WIDTH = 16;  // 16 maps wide
    private static final int GRID_HEIGHT = 11; // 11 maps tall
    private static final int TOTAL_MAPS = GRID_WIDTH * GRID_HEIGHT; // 176 maps
    private static final double DISPLAY_DISTANCE = 6.5; // 6.5 blocks distance
    private static final double FRAME_SIZE = 1.0; // Size of each frame
    private static final double SCREEN_HEIGHT_OFFSET = 0.5; // Slightly higher
    private static final double BUTTON_OFFSET_X = 1.5; // Horizontal distance of buttons from player
    private static final double BUTTON_DISTANCE = 1.5; // Distance of buttons in front of player
    private static final double BUTTON_OFFSET_Y = -0.5; // Button height (slightly below eye level)
    private static final double HOLOGRAM_OFFSET_Y = 0.5; // Hologram height above button
    
    // Storage for active screens: playerId -> List<ItemFrame>
    private static final Map<UUID, List<ItemFrame>> activeScreens = new HashMap<>();
    
    // Storage for temporary support blocks: playerId -> List<Location>
    private static final Map<UUID, List<Location>> supportBlocks = new HashMap<>();
    
    // Storage for navigation buttons: playerId -> [previousButton, nextButton]
    private static final Map<UUID, ItemFrame[]> navigationButtons = new HashMap<>();
    
    // Storage for button holograms: playerId -> [previousHologram, nextHologram]
    private static final Map<UUID, org.bukkit.entity.ArmorStand[]> navigationHolograms = new HashMap<>();
    
    /**
     * Create 16x11 screen in front of player
     * @param player The player
     * @param mapItems List of 176 maps for the 16x11 grid
     * @return List of created item frames
     */
    public static List<ItemFrame> createSlideScreen(Player player, List<ItemStack> mapItems) {
        return createSlideScreen(player, mapItems, null, null);
    }
    
    /**
     * Create 16x11 screen with optional fixed direction
     * @param player The player
     * @param mapItems List of 176 maps for the 16x11 grid
     * @param fixedDirection Fixed direction (+X, -X, +Z, -Z) or null for player direction
     * @return List of created item frames
     */
    public static List<ItemFrame> createSlideScreen(Player player, List<ItemStack> mapItems, String fixedDirection) {
        return createSlideScreen(player, mapItems, fixedDirection, null);
    }
    
    /**
     * Create 16x11 screen with optional fixed position and direction
     * @param player The player
     * @param mapItems List of 176 maps for the 16x11 grid
     * @param fixedDirection Fixed direction (+X, -X, +Z, -Z) or null
     * @param fixedLocation Fixed location for rendering or null
     * @return List of created item frames
     */
    public static List<ItemFrame> createSlideScreen(Player player, List<ItemStack> mapItems, String fixedDirection, Location fixedLocation) {
        if (mapItems.size() != TOTAL_MAPS) {
            throw new IllegalArgumentException("Se requieren exactamente " + TOTAL_MAPS + " mapas para pantalla " + GRID_WIDTH + "x" + GRID_HEIGHT);
        }
        
        // Clear existing screen
        removeSlideScreen(player);
        
        // Use fixed position or player position
        Location playerEye = (fixedLocation != null) ? fixedLocation.clone() : player.getEyeLocation();
        Vector direction;
        
        // If there's a fixed direction, use it; otherwise, use eye/location direction
        if (fixedDirection != null && !fixedDirection.isEmpty()) {
            direction = getFixedDirectionVector(fixedDirection);
        } else {
            direction = playerEye.getDirection().normalize();
        }
        
        // Rest of creation code...
        // Calculate vectors for positioning
        Vector right = direction.getCrossProduct(new Vector(0, 1, 0)).normalize();
        Vector up = new Vector(0, 1, 0);
        
        // Screen center (in front of reference point)
        Location screenCenter = playerEye.clone().add(direction.multiply(DISPLAY_DISTANCE));
        screenCenter.add(0, SCREEN_HEIGHT_OFFSET, 0);
        
        // Determine frame orientation (towards player)
        BlockFace frameFacing = calculateFrameFacing(direction);
        
        // Create 15x11 grid of item frames FIRST
        List<ItemFrame> frames = new ArrayList<>();
        
        // Vector towards player (opposite to frame direction)
        Vector towardsPlayer = getDirectionVector(frameFacing).multiply(-0.01);
        
        // Grid layout (15 columns x 11 rows = 165 frames)
        for (int row = 0; row < GRID_HEIGHT; row++) {
            for (int col = 0; col < GRID_WIDTH; col++) {
                int mapIndex = row * GRID_WIDTH + col;
                
                // Calculate frame position
                // row 0 = top, row 10 = bottom
                // col 0 = left, col 14 = right
                double xOffset = (col - (GRID_WIDTH - 1) / 2.0) * FRAME_SIZE; // Centered horizontally
                double yOffset = ((GRID_HEIGHT - 1) / 2.0 - row) * FRAME_SIZE; // Centered vertically
                
                Location framePos = screenCenter.clone()
                    .add(right.clone().multiply(xOffset))
                    .add(up.clone().multiply(yOffset))
                    .add(towardsPlayer); // Slightly towards player to be IN FRONT of block
                
                // Create the item frame
                ItemFrame frame = player.getWorld().spawn(framePos, ItemFrame.class);
                
                // Configure the frame
                configureItemFrame(frame, frameFacing, mapItems.get(mapIndex), mapIndex);
                
                frames.add(frame);
            }
        }
        
        // Create support blocks AFTER frames
        List<Location> supports = createSupportBlocks(screenCenter, right, up, frameFacing, player.getWorld());
        supportBlocks.put(player.getUniqueId(), supports);
        
        // Create navigation buttons
        createNavigationButtons(player, screenCenter, right, up, frameFacing);
        
        // Store the screen
        activeScreens.put(player.getUniqueId(), frames);
        
        // Confirmation message
        String directionInfo = fixedDirection != null ? " (dirección fija: " + fixedDirection + ")" : "";
        player.sendMessage(Component.text("Pantalla " + GRID_WIDTH + "x" + GRID_HEIGHT + " creada con " + frames.size() + " frames" + directionInfo, NamedTextColor.GREEN));
        
        return frames;
    }
    
    /**
     * Create 16x11 screen for FIXSLIDE (no player, fixed position and direction)
     * @param renderLocation Fixed location where screen is rendered
     * @param mapItems List of 176 maps for the 16x11 grid
     * @param direction Fixed direction (+X, -X, +Z, -Z)
     * @return List of created item frames
     */
    public static List<ItemFrame> createFixSlideScreen(Location renderLocation, List<ItemStack> mapItems, String direction) {
        if (mapItems.size() != TOTAL_MAPS) {
            throw new IllegalArgumentException("Se requieren exactamente " + TOTAL_MAPS + " mapas para pantalla " + GRID_WIDTH + "x" + GRID_HEIGHT);
        }
        
        if (direction == null || direction.isEmpty()) {
            throw new IllegalArgumentException("Direction required for FIXSLIDE");
        }
        
        // Calculate direction
        Vector directionVector = getFixedDirectionVector(direction);
        
        // Calculate vectors for positioning
        Vector right = directionVector.getCrossProduct(new Vector(0, 1, 0)).normalize();
        Vector up = new Vector(0, 1, 0);
        
        // Screen center (in front of render point)
        Location screenCenter = renderLocation.clone().add(directionVector.multiply(DISPLAY_DISTANCE));
        screenCenter.add(0, SCREEN_HEIGHT_OFFSET, 0);
        
        // Determine frame orientation
        BlockFace frameFacing = calculateFrameFacing(directionVector);
        
        // Create grid of item frames
        List<ItemFrame> frames = new ArrayList<>();
        World world = renderLocation.getWorld();
        
        // Vector towards viewpoint (opposite to frame direction)
        Vector towardsViewer = getDirectionVector(frameFacing).multiply(-0.01);
        
        // Grid layout (16 columns x 11 rows)
        for (int row = 0; row < GRID_HEIGHT; row++) {
            for (int col = 0; col < GRID_WIDTH; col++) {
                int mapIndex = row * GRID_WIDTH + col;
                
                // Calculate frame position
                double xOffset = (col - (GRID_WIDTH - 1) / 2.0) * FRAME_SIZE;
                double yOffset = ((GRID_HEIGHT - 1) / 2.0 - row) * FRAME_SIZE;
                
                Location framePos = screenCenter.clone()
                    .add(right.clone().multiply(xOffset))
                    .add(up.clone().multiply(yOffset))
                    .add(towardsViewer);
                
                // Create and configure item frame
                ItemFrame frame = world.spawn(framePos, ItemFrame.class);
                configureItemFrame(frame, frameFacing, mapItems.get(mapIndex), mapIndex);
                frame.setPersistent(true); // Persist across server restarts
                
                frames.add(frame);
            }
        }
        
        return frames;
    }
    
    /**
     * Configure an individual item frame
     */
    private static void configureItemFrame(ItemFrame frame, BlockFace facing, ItemStack mapItem, int index) {
        frame.setVisible(false); // Invisible frame
        frame.setFixed(true); // Cannot be broken
        frame.setFacingDirection(facing); // Orientation towards player
        frame.setRotation(org.bukkit.Rotation.NONE); // No rotation
        frame.setItem(mapItem); // Set the map
        
        // Metadata for identification
        frame.customName(Component.text("[SlideScreen-" + index + "]", NamedTextColor.DARK_GRAY));
        frame.setCustomNameVisible(false);
    }
    
    /**
     * Calculate frame orientation based on player direction
     */
    private static BlockFace calculateFrameFacing(Vector playerDirection) {
        double x = playerDirection.getX();
        double z = playerDirection.getZ();
        
        // Frame should face towards player (opposite direction)
        if (Math.abs(x) > Math.abs(z)) {
            return x > 0 ? BlockFace.WEST : BlockFace.EAST;
        } else {
            return z > 0 ? BlockFace.NORTH : BlockFace.SOUTH;
        }
    }
    
    /**
     * Create support blocks for item frames
     */
    private static List<Location> createSupportBlocks(Location center, Vector right, Vector up, 
                                                     BlockFace facing, World world) {
        List<Location> blocks = new ArrayList<>();
        
        // Vector backwards (where support blocks go)
        Vector back = getDirectionVector(facing);
        
        // Create 15x11 grid of support blocks
        for (int row = 0; row < GRID_HEIGHT; row++) {
            for (int col = 0; col < GRID_WIDTH; col++) {
                double xOffset = (col - (GRID_WIDTH - 1) / 2.0) * FRAME_SIZE;
                double yOffset = ((GRID_HEIGHT - 1) / 2.0 - row) * FRAME_SIZE;
                
                Location blockPos = center.clone()
                    .add(right.clone().multiply(xOffset))
                    .add(up.clone().multiply(yOffset))
                    .add(back.clone().multiply(0.1)); // Slightly behind
                
                Block block = blockPos.getBlock();
                if (block.getType() == Material.AIR) {
                    block.setType(Material.BARRIER); // Invisible block
                    blocks.add(blockPos);
                }
            }
        }
        
        return blocks;
    }
    
    /**
     * Get direction vector from BlockFace
     */
    private static Vector getDirectionVector(BlockFace face) {
        return switch (face) {
            case NORTH -> new Vector(0, 0, -1);
            case SOUTH -> new Vector(0, 0, 1);
            case EAST -> new Vector(1, 0, 0);
            case WEST -> new Vector(-1, 0, 0);
            default -> new Vector(0, 0, 1);
        };
    }
    
    /**
     * Get direction vector from fixed direction (+X, -X, +Z, -Z)
     */
    private static Vector getFixedDirectionVector(String fixedDirection) {
        return switch (fixedDirection.toUpperCase()) {
            case "+X" -> new Vector(1, 0, 0);  // East
            case "-X" -> new Vector(-1, 0, 0); // West
            case "+Z" -> new Vector(0, 0, 1);  // South
            case "-Z" -> new Vector(0, 0, -1); // North
            default -> new Vector(0, 0, 1);
        };
    }
    
    /**
     * Create navigation buttons (previous/next) near player with holograms
     */
    private static void createNavigationButtons(Player player, Location screenCenter, Vector right, Vector up, BlockFace facing) {
        // Remove previous buttons if they exist
        removeNavigationButtons(player);
        
        // Clean up any orphaned armor stands from previous buttons near player
        cleanupOrphanedButtonHolograms(player);
        
        World world = player.getWorld();
        Location playerEye = player.getEyeLocation();
        Vector direction = playerEye.getDirection().normalize();
        
        // Use same vectors as screen for consistent orientation
        Vector towardsPlayer = getDirectionVector(facing).multiply(-0.01);
        
        // PREVIOUS button (left of player using screen vectors)
        Location prevButtonPos = playerEye.clone()
            .add(direction.clone().multiply(BUTTON_DISTANCE))  // In front of player
            .add(right.clone().multiply(-BUTTON_OFFSET_X))  // To the left (using screen's right)
            .add(up.clone().multiply(BUTTON_OFFSET_Y))  // Slightly down
            .add(towardsPlayer);
        
        ItemFrame prevButton = world.spawn(prevButtonPos, ItemFrame.class);
        prevButton.setVisible(false);  // INVISIBLE so only item is visible
        prevButton.setFixed(true);
        prevButton.setFacingDirection(facing);
        prevButton.setItem(new ItemStack(Material.RED_CONCRETE)); // Red button for previous
        prevButton.setItemDropChance(0.0f); // No drop
        prevButton.customName(Component.text("[SlideButton-Previous]", NamedTextColor.RED));
        prevButton.setCustomNameVisible(false);
        
        // Hologram for previous button
        Location prevHologramPos = prevButtonPos.clone().add(0, HOLOGRAM_OFFSET_Y, 0);
        org.bukkit.entity.ArmorStand prevHologram = world.spawn(prevHologramPos, org.bukkit.entity.ArmorStand.class);
        prevHologram.setVisible(false);
        prevHologram.setGravity(false);
        prevHologram.setMarker(true);
        prevHologram.setCustomNameVisible(true);
        prevHologram.customName(Component.text("← Anterior", NamedTextColor.RED));
        prevHologram.setInvulnerable(true);
        
        // NEXT button (right of player using screen vectors)
        Location nextButtonPos = playerEye.clone()
            .add(direction.clone().multiply(BUTTON_DISTANCE))  // In front of player
            .add(right.clone().multiply(BUTTON_OFFSET_X))  // To the right (using screen's right)
            .add(up.clone().multiply(BUTTON_OFFSET_Y))  // Slightly down
            .add(towardsPlayer);
        
        ItemFrame nextButton = world.spawn(nextButtonPos, ItemFrame.class);
        nextButton.setVisible(false);  // INVISIBLE so only item is visible
        nextButton.setFixed(true);
        nextButton.setFacingDirection(facing);
        nextButton.setItem(new ItemStack(Material.LIME_CONCRETE)); // Green button for next
        nextButton.setItemDropChance(0.0f); // No drop
        nextButton.customName(Component.text("[SlideButton-Next]", NamedTextColor.GREEN));
        nextButton.setCustomNameVisible(false);
        
        // Hologram for next button
        Location nextHologramPos = nextButtonPos.clone().add(0, HOLOGRAM_OFFSET_Y, 0);
        org.bukkit.entity.ArmorStand nextHologram = world.spawn(nextHologramPos, org.bukkit.entity.ArmorStand.class);
        nextHologram.setVisible(false);
        nextHologram.setGravity(false);
        nextHologram.setMarker(true);
        nextHologram.setCustomNameVisible(true);
        nextHologram.customName(Component.text("Siguiente →", NamedTextColor.GREEN));
        nextHologram.setInvulnerable(true);
        
        // Save buttons and holograms
        navigationButtons.put(player.getUniqueId(), new ItemFrame[]{prevButton, nextButton});
        navigationHolograms.put(player.getUniqueId(), new org.bukkit.entity.ArmorStand[]{prevHologram, nextHologram});
    }
    
    /**
     * Remove navigation buttons and holograms from a player
     */
    private static void removeNavigationButtons(Player player) {
        UUID playerId = player.getUniqueId();
        
        // Remove buttons
        ItemFrame[] buttons = navigationButtons.remove(playerId);
        if (buttons != null) {
            for (ItemFrame button : buttons) {
                if (button != null && button.isValid()) {
                    button.remove();
                }
            }
        }
        
        // Remove holograms
        org.bukkit.entity.ArmorStand[] holograms = navigationHolograms.remove(playerId);
        if (holograms != null) {
            for (org.bukkit.entity.ArmorStand hologram : holograms) {
                if (hologram != null && hologram.isValid()) {
                    hologram.remove();
                }
            }
        }
    }
    
    /**
     * Clean up orphaned armor stands from previous buttons near player
     */
    private static void cleanupOrphanedButtonHolograms(Player player) {
        World world = player.getWorld();
        Location playerLoc = player.getLocation();
        
        // Search for armor stands within 20 blocks radius
        world.getNearbyEntities(playerLoc, 20, 20, 20).forEach(entity -> {
            if (entity instanceof org.bukkit.entity.ArmorStand armorStand) {
                Component customName = armorStand.customName();
                if (customName != null) {
                    String nameStr = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(customName);
                    // Remove armor stands with button names
                    if (nameStr.equals("← Anterior") || nameStr.equals("Siguiente →")) {
                        armorStand.remove();
                    }
                }
            }
        });
    }
    
    /**
     * Check if an item frame is a navigation button
     */
    public static boolean isNavigationButton(Entity entity) {
        if (!(entity instanceof ItemFrame frame)) {
            return false;
        }
        Component name = frame.customName();
        if (name == null) {
            return false;
        }
        String nameStr = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(name);
        return nameStr.equals("[SlideButton-Previous]") || nameStr.equals("[SlideButton-Next]");
    }
    
    /**
     * Check if a button is the "previous" button
     */
    public static boolean isPreviousButton(ItemFrame frame) {
        Component name = frame.customName();
        if (name == null) {
            return false;
        }
        String nameStr = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(name);
        return nameStr.equals("[SlideButton-Previous]");
    }
    
    /**
     * Check if a button is the "next" button
     */
    public static boolean isNextButton(ItemFrame frame) {
        Component name = frame.customName();
        if (name == null) {
            return false;
        }
        String nameStr = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(name);
        return nameStr.equals("[SlideButton-Next]");
    }
    
    /**
     * Update existing screen with new maps
     */
    public static boolean updateSlideScreen(Player player, List<ItemStack> newMapItems) {
        List<ItemFrame> frames = activeScreens.get(player.getUniqueId());
        if (frames == null || frames.size() != TOTAL_MAPS || newMapItems.size() != TOTAL_MAPS) {
            return false;
        }
        
        // Update each frame with its new map
        for (int i = 0; i < TOTAL_MAPS; i++) {
            ItemFrame frame = frames.get(i);
            if (frame.isValid()) {
                frame.setItem(newMapItems.get(i));
            }
        }
        
        return true;
    }
    
    /**
     * Remove screen from a player
     */
    public static void removeSlideScreen(Player player) {
        UUID playerId = player.getUniqueId();
        
        // Remove item frames
        List<ItemFrame> frames = activeScreens.remove(playerId);
        if (frames != null) {
            for (ItemFrame frame : frames) {
                if (frame.isValid()) {
                    frame.remove();
                }
            }
        }
        
        // Remove navigation buttons
        removeNavigationButtons(player);
        
        // Remove support blocks
        List<Location> supports = supportBlocks.remove(playerId);
        if (supports != null) {
            for (Location blockLoc : supports) {
                Block block = blockLoc.getBlock();
                if (block.getType() == Material.BARRIER) {
                    block.setType(Material.AIR);
                }
            }
        }
        
        player.sendMessage(Component.text("Pantalla removida", NamedTextColor.GRAY));
    }
    
    /**
     * Check if a player has an active screen
     */
    public static boolean hasActiveScreen(Player player) {
        List<ItemFrame> frames = activeScreens.get(player.getUniqueId());
        return frames != null && !frames.isEmpty();
    }
    
    /**
     * Check if an entity is part of a slide screen
     */
    public static boolean isSlideScreenFrame(Entity entity) {
        if (!(entity instanceof ItemFrame)) {
            return false;
        }
        
        Component name = entity.customName();
        if (name == null) {
            return false;
        }
        
        String nameStr = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
            .plainText().serialize(name);
        return nameStr.contains("[SlideScreen-");
    }
    
    /**
     * Find the owner of a screen frame
     */
    public static UUID getScreenOwner(ItemFrame frame) {
        for (Map.Entry<UUID, List<ItemFrame>> entry : activeScreens.entrySet()) {
            if (entry.getValue().contains(frame)) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    /**
     * Clean up all screens (plugin shutdown)
     */
    public static void cleanupAllScreens() {
        for (Map.Entry<UUID, List<ItemFrame>> entry : activeScreens.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                removeSlideScreen(player);
            }
        }
        activeScreens.clear();
        supportBlocks.clear();
    }
    
    /**
     * Reposition screen when player moves
     */
    public static void repositionScreen(Player player) {
        List<ItemFrame> currentFrames = activeScreens.get(player.getUniqueId());
        if (currentFrames == null || currentFrames.isEmpty()) {
            return;
        }
        
        // Get current maps
        List<ItemStack> currentMaps = new ArrayList<>();
        for (ItemFrame frame : currentFrames) {
            if (frame.isValid()) {
                currentMaps.add(frame.getItem());
            }
        }
        
        // Recreate screen in new position (15x11 system = 165 maps)
        if (currentMaps.size() == TOTAL_MAPS) {
            createSlideScreen(player, currentMaps);
        }
    }
}