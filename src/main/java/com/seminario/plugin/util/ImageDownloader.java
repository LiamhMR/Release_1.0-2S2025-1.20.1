package com.seminario.plugin.util;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Utility class for downloading and processing images from URLs
 * Handles image validation, downloading, resizing and caching
 */
public class ImageDownloader {
    
    private static final Logger LOGGER = Logger.getLogger(ImageDownloader.class.getName());
    private static final List<String> SUPPORTED_FORMATS = Arrays.asList("jpg", "jpeg", "png", "gif", "bmp");
    private static final int MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB max
    private static final int CONNECT_TIMEOUT = 10000; // 10 seconds
    private static final int READ_TIMEOUT = 30000; // 30 seconds
    private static final int TARGET_SIZE = 256; // 256x256 for 2x2 maps
    
    private final JavaPlugin plugin;
    private final File cacheDir;
    private final File slidesSrcDir;
    
    public ImageDownloader(JavaPlugin plugin) {
        this.plugin = plugin;
        this.cacheDir = new File(plugin.getDataFolder(), "image_cache");
        this.slidesSrcDir = new File(plugin.getDataFolder(), "slides_src");
        
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
        
        if (!slidesSrcDir.exists()) {
            slidesSrcDir.mkdirs();
            LOGGER.info("Created slides_src directory for local slide images");
        }
        
        // Limpiar caché al iniciar para forzar regeneración de imágenes en formato actual
        clearCache();
        LOGGER.info("Image cache cleared on startup - all slides will be regenerated in 16x11 format");
        LOGGER.info("You can place local images in slides_src/<slide_zone_name>/<slide_number>.png to avoid downloading from URLs");
    }
    
    /**
     * Clear all cached images
     */
    public void clearCache() {
        if (cacheDir.exists() && cacheDir.isDirectory()) {
            File[] files = cacheDir.listFiles();
            if (files != null) {
                int deletedCount = 0;
                for (File file : files) {
                    if (file.isFile() && file.delete()) {
                        deletedCount++;
                    }
                }
                LOGGER.info("Cleared " + deletedCount + " cached images");
            }
        }
    }
    
    /**
     * Download and process an image asynchronously
     * Checks for local image in slides_src first before downloading from URL
     * @param imageUrl The URL of the image
     * @param filename The filename to save the cached image
     * @return CompletableFuture with the processed BufferedImage
     */
    public CompletableFuture<BufferedImage> downloadAndProcessImage(String imageUrl, String filename) {
        return downloadAndProcessImage(imageUrl, filename, null, 0);
    }
    
    /**
     * Download and process an image asynchronously with local file check
     * Checks for local image in slides_src/<zoneName>/<slideNumber>.png first
     * @param imageUrl The URL of the image
     * @param filename The filename to save the cached image
     * @param zoneName The zone name (for local file lookup)
     * @param slideNumber The slide number (for local file lookup)
     * @return CompletableFuture with the processed BufferedImage
     */
    public CompletableFuture<BufferedImage> downloadAndProcessImage(String imageUrl, String filename, String zoneName, int slideNumber) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check for local image file first (slides_src/<zoneName>/<slideNumber>.png)
                if (zoneName != null && slideNumber > 0) {
                    File localImageFile = getLocalSlideImageFile(zoneName, slideNumber);
                    if (localImageFile != null && localImageFile.exists()) {
                        LOGGER.info("Loading slide " + slideNumber + " from local file: " + localImageFile.getName() + " (zone: " + zoneName + ")");
                        BufferedImage localImage = ImageIO.read(localImageFile);
                        if (localImage != null) {
                            // Process the local image the same way as downloaded images
                            BufferedImage processedImage = resizeImage(localImage, TARGET_SIZE, TARGET_SIZE);
                            
                            // Liberar imagen original si es diferente de la procesada
                            if (localImage != processedImage) {
                                localImage.flush();
                            }
                            
                            // Cache the processed image
                            try {
                                File cachedFile = new File(cacheDir, filename);
                                ImageIO.write(processedImage, "png", cachedFile);
                                LOGGER.info("Cached processed local image: " + filename);
                            } catch (IOException e) {
                                LOGGER.warning("Failed to cache local image: " + e.getMessage());
                            }
                            
                            return processedImage;
                        }
                    }
                }
                
                LOGGER.info("Starting image processing from URL: " + imageUrl);
                
                // Validate URL format
                if (!isValidImageUrl(imageUrl)) {
                    throw new IllegalArgumentException("Invalid image URL format: " + imageUrl);
                }
                
