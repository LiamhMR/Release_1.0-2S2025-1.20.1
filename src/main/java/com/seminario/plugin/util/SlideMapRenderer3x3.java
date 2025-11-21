package com.seminario.plugin.util;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

/**
 * Map renderer for 3x3 slide displays
 * Handles creation and rendering of 128x128 pixel maps
 */
public class SlideMapRenderer3x3 {
    
    /**
     * Create 9 maps from 9 image segments for 3x3 display
     * @param imageSegments Array of 9 BufferedImages (128x128 each)
     * @return List of ItemStacks with the maps
     */
    public static List<ItemStack> createMapItemsFor3x3Display(BufferedImage[] imageSegments) {
        if (imageSegments.length != 9) {
            throw new IllegalArgumentException("Se requieren exactamente 9 segmentos de imagen");
        }
        
        List<ItemStack> mapItems = new ArrayList<>();
        
        for (int i = 0; i < 9; i++) {
            BufferedImage segment = imageSegments[i];
            if (segment == null) {
                throw new IllegalArgumentException("Segmento de imagen " + i + " es null");
            }
            
            // Create a new map
            ItemStack mapItem = createMapItemFromImage(segment, i);
            mapItems.add(mapItem);
        }
        
        return mapItems;
    }
    
    /**
     * Create a map ItemStack from an image
     * @param image The image (128x128)
     * @param segmentIndex Segment index (0-8)
     * @return Map ItemStack
     */
    private static ItemStack createMapItemFromImage(BufferedImage image, int segmentIndex) {
        // Create new map
        MapView mapView = Bukkit.createMap(Bukkit.getWorlds().get(0));
        
        // Clear existing renderers
        for (MapRenderer renderer : mapView.getRenderers()) {
            mapView.removeRenderer(renderer);
        }
        
        // Add our custom renderer
        mapView.addRenderer(new ImageMapRenderer(image));
        
        // Create ItemStack
        ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
        MapMeta mapMeta = (MapMeta) mapItem.getItemMeta();
        mapMeta.setMapView(mapView);
        mapMeta.displayName(net.kyori.adventure.text.Component.text("Slide Segment " + (segmentIndex + 1), net.kyori.adventure.text.format.NamedTextColor.GOLD));
        mapItem.setItemMeta(mapMeta);
        
        return mapItem;
    }
    
    /**
     * Create maps from existing IDs
     * @param mapIds List of 9 map IDs
     * @return List of ItemStacks
     */
    public static List<ItemStack> createMapItemsFromIds(List<Integer> mapIds) {
        if (mapIds.size() != 9) {
            throw new IllegalArgumentException("Se requieren exactamente 9 IDs de mapas");
        }
        
        List<ItemStack> mapItems = new ArrayList<>();
        
        for (int i = 0; i < 9; i++) {
            int mapId = mapIds.get(i);
            ItemStack mapItem = createMapItemFromId(mapId);
            mapItems.add(mapItem);
        }
        
        return mapItems;
    }
    
    /**
     * Create map ItemStack from an ID
     * @param mapId The map ID
     * @return Map ItemStack
     */
    private static ItemStack createMapItemFromId(int mapId) {
        MapView mapView = Bukkit.getMap(mapId);
        if (mapView == null) {
            throw new IllegalArgumentException("Mapa con ID " + mapId + " no encontrado");
        }
        
        ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
        MapMeta mapMeta = (MapMeta) mapItem.getItemMeta();
        mapMeta.setMapView(mapView);
        mapItem.setItemMeta(mapMeta);
        
        return mapItem;
    }
    
    /**
     * Get the IDs from a list of maps
     * @param mapItems List of map ItemStacks
     * @return List of map IDs
     */
    public static List<Integer> getMapIds(List<ItemStack> mapItems) {
        List<Integer> mapIds = new ArrayList<>();
        
        for (ItemStack mapItem : mapItems) {
            if (mapItem.getType() == Material.FILLED_MAP) {
                MapMeta mapMeta = (MapMeta) mapItem.getItemMeta();
                if (mapMeta != null) {
                    MapView mapView = mapMeta.getMapView();
                    if (mapView != null) {
                        mapIds.add(mapView.getId());
                    }
                }
            }
        }
        
        return mapIds;
    }
    
    /**
     * Custom renderer for images on maps
     */
    private static class ImageMapRenderer extends MapRenderer {
        private final BufferedImage image;
        private boolean rendered = false;
        
        public ImageMapRenderer(BufferedImage image) {
            this.image = image;
        }
        
        @Override
        public void render(MapView map, MapCanvas canvas, org.bukkit.entity.Player player) {
            if (rendered) {
                return; // Only render once
            }
            
            // Render the image on the map canvas
            if (image != null) {
                canvas.drawImage(0, 0, image);
                rendered = true;
            }
        }
    }
}