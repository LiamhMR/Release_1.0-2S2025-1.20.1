package com.seminario.plugin.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

/**
 * Manages floating item frames for slideshow displays
 * Creates 2x2 grid of invisible item frames in front of players
 */
public class FloatingFrameRenderer {
    
    // Distance from player to display frames
    private static final double DISPLAY_DISTANCE = 3.0;
    // Size of each frame (in blocks)
    private static final double FRAME_SIZE = 1.0;
    // Vertical offset from player eye level
    private static final double VERTICAL_OFFSET = 0.5;
    
    // Track active frame displays: playerId -> List<ItemFrame>
    private static final Map<UUID, List<ItemFrame>> activeDisplays = new HashMap<>();
    
    /**
     * Create floating 2x2 item frame display in front of player
     * @param player The player to create display for
     * @param mapItems List of 4 map items for the 2x2 grid (top-left, top-right, bottom-left, bottom-right)
     * @return List of created item frames
     */
    public static List<ItemFrame> createFloatingDisplay(Player player, List<ItemStack> mapItems) {
        if (mapItems.size() != 4) {
            throw new IllegalArgumentException("Exactly 4 map items required for 2x2 display");
        }
        
        // Remove existing display if any
        removeFloatingDisplay(player);
        
        Location playerLoc = player.getEyeLocation();
        Vector direction = playerLoc.getDirection().normalize();
        Vector right = direction.getCrossProduct(new Vector(0, 1, 0)).normalize();
        Vector up = new Vector(0, 1, 0);
        
        // Calculate center position of the display
        Location centerLoc = playerLoc.add(direction.multiply(DISPLAY_DISTANCE));
        centerLoc.add(0, VERTICAL_OFFSET, 0); // Adjust vertical position
        
        List<ItemFrame> frames = new ArrayList<>();
        World world = player.getWorld();
        
        // Get the direction the frame should face (toward player)
        BlockFace facingDirection = getPlayerFacingDirection(direction);
        
        // Create 2x2 grid of temporary blocks as support
        createTemporaryBlockGrid(centerLoc, right, up, facingDirection);
        
        // Create 2x2 grid positions for frames
        // Grid layout: [0][1]  (Top-left, Top-right)
        //              [2][3]  (Bottom-left, Bottom-right)
        Location[] framePositions = {
            // Top-left
            centerLoc.clone().subtract(right.clone().multiply(FRAME_SIZE / 2)).add(up.clone().multiply(FRAME_SIZE / 2)),
            // Top-right  
            centerLoc.clone().add(right.clone().multiply(FRAME_SIZE / 2)).add(up.clone().multiply(FRAME_SIZE / 2)),
            // Bottom-left
            centerLoc.clone().subtract(right.clone().multiply(FRAME_SIZE / 2)).subtract(up.clone().multiply(FRAME_SIZE / 2)),
            // Bottom-right
            centerLoc.clone().add(right.clone().multiply(FRAME_SIZE / 2)).subtract(up.clone().multiply(FRAME_SIZE / 2))
        };
        
        // Create item frames at each position
        for (int i = 0; i < 4; i++) {
            Location frameLoc = framePositions[i];
            
            // Spawn the item frame attached to the block
            ItemFrame frame = world.spawn(frameLoc, ItemFrame.class);
            
            // Configure the frame
            frame.setVisible(false); // Make frame invisible
            frame.setFixed(true); // Prevent players from breaking it
            frame.setFacingDirection(facingDirection); // Set orientation
            
            // Calculate correct map rotation based on frame orientation
            org.bukkit.Rotation mapRotation = getCorrectMapRotation(facingDirection);
            frame.setRotation(mapRotation);
            
            // Set the map item AFTER configuring the frame
            frame.setItem(mapItems.get(i));
            
            // Add metadata to identify this as a slideshow frame
            frame.customName(net.kyori.adventure.text.Component.text("[Slideshow Frame " + i + "]", net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY));
            frame.setCustomNameVisible(false);
            
            frames.add(frame);
        }
        
        // Store the display for this player
        activeDisplays.put(player.getUniqueId(), frames);
        
        return frames;
    }
    