                // Check cache first
                File cachedFile = new File(cacheDir, filename);
                if (cachedFile.exists()) {
                    LOGGER.info("Loading image from cache: " + filename);
                    BufferedImage cached = ImageIO.read(cachedFile);
                    if (cached != null) {
                        return cached;
                    }
                }
                
                // Download image
                BufferedImage originalImage = downloadImage(imageUrl);
                if (originalImage == null) {
                    throw new RuntimeException("Failed to download image from: " + imageUrl);
                }
                
                // Process image (resize to 256x256)
                BufferedImage processedImage = resizeImage(originalImage, TARGET_SIZE, TARGET_SIZE);
                
                // Liberar imagen original si es diferente de la procesada
                if (originalImage != processedImage) {
                    originalImage.flush();
                    originalImage = null;
                }
                
                // Cache processed image
                try {
                    ImageIO.write(processedImage, "png", cachedFile);
                    LOGGER.info("Cached processed image: " + filename);
                } catch (IOException e) {
                    LOGGER.warning("Failed to cache image: " + e.getMessage());
                }
                
                return processedImage;
                
            } catch (Exception e) {
                LOGGER.severe("Error processing image " + imageUrl + ": " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * Download an image from URL
     * @param imageUrl The image URL
     * @return BufferedImage or null if failed
     */
    private BufferedImage downloadImage(String imageUrl) throws IOException {
        URL url = new URL(imageUrl);
        URLConnection connection = url.openConnection();
        
        if (connection instanceof HttpURLConnection) {
            HttpURLConnection httpConnection = (HttpURLConnection) connection;
            httpConnection.setRequestMethod("GET");
            httpConnection.setConnectTimeout(CONNECT_TIMEOUT);
            httpConnection.setReadTimeout(READ_TIMEOUT);
            httpConnection.setRequestProperty("User-Agent", 
                "Mozilla/5.0 (SeminarioPlugin) Minecraft/1.20.1");
            
            int responseCode = httpConnection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + responseCode + " for URL: " + imageUrl);
            }
            
            // Check content type
            String contentType = httpConnection.getContentType();
            if (contentType != null && !contentType.startsWith("image/")) {
                throw new IOException("Invalid content type: " + contentType);
            }
            
            // Check file size
            int contentLength = httpConnection.getContentLength();
            if (contentLength > MAX_FILE_SIZE) {
                throw new IOException("Image too large: " + contentLength + " bytes");
            }
        }
        
        try (InputStream inputStream = connection.getInputStream()) {
            // Read with size limit
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            int totalBytesRead = 0;
            
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                totalBytesRead += bytesRead;
                if (totalBytesRead > MAX_FILE_SIZE) {
                    throw new IOException("Image too large during download");
                }
                outputStream.write(buffer, 0, bytesRead);
            }
            
            byte[] imageBytes = outputStream.toByteArray();
            return ImageIO.read(new ByteArrayInputStream(imageBytes));
        }
    }
    
    /**
     * Resize an image to specified dimensions
     * @param originalImage The original image
     * @param width Target width
     * @param height Target height
     * @return Resized BufferedImage
     */
    private BufferedImage resizeImage(BufferedImage originalImage, int width, int height) {
        BufferedImage resizedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resizedImage.createGraphics();
        
        // Ultra high quality resizing for large displays (15x11 = 1920x1408)
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        
        graphics.drawImage(originalImage, 0, 0, width, height, null);
        graphics.dispose();
        
        return resizedImage;
    }
    
    /**
     * Validate if URL appears to be a valid image URL
     * @param url The URL to validate
     * @return true if valid format
     */
    private boolean isValidImageUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        
        url = url.toLowerCase().trim();
        
        // Must start with http/https
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return false;
        }
        
        // Check if URL ends with supported image extension
        for (String format : SUPPORTED_FORMATS) {
            if (url.endsWith("." + format)) {
                return true;
            }
        }
        
        // Allow URLs without extension (some image services don't show extensions)
        return true;
    }
    
    /**
     * Split a 256x256 image into 4 quadrants for 2x2 map display
     * @param image The 256x256 source image
     * @return Array of 4 BufferedImages [topLeft, topRight, bottomLeft, bottomRight]
     */
    public BufferedImage[] splitImageFor2x2Maps(BufferedImage image) {
        if (image.getWidth() != TARGET_SIZE || image.getHeight() != TARGET_SIZE) {
            throw new IllegalArgumentException("Image must be 256x256 pixels");
        }
        
        BufferedImage[] quadrants = new BufferedImage[4];
        int halfSize = TARGET_SIZE / 2; // 128 pixels
        
        // Top-left quadrant (Map 1)
        quadrants[0] = image.getSubimage(0, 0, halfSize, halfSize);
        
        // Top-right quadrant (Map 2)
        quadrants[1] = image.getSubimage(halfSize, 0, halfSize, halfSize);
        
        // Bottom-left quadrant (Map 3)
        quadrants[2] = image.getSubimage(0, halfSize, halfSize, halfSize);
        
        // Bottom-right quadrant (Map 4)
        quadrants[3] = image.getSubimage(halfSize, halfSize, halfSize, halfSize);
        
        return quadrants;
    }
    
    /**
     * Split image into 9 segments for 3x3 map display
     * Resizes image to 384x384 first, then splits into 128x128 segments
     * @param originalImage The original downloaded image
     * @return Array of 9 BufferedImages (128x128 each)
     */
    public BufferedImage[] splitImageFor3x3Maps(BufferedImage originalImage) {
        // Resize to 384x384 for optimal 3x3 division
        int targetSize = 384; // 3 * 128 = 384
        BufferedImage resizedImage = resizeImage(originalImage, targetSize, targetSize);
        
        BufferedImage[] segments = new BufferedImage[9];
        int segmentSize = 128; // Each map is 128x128
        
        // Split into 3x3 grid
        // Grid layout: [0][1][2]
        //              [3][4][5]
        //              [6][7][8]
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int index = row * 3 + col;
                int x = col * segmentSize;
                int y = row * segmentSize;
                
                segments[index] = resizedImage.getSubimage(x, y, segmentSize, segmentSize);
            }
        }
        
        return segments;
    }
    
    /**
     * Split image into 165 segments for 15x11 map display
     * Resizes image to 1920x1408 first, then splits into 128x128 segments
     * @param originalImage The original downloaded image
     * @return Array of 165 BufferedImages (128x128 each)
     */
    public BufferedImage[] splitImageFor15x11Maps(BufferedImage originalImage) {
        // Resize to 1920x1408 for optimal 15x11 division (15*128 x 11*128)
        int targetWidth = 1920;  // 15 * 128
        int targetHeight = 1408; // 11 * 128
        BufferedImage resizedImage = resizeImage(originalImage, targetWidth, targetHeight);
        
        BufferedImage[] segments = new BufferedImage[165];
        int segmentSize = 128; // Each map is 128x128
        
        // Split into 15x11 grid (15 columns, 11 rows)
        for (int row = 0; row < 11; row++) {
            for (int col = 0; col < 15; col++) {
                int index = row * 15 + col;
                int x = col * segmentSize;
                int y = row * segmentSize;
                
                segments[index] = resizedImage.getSubimage(x, y, segmentSize, segmentSize);
            }
        }
        
        return segments;
    }
    
    /**
     * Split image into 176 segments for 16x11 map display
     * OPTIMIZADO: Procesa a 4096x2816 (256x256 por segmento) para reducir uso de RAM
     * Luego reduce cada segmento a 128x128 con BICUBIC para buena calidad
     * Procesa en lotes de 32 segmentos para liberar memoria progresivamente
     * @param originalImage The original downloaded image
     * @return Array of 176 BufferedImages (128x128 each)
     */
    public BufferedImage[] splitImageFor16x11Maps(BufferedImage originalImage) {
        // OPTIMIZACIÓN: Reducir super-resolución a la mitad para ahorrar RAM
        // 4096x2816 = 11.5 MB en lugar de 46 MB (75% menos RAM)
        // Sigue siendo 256x256 por segmento antes de reducir, buena calidad
        int superWidth = 4096;   // 16 * 256 (antes 8192)
        int superHeight = 2816;  // 11 * 256 (antes 5632)
        
        LOGGER.info("Resizing image to " + superWidth + "x" + superHeight + " for optimal quality...");
        BufferedImage superResImage = resizeImage(originalImage, superWidth, superHeight);
        
        // Liberar imagen original inmediatamente
        if (originalImage != superResImage) {
            originalImage.flush();
            LOGGER.fine("Original image flushed from memory");
        }
        
        BufferedImage[] segments = new BufferedImage[176];
        int superSegmentSize = 256; // Cada segmento temporal es 256x256 (antes 512x512)
        int finalSegmentSize = 128; // Tamaño final 128x128
        
        LOGGER.info("Splitting into 16x11 segments and downsampling (processing in batches)...");
        
        // Procesar en lotes de 32 segmentos para liberar memoria progresivamente
        int batchSize = 32;
        for (int batchStart = 0; batchStart < 176; batchStart += batchSize) {
            int batchEnd = Math.min(batchStart + batchSize, 176);
            
            for (int index = batchStart; index < batchEnd; index++) {
                int row = index / 16;
                int col = index % 16;
                int x = col * superSegmentSize;
                int y = row * superSegmentSize;
                
                // Extraer segmento de 256x256
                BufferedImage superSegment = superResImage.getSubimage(x, y, superSegmentSize, superSegmentSize);
                
                // Reducir a 128x128 con BICUBIC para buena calidad
                segments[index] = resizeImage(superSegment, finalSegmentSize, finalSegmentSize);
                
                // Liberar el subimage temporal inmediatamente
                // Nota: getSubimage() crea una vista, no una copia, así que no consumimos memoria extra
            }
            
            // Sugerir garbage collection después de cada lote
            if ((batchEnd - batchStart) == batchSize) {
                System.gc();
                LOGGER.fine("Processed batch " + (batchStart / batchSize + 1) + "/" + ((176 + batchSize - 1) / batchSize));
            }
        }
        
        // Liberar memoria de la imagen super-res
        superResImage.flush();
        
        LOGGER.info("Successfully split image into 176 segments with memory optimization");
        return segments;
    }
    
    /**
     * Split image into 108 segments for 12x9 map display
     * Resizes image to 1536x1152 first, then splits into 128x128 segments
     * @param originalImage The original downloaded image
     * @return Array of 108 BufferedImages (128x128 each)
     * @deprecated Use splitImageFor16x11Maps instead
     */
    @Deprecated
    public BufferedImage[] splitImageFor12x9Maps(BufferedImage originalImage) {
        // Resize to 1280x1024 for optimal 10x8 division (10*128 x 8*128)
        int targetWidth = 1280;  // 10 * 128
        int targetHeight = 1024; // 8 * 128
        BufferedImage resizedImage = resizeImage(originalImage, targetWidth, targetHeight);
        
        BufferedImage[] segments = new BufferedImage[80];
        int segmentSize = 128; // Each map is 128x128
        
        // Split into 10x8 grid (10 columns, 8 rows)
        // Grid layout: [0 ][1 ][2 ][3 ][4 ][5 ][6 ][7 ][8 ][9 ]
        //              [10][11][12][13][14][15][16][17][18][19]
        //              [20][21][22][23][24][25][26][27][28][29]
        //              [30][31][32][33][34][35][36][37][38][39]
        //              [40][41][42][43][44][45][46][47][48][49]
        //              [50][51][52][53][54][55][56][57][58][59]
        //              [60][61][62][63][64][65][66][67][68][69]
        //              [70][71][72][73][74][75][76][77][78][79]
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 10; col++) {
                int index = row * 10 + col;
                int x = col * segmentSize;
                int y = row * segmentSize;
                
                segments[index] = resizedImage.getSubimage(x, y, segmentSize, segmentSize);
            }
        }
        
        return segments;
    }
    
    /**
     * Split image into 48 segments for 8x6 map display
     * Resizes image to 1024x768 first, then splits into 128x128 segments
     * @param originalImage The original downloaded image
     * @return Array of 48 BufferedImages (128x128 each)
     * @deprecated Use splitImageFor10x8Maps instead
     */
    @Deprecated
    public BufferedImage[] splitImageFor8x6Maps(BufferedImage originalImage) {
        // Resize to 1024x768 for optimal 8x6 division (8*128 x 6*128)
        int targetWidth = 1024;  // 8 * 128
        int targetHeight = 768; // 6 * 128
        BufferedImage resizedImage = resizeImage(originalImage, targetWidth, targetHeight);
        
        BufferedImage[] segments = new BufferedImage[48];
        int segmentSize = 128; // Each map is 128x128
        
        // Split into 8x6 grid (8 columns, 6 rows)
        // Grid layout: [0 ][1 ][2 ][3 ][4 ][5 ][6 ][7 ]
        //              [8 ][9 ][10][11][12][13][14][15]
        //              [16][17][18][19][20][21][22][23]
        //              [24][25][26][27][28][29][30][31]
        //              [32][33][34][35][36][37][38][39]
        //              [40][41][42][43][44][45][46][47]
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 8; col++) {
                int index = row * 8 + col;
                int x = col * segmentSize;
                int y = row * segmentSize;
                
                segments[index] = resizedImage.getSubimage(x, y, segmentSize, segmentSize);
            }
        }
        
        return segments;
    }
    
    /**
     * Split image into 30 segments for 6x5 map display
     * Resizes image to 768x640 first, then splits into 128x128 segments
     * @param originalImage The original downloaded image
     * @return Array of 30 BufferedImages (128x128 each)
     * @deprecated Use splitImageFor8x6Maps instead
     */
    @Deprecated
    public BufferedImage[] splitImageFor6x5Maps(BufferedImage originalImage) {
        // Resize to 768x640 for optimal 6x5 division (6*128 x 5*128)
        int targetWidth = 768;  // 6 * 128
        int targetHeight = 640; // 5 * 128
        BufferedImage resizedImage = resizeImage(originalImage, targetWidth, targetHeight);
        
        BufferedImage[] segments = new BufferedImage[30];
        int segmentSize = 128; // Each map is 128x128
        
        // Split into 6x5 grid (6 columns, 5 rows)
        // Grid layout: [0 ][1 ][2 ][3 ][4 ][5 ]
        //              [6 ][7 ][8 ][9 ][10][11]
        //              [12][13][14][15][16][17]
        //              [18][19][20][21][22][23]
        //              [24][25][26][27][28][29]
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 6; col++) {
                int index = row * 6 + col;
                int x = col * segmentSize;
                int y = row * segmentSize;
                
                segments[index] = resizedImage.getSubimage(x, y, segmentSize, segmentSize);
            }
        }
        
        return segments;
    }
    
    /**
     * Split image into 16 segments for 4x4 map display
     * Resizes image to 512x512 first, then splits into 128x128 segments
     * @param originalImage The original downloaded image
     * @return Array of 16 BufferedImages (128x128 each)
     * @deprecated Use splitImageFor6x5Maps instead
     */
    @Deprecated
    public BufferedImage[] splitImageFor4x4Maps(BufferedImage originalImage) {
        // Resize to 512x512 for optimal 4x4 division
        int targetSize = 512; // 4 * 128 = 512
        BufferedImage resizedImage = resizeImage(originalImage, targetSize, targetSize);
        
        BufferedImage[] segments = new BufferedImage[16];
        int segmentSize = 128; // Each map is 128x128
        
        // Split into 4x4 grid
        // Grid layout: [0 ][1 ][2 ][3 ]
        //              [4 ][5 ][6 ][7 ]
        //              [8 ][9 ][10][11]
        //              [12][13][14][15]
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                int index = row * 4 + col;
                int x = col * segmentSize;
                int y = row * segmentSize;
                
                segments[index] = resizedImage.getSubimage(x, y, segmentSize, segmentSize);
            }
        }
        
        return segments;
    }
    
    /**
     * Generate a safe filename from URL
     * @param url The image URL
     * @param slideNumber The slide number
     * @return Safe filename for caching
     */
    public String generateCacheFilename(String url, int slideNumber) {
        String filename = "slide_" + slideNumber + "_" + Math.abs(url.hashCode()) + ".png";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
    
    /**
     * Clear cached images older than specified days
     * @param days Number of days
     * @return Number of files deleted
     */
    public int clearOldCache(int days) {
        if (!cacheDir.exists()) {
            return 0;
        }
        
        long cutoffTime = System.currentTimeMillis() - (days * 24L * 60L * 60L * 1000L);
        int deletedCount = 0;
        
        File[] files = cacheDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.lastModified() < cutoffTime) {
                    if (file.delete()) {
                        deletedCount++;
                    }
                }
            }
        }
        
        LOGGER.info("Cleared " + deletedCount + " old cached images");
        return deletedCount;
    }
    
    /**
     * Get cache directory
     * @return Cache directory file
     */
    public File getCacheDir() {
        return cacheDir;
    }
    
    /**
     * Get the local slide image file if it exists
     * Looks for: slides_src/<zoneName>/<slideNumber>.png
     * @param zoneName The zone name
     * @param slideNumber The slide number
     * @return File object if exists, null otherwise
     */
    private File getLocalSlideImageFile(String zoneName, int slideNumber) {
        if (zoneName == null || zoneName.trim().isEmpty() || slideNumber <= 0) {
            return null;
        }
        
        // Create zone directory path
        File zoneDir = new File(slidesSrcDir, zoneName);
        if (!zoneDir.exists() || !zoneDir.isDirectory()) {
            return null;
        }
        
        // Check for <slideNumber>.png
        File imageFile = new File(zoneDir, slideNumber + ".png");
        if (imageFile.exists() && imageFile.isFile()) {
            return imageFile;
        }
        
        return null;
    }
}