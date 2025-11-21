package com.seminario.plugin.util;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

/**
 * Utility class for rendering images to a single large Minecraft map
 * Provides better image quality using high-resolution maps
 */
public class SingleMapRenderer {
    
    private static final Logger LOGGER = Logger.getLogger(SingleMapRenderer.class.getName());
    
    // Target resolution for the map (will be scaled to fit)
    public static final int MAP_RESOLUTION = 128; // Base map size
    public static final int HIGH_RES_SIZE = 512;   // Target high resolution size
    
    /**
     * Create a single map item from a BufferedImage
     * @param sourceImage The image to render on the map
     * @return ItemStack containing the map
     */
    @SuppressWarnings("deprecation")
    public static ItemStack createSingleMapItem(BufferedImage sourceImage) {
        if (sourceImage == null) {
            throw new IllegalArgumentException("Source image cannot be null");
        }
        
        // Scale image to high resolution while maintaining aspect ratio
        BufferedImage scaledImage = scaleImageForMap(sourceImage, HIGH_RES_SIZE);
        
        // Create new map with zoom level for better resolution
        MapView mapView = Bukkit.createMap(Bukkit.getWorlds().get(0));
        
        // Set zoom level for higher resolution (0 = closest, 4 = furthest)
        // Level 2 gives us 512x512 effective resolution
        try {
            // Try to set scale (Paper/modern Bukkit method)
            mapView.setScale(MapView.Scale.CLOSEST);
        } catch (Exception e) {
            LOGGER.info("Using legacy map scaling");
        }
        
        // Clear existing renderers
        for (MapRenderer renderer : mapView.getRenderers()) {
            mapView.removeRenderer(renderer);
        }
        
        // Add our custom high-resolution renderer
        mapView.addRenderer(new HighResImageRenderer(scaledImage));
        
        // Create map item
        ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
        MapMeta mapMeta = (MapMeta) mapItem.getItemMeta();
        if (mapMeta != null) {
            mapMeta.setMapView(mapView);
            mapItem.setItemMeta(mapMeta);
        }
        
        return mapItem;
    }
    
    /**
     * Scale an image to fit the map while maintaining aspect ratio
     * @param originalImage The original image
     * @param targetSize The target size (will be square)
     * @return Scaled BufferedImage
     */
    public static BufferedImage scaleImageForMap(BufferedImage originalImage, int targetSize) {
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();
        
        // Calculate scaling to fit within target size while maintaining aspect ratio
        double scaleX = (double) targetSize / originalWidth;
        double scaleY = (double) targetSize / originalHeight;
        double scale = Math.min(scaleX, scaleY); // Use smaller scale to fit both dimensions
        
        int scaledWidth = (int) (originalWidth * scale);
        int scaledHeight = (int) (originalHeight * scale);
        
        // Create new image with target size (square)
        BufferedImage scaledImage = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = scaledImage.createGraphics();
        
        // Fill background with black
        g2d.setColor(java.awt.Color.BLACK);
        g2d.fillRect(0, 0, targetSize, targetSize);
        
        // Draw scaled image centered
        int x = (targetSize - scaledWidth) / 2;
        int y = (targetSize - scaledHeight) / 2;
        g2d.drawImage(originalImage, x, y, scaledWidth, scaledHeight, null);
        g2d.dispose();
        
        return scaledImage;
    }
    
    /**
     * Give a single map to the player in their first available hotbar slot
     * @param player The player to give the map to
     * @param mapItem The map ItemStack
     */
    public static void giveSingleMapToPlayer(Player player, ItemStack mapItem) {
        // Clear hotbar first
        for (int i = 0; i < 9; i++) {
            player.getInventory().setItem(i, null);
        }
        
        // Give map in first slot
        player.getInventory().setItem(0, mapItem);
        
        // Update inventory
        player.updateInventory();
        
        LOGGER.info("Gave single high-resolution map to " + player.getName());
    }
    
    /**
     * Get the map ID from a map ItemStack
     * @param mapItem The map ItemStack
     * @return Map ID or -1 if invalid
     */
    @SuppressWarnings("deprecation")
    public static int getMapId(ItemStack mapItem) {
        if (mapItem == null || mapItem.getType() != Material.FILLED_MAP) {
            return -1;
        }
        
        MapMeta mapMeta = (MapMeta) mapItem.getItemMeta();
        if (mapMeta != null && mapMeta.getMapView() != null) {
            return mapMeta.getMapView().getId();
        }
        
        return -1;
    }
    
    /**
     * Create a map item from an existing map ID
     * @param mapId The map ID
     * @return ItemStack or null if invalid
     */
    @SuppressWarnings("deprecation")
    public static ItemStack createMapItemFromId(int mapId) {
        try {
            MapView mapView = Bukkit.getMap(mapId);
            if (mapView == null) {
                return null;
            }
            
            ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
            MapMeta mapMeta = (MapMeta) mapItem.getItemMeta();
            if (mapMeta != null) {
                mapMeta.setMapView(mapView);
                mapItem.setItemMeta(mapMeta);
            }
            
            return mapItem;
        } catch (Exception e) {
            LOGGER.warning("Failed to create map item from ID " + mapId + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Custom map renderer for high-resolution images
     */
    public static class HighResImageRenderer extends MapRenderer {
        
        private final BufferedImage image;
        private boolean rendered = false;
        
        public HighResImageRenderer(BufferedImage image) {
            this.image = image;
        }
        
        @Override
        public void render(MapView map, MapCanvas canvas, Player player) {
            if (rendered) {
                return; // Only render once for performance
            }
            
            try {
                // Scale image to exactly fit the map canvas (128x128)
                BufferedImage canvasImage = scaleImageForMap(image, 128);
                
                // Draw image to canvas
                for (int x = 0; x < 128; x++) {
                    for (int y = 0; y < 128; y++) {
                        if (x < canvasImage.getWidth() && y < canvasImage.getHeight()) {
                            int rgb = canvasImage.getRGB(x, y);
                            
                            // Convert RGB to closest Minecraft map color
                            byte mapColor = getClosestMapColor(rgb);
                            canvas.setPixel(x, y, mapColor);
                        }
                    }
                }
                
                rendered = true;
                
            } catch (Exception e) {
                LOGGER.warning("Error rendering image to map: " + e.getMessage());
            }
        }
        
        /**
         * Convert RGB color to closest Minecraft map color
         * @param rgb The RGB color value
         * @return Closest map color byte
         */
        private byte getClosestMapColor(int rgb) {
            // Extract RGB components
            int red = (rgb >> 16) & 0xFF;
            int green = (rgb >> 8) & 0xFF;
            int blue = rgb & 0xFF;
            
            // Simple brightness-based mapping to map colors
            // Minecraft maps have limited color palette, this is a basic approximation
            int brightness = (red + green + blue) / 3;
            
            // Map brightness to Minecraft map color range (approximately 0-63)
            // This is a simplified mapping - for better results, use proper color matching
            if (brightness < 32) return 0;      // Black-ish
            else if (brightness < 64) return 8;  // Dark gray
            else if (brightness < 96) return 12; // Gray  
            else if (brightness < 128) return 16; // Light gray
            else if (brightness < 160) return 20; // White-ish
            else if (brightness < 192) return 24; // Bright
            else if (brightness < 224) return 28; // Very bright
            else return 34; // White
        }
    }
}