    /**
     * Update existing floating display with new map items
     * @param player The player whose display to update
     * @param mapItems New map items for the display
     * @return true if display was updated successfully
     */
    public static boolean updateFloatingDisplay(Player player, List<ItemStack> mapItems) {
        List<ItemFrame> frames = activeDisplays.get(player.getUniqueId());
        if (frames == null || frames.size() != 4 || mapItems.size() != 4) {
            return false;
        }
        
        // Update each frame with new map item
        for (int i = 0; i < 4; i++) {
            ItemFrame frame = frames.get(i);
            if (frame.isValid()) {
                frame.setItem(mapItems.get(i));
            }
        }
        
        return true;
    }
    
    /**
     * Remove floating display for a player
     * @param player The player whose display to remove
     */
    public static void removeFloatingDisplay(Player player) {
        List<ItemFrame> frames = activeDisplays.remove(player.getUniqueId());
        if (frames != null) {
            // Remove temporary blocks first
            removeTemporaryBlocks(player);
            
            // Remove frames
            for (ItemFrame frame : frames) {
                if (frame.isValid()) {
                    frame.remove();
                }
            }
        }
    }
    
    /**
     * Update display position when player moves within zone
     * @param player The player whose display to update
     */
    public static void updateDisplayPosition(Player player) {
        List<ItemFrame> frames = activeDisplays.get(player.getUniqueId());
        if (frames == null || frames.isEmpty()) {
            return;
        }
        
        // Get current map items before recreating
        List<ItemStack> currentMaps = new ArrayList<>();
        for (ItemFrame frame : frames) {
            if (frame.isValid()) {
                currentMaps.add(frame.getItem());
            }
        }
        
        if (currentMaps.size() == 4) {
            // Recreate display at new position
            createFloatingDisplay(player, currentMaps);
        }
    }
    
    /**
     * Check if player has an active floating display
     * @param player The player to check
     * @return true if player has active display
     */
    public static boolean hasFloatingDisplay(Player player) {
        List<ItemFrame> frames = activeDisplays.get(player.getUniqueId());
        return frames != null && !frames.isEmpty();
    }
    
    /**
     * Get item frames for a player's display
     * @param player The player
     * @return List of item frames or null if no display
     */
    public static List<ItemFrame> getPlayerFrames(Player player) {
        return activeDisplays.get(player.getUniqueId());
    }
    
    /**
     * Check if an entity is part of a slideshow display
     * @param entity The entity to check
     * @return true if it's a slideshow frame
     */
    public static boolean isSlideshowFrame(Entity entity) {
        if (!(entity instanceof ItemFrame)) {
            return false;
        }
        
        net.kyori.adventure.text.Component customName = entity.customName();
        return customName != null && net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(customName).contains("[Slideshow Frame]");
    }
    
