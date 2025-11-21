package com.seminario.plugin.util;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
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
 * Utility class for rendering images to Minecraft maps
 * Handles creation of custom map renderers for slideshow images
 */
public class SlideMapRenderer {
    
    private static final Logger LOGGER = Logger.getLogger(SlideMapRenderer.class.getName());
    
    /**
     * Create map items for a 2x2 display from image quadrants
     * @param imageQuadrants Array of 4 BufferedImages (128x128 each)
     * @return List of 4 map ItemStacks
     */
    @SuppressWarnings("deprecation")
    public static List<ItemStack> createMapItemsFor2x2Display(BufferedImage[] imageQuadrants) {
        if (imageQuadrants.length != 4) {
            throw new IllegalArgumentException("Must provide exactly 4 image quadrants");
        }
        
        List<ItemStack> mapItems = new ArrayList<>();
        
        for (int i = 0; i < 4; i++) {
            BufferedImage quadrant = imageQuadrants[i];
            
            // Create new map
            MapView mapView = Bukkit.createMap(Bukkit.getWorlds().get(0));
            
            // Clear existing renderers
            for (MapRenderer renderer : mapView.getRenderers()) {
                mapView.removeRenderer(renderer);
            }
            
            // Add custom image renderer
            mapView.addRenderer(new ImageMapRenderer(quadrant));
            
            // Create map item
            ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
            MapMeta mapMeta = (MapMeta) mapItem.getItemMeta();
            if (mapMeta != null) {
                mapMeta.setMapView(mapView);
                mapItem.setItemMeta(mapMeta);
            }
            
            mapItems.add(mapItem);
            LOGGER.info("Created map item " + (i + 1) + "/4 with ID: " + mapView.getId());
        }
        
        return mapItems;
    }
    
    /**
     * Get map IDs from map items
     * @param mapItems List of map ItemStacks
     * @return List of map IDs
     */
    @SuppressWarnings("deprecation")
    public static List<Integer> getMapIds(List<ItemStack> mapItems) {
        List<Integer> mapIds = new ArrayList<>();
        
        for (ItemStack mapItem : mapItems) {
            if (mapItem.getType() == Material.FILLED_MAP) {
                MapMeta mapMeta = (MapMeta) mapItem.getItemMeta();
                if (mapMeta != null && mapMeta.getMapView() != null) {
                    mapIds.add(mapMeta.getMapView().getId());
                }
            }
        }
        
        return mapIds;
    }
    
    /**
     * Create map items from existing map IDs
     * @param mapIds List of map IDs
     * @return List of map ItemStacks
     */
    @SuppressWarnings("deprecation")
    public static List<ItemStack> createMapItemsFromIds(List<Integer> mapIds) {
        List<ItemStack> mapItems = new ArrayList<>();
        
        for (Integer mapId : mapIds) {
            MapView mapView = Bukkit.getMap(mapId);
            if (mapView != null) {
                ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
                MapMeta mapMeta = (MapMeta) mapItem.getItemMeta();
                if (mapMeta != null) {
                    mapMeta.setMapView(mapView);
                    mapItem.setItemMeta(mapMeta);
                }
                mapItems.add(mapItem);
            }
        }
        
        return mapItems;
    }
    
    /**
     * Give 2x2 map items to a player in a specific arrangement
     * @param player The player to give maps to
     * @param mapItems List of 4 map items [topLeft, topRight, bottomLeft, bottomRight]
     */
    public static void give2x2MapsToPlayer(Player player, List<ItemStack> mapItems) {
        if (mapItems.size() != 4) {
            throw new IllegalArgumentException("Must provide exactly 4 map items for 2x2 display");
        }
        
        // Clear player's inventory hotbar first 4 slots
        for (int i = 0; i < 4; i++) {
            player.getInventory().setItem(i, null);
        }
        
        // Give maps in order: [topLeft, topRight, bottomLeft, bottomRight]
        for (int i = 0; i < 4; i++) {
            player.getInventory().setItem(i, mapItems.get(i));
        }
        
        // Select first map in hotbar
        player.getInventory().setHeldItemSlot(0);
        
        player.sendMessage("§aSlide mostrado! Usa los items 1-4 para ver cada cuadrante de la imagen.");
    }
    
    /**
     * Custom MapRenderer that displays a static image
     */
    private static class ImageMapRenderer extends MapRenderer {
        
        private final BufferedImage image;
        private boolean hasRendered = false;
        
        public ImageMapRenderer(BufferedImage image) {
            this.image = image;
        }
        
        @Override
        public void render(MapView map, MapCanvas canvas, Player player) {
            // Only render once to avoid performance issues
            if (hasRendered) {
                return;
            }
            
            if (image != null) {
                // Draw the image to the map canvas
                canvas.drawImage(0, 0, image);
                hasRendered = true;
                
                LOGGER.fine("Rendered image to map " + map.getId() + " for player " + player.getName());
            }
        }
    }
    
    /**
     * Update existing maps with new image quadrants
     * @param mapIds List of existing map IDs
     * @param imageQuadrants New image quadrants to render
     * @return true if update was successful
     */
    @SuppressWarnings("deprecation")
    public static boolean updateExistingMaps(List<Integer> mapIds, BufferedImage[] imageQuadrants) {
        if (mapIds.size() != 4 || imageQuadrants.length != 4) {
            return false;
        }
        
        for (int i = 0; i < 4; i++) {
            MapView mapView = Bukkit.getMap(mapIds.get(i));
            if (mapView == null) {
                return false;
            }
            
            // Clear existing renderers
            for (MapRenderer renderer : mapView.getRenderers()) {
                mapView.removeRenderer(renderer);
            }
            
            // Add new image renderer
            mapView.addRenderer(new ImageMapRenderer(imageQuadrants[i]));
        }
        
        LOGGER.info("Updated " + mapIds.size() + " existing maps with new image");
        return true;
    }
    
    /**
     * Clean up map resources (remove custom renderers)
     * @param mapIds List of map IDs to clean up
     */
    @SuppressWarnings("deprecation")
    public static void cleanupMaps(List<Integer> mapIds) {
        for (Integer mapId : mapIds) {
            MapView mapView = Bukkit.getMap(mapId);
            if (mapView != null) {
                // Remove all custom renderers
                List<MapRenderer> renderers = new ArrayList<>(mapView.getRenderers());
                for (MapRenderer renderer : renderers) {
                    if (renderer instanceof ImageMapRenderer) {
                        mapView.removeRenderer(renderer);
                    }
                }
            }
        }
        
        LOGGER.info("Cleaned up " + mapIds.size() + " map renderers");
    }
}