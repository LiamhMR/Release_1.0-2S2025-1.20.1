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
 * Map renderer for 16x11 slide displays
 * Handles creation and rendering of 128x128 pixel maps for 16x11 grid
 */
public class SlideMapRenderer4x4 {
    
    /**
     * Create 176 maps from 176 image segments for 16x11 display
     * @param imageSegments Array of 176 BufferedImages (128x128 each)
     * @return List of ItemStacks with the maps
     */
    public static List<ItemStack> createMapItemsFor16x11Display(BufferedImage[] imageSegments) {
        if (imageSegments.length != 176) {
            throw new IllegalArgumentException("Se requieren exactamente 176 segmentos de imagen");
        }
        
        List<ItemStack> mapItems = new ArrayList<>();
        
        for (int i = 0; i < 176; i++) {
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
     * Create 108 maps from 108 image segments for 12x9 display
     * @param imageSegments Array of 108 BufferedImages (128x128 each)
     * @return List of ItemStacks with the maps
     * @deprecated Use createMapItemsFor16x11Display instead
     */
    @Deprecated
    public static List<ItemStack> createMapItemsFor12x9Display(BufferedImage[] imageSegments) {
        if (imageSegments.length != 108) {
            throw new IllegalArgumentException("Se requieren exactamente 108 segmentos de imagen");
        }
        
        List<ItemStack> mapItems = new ArrayList<>();
        
        for (int i = 0; i < 108; i++) {
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
     * Create 80 maps from 80 image segments for 10x8 display
     * @param imageSegments Array of 80 BufferedImages (128x128 each)
     * @return List of ItemStacks with the maps
     * @deprecated Use createMapItemsFor12x9Display instead
     */
    @Deprecated
    public static List<ItemStack> createMapItemsFor10x8Display(BufferedImage[] imageSegments) {
        if (imageSegments.length != 80) {
            throw new IllegalArgumentException("Se requieren exactamente 80 segmentos de imagen");
        }
        
        List<ItemStack> mapItems = new ArrayList<>();
        
        for (int i = 0; i < 80; i++) {
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
     * Create 48 maps from 48 image segments for 8x6 display
     * @param imageSegments Array of 48 BufferedImages (128x128 each)
     * @return List of ItemStacks with the maps
     * @deprecated Use createMapItemsFor10x8Display instead
     */
    @Deprecated
    public static List<ItemStack> createMapItemsFor8x6Display(BufferedImage[] imageSegments) {
        if (imageSegments.length != 48) {
            throw new IllegalArgumentException("Se requieren exactamente 48 segmentos de imagen");
        }
        
        List<ItemStack> mapItems = new ArrayList<>();
        
        for (int i = 0; i < 48; i++) {
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
     * Create 30 maps from 30 image segments for 6x5 display
     * @param imageSegments Array of 30 BufferedImages (128x128 each)
     * @return List of ItemStacks with the maps
     * @deprecated Use createMapItemsFor8x6Display instead
     */
    @Deprecated
    public static List<ItemStack> createMapItemsFor6x5Display(BufferedImage[] imageSegments) {
        if (imageSegments.length != 30) {
            throw new IllegalArgumentException("Se requieren exactamente 30 segmentos de imagen");
        }
        
        List<ItemStack> mapItems = new ArrayList<>();
        
        for (int i = 0; i < 30; i++) {
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
     * Create 16 maps from 16 image segments for 4x4 display
     * @param imageSegments Array of 16 BufferedImages (128x128 each)
     * @return List of ItemStacks with the maps
     * @deprecated Use createMapItemsFor6x5Display instead
     */
    @Deprecated
    public static List<ItemStack> createMapItemsFor4x4Display(BufferedImage[] imageSegments) {
        if (imageSegments.length != 16) {
            throw new IllegalArgumentException("Se requieren exactamente 16 segmentos de imagen");
        }
        
        List<ItemStack> mapItems = new ArrayList<>();
        
        for (int i = 0; i < 16; i++) {
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
     * @param segmentIndex Segment index (0-15)
     * @return Map ItemStack
     */
    private static ItemStack createMapItemFromImage(BufferedImage image, int segmentIndex) {
        // Create new map
        MapView mapView = Bukkit.createMap(Bukkit.getWorlds().get(0));
        
        // DO NOT use scale - item frames always use scale 0 (128x128)
        // FARTHEST scale only affects when holding the map in hand
        mapView.setScale(MapView.Scale.CLOSEST); // Scale 0 - 128x128 pixels (optimal for item frames)
        
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
     * Create maps from existing IDs
     * @param mapIds List of map IDs
     * @return List of ItemStacks
     */
    public static List<ItemStack> createMapItemsFromIds(List<Integer> mapIds) {
        List<ItemStack> mapItems = new ArrayList<>();
        
        for (Integer mapId : mapIds) {
            try {
                ItemStack mapItem = createMapItemFromId(mapId);
                mapItems.add(mapItem);
            } catch (Exception e) {
                // If the map doesn't exist, create an empty one
                mapItems.add(new ItemStack(Material.FILLED_MAP));
            }
        }
        
        return mapItems;
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
     * Uses pixel-by-pixel rendering for better quality
     */
    private static class ImageMapRenderer extends MapRenderer {
        private final BufferedImage image;
        private boolean rendered = false;
        
        public ImageMapRenderer(BufferedImage image) {
            super(true); // contextual = true to disable automatic world rendering
            this.image = image;
        }
        
        @Override
        public void render(MapView map, MapCanvas canvas, org.bukkit.entity.Player player) {
            if (rendered) {
                return; // Only render once
            }
            
            // Render the image pixel by pixel for better color conversion
            if (image != null) {
                int width = Math.min(128, image.getWidth());
                int height = Math.min(128, image.getHeight());
                
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int rgb = image.getRGB(x, y);
                        // Convert RGB to byte using Minecraft's palette
                        byte color = org.bukkit.map.MapPalette.matchColor(
                            (rgb >> 16) & 0xFF,  // Red
                            (rgb >> 8) & 0xFF,   // Green
                            rgb & 0xFF           // Blue
                        );
                        canvas.setPixel(x, y, color);
                    }
                }
                rendered = true;
            }
        }
    }
}