    /**
     * Find which player owns a specific slideshow frame
     * @param frame The item frame
     * @return Player UUID or null if not found
     */
    public static UUID getFrameOwner(ItemFrame frame) {
        for (Map.Entry<UUID, List<ItemFrame>> entry : activeDisplays.entrySet()) {
            if (entry.getValue().contains(frame)) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    /**
     * Clean up all floating displays (for plugin shutdown)
     */
    public static void cleanupAllDisplays() {
        for (Map.Entry<UUID, List<ItemFrame>> entry : activeDisplays.entrySet()) {
            org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                removeTemporaryBlocks(player);
            }
            
            for (ItemFrame frame : entry.getValue()) {
                if (frame.isValid()) {
                    frame.remove();
                }
            }
        }
        activeDisplays.clear();
    }
    
    /**
     * Create temporary blocks in a 2x2 grid to support item frames
     * @param centerLoc Center location of the grid
     * @param right Right vector for positioning
     * @param up Up vector for positioning  
     * @param facingDirection Direction the frames will face
     */
    private static void createTemporaryBlockGrid(Location centerLoc, Vector right, Vector up, BlockFace facingDirection) {
        // Get the offset vector to place blocks behind the frames
        Vector backOffset = getDirectionVector(facingDirection).multiply(0.1);
        
        // Create 2x2 grid of barrier blocks (invisible)
        Location[] blockPositions = {
            // Top-left
            centerLoc.clone().subtract(right.clone().multiply(FRAME_SIZE / 2)).add(up.clone().multiply(FRAME_SIZE / 2)).add(backOffset),
            // Top-right  
            centerLoc.clone().add(right.clone().multiply(FRAME_SIZE / 2)).add(up.clone().multiply(FRAME_SIZE / 2)).add(backOffset),
            // Bottom-left
            centerLoc.clone().subtract(right.clone().multiply(FRAME_SIZE / 2)).subtract(up.clone().multiply(FRAME_SIZE / 2)).add(backOffset),
            // Bottom-right
            centerLoc.clone().add(right.clone().multiply(FRAME_SIZE / 2)).subtract(up.clone().multiply(FRAME_SIZE / 2)).add(backOffset)
        };
        
        // Place barrier blocks
        for (Location blockLoc : blockPositions) {
            Block block = blockLoc.getBlock();
            if (block.getType() == Material.AIR) {
                block.setType(Material.BARRIER);
            }
        }
    }
    
    /**
     * Get attachment block location for a frame
     * @param frameLoc Frame location
     * @param facingDirection Direction the frame faces
     * @return Location of the attachment block
     */
    private static Location getAttachmentBlockLocation(Location frameLoc, BlockFace facingDirection) {
        Vector backOffset = getDirectionVector(facingDirection).multiply(0.1);
        return frameLoc.clone().add(backOffset);
    }
    
    /**
     * Convert BlockFace to direction vector
     * @param face The BlockFace
     * @return Corresponding direction vector
     */
    private static Vector getDirectionVector(BlockFace face) {
        return switch (face) {
            case NORTH -> new Vector(0, 0, -1);
            case SOUTH -> new Vector(0, 0, 1);
            case EAST -> new Vector(1, 0, 0);
            case WEST -> new Vector(-1, 0, 0);
            default -> new Vector(0, 0, 1); // Default to south
        };
    }
    
    /**
     * Remove temporary blocks for a player's display
     * @param player The player
     */
    private static void removeTemporaryBlocks(Player player) {
        List<ItemFrame> frames = activeDisplays.get(player.getUniqueId());
        if (frames == null) return;
        
        for (ItemFrame frame : frames) {
            if (frame.isValid()) {
                BlockFace facing = frame.getFacing();
                Location blockLoc = getAttachmentBlockLocation(frame.getLocation(), facing);
                Block block = blockLoc.getBlock();
                if (block.getType() == Material.BARRIER) {
                    block.setType(Material.AIR);
                }
            }
        }
    }
    
    /**
     * Get the direction the frame should face to be visible to the player
     * Frame should face toward the player (opposite of player's looking direction)
     * @param playerDirection The direction the player is looking
     * @return The BlockFace the frame should face
     */
    private static BlockFace getPlayerFacingDirection(Vector playerDirection) {
        // Determine the primary direction the player is looking
        double x = playerDirection.getX();
        double z = playerDirection.getZ();
        
        // Frame should face toward the player, so we use the opposite direction
        if (Math.abs(x) > Math.abs(z)) {
            // Player looking primarily east/west
            return x > 0 ? BlockFace.WEST : BlockFace.EAST;
        } else {
            // Player looking primarily north/south  
            return z > 0 ? BlockFace.NORTH : BlockFace.SOUTH;
        }
    }
    
    /**
     * Get the correct map rotation based on frame orientation
     * This ensures the map image appears correctly oriented regardless of frame direction
     * @param frameFacing The direction the frame is facing
     * @return The correct Rotation for the map
     */
    private static org.bukkit.Rotation getCorrectMapRotation(BlockFace frameFacing) {
        return switch (frameFacing) {
            case NORTH -> org.bukkit.Rotation.NONE;              // 0° - Default orientation
            case EAST -> org.bukkit.Rotation.CLOCKWISE;          // 90° clockwise
            case SOUTH -> org.bukkit.Rotation.FLIPPED;           // 180° 
            case WEST -> org.bukkit.Rotation.COUNTER_CLOCKWISE;  // 270° / 90° counter-clockwise
            default -> org.bukkit.Rotation.NONE;                 // Default fallback
        };
    }
}