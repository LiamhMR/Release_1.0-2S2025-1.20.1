package com.seminario.plugin.model;

import java.util.List;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Represents an active slideshow session for a player
 * Tracks current slide, zone, and presentation state
 */
public class PlayerSlideSession {
    
    private final UUID playerId;
    private final String playerName;
    private final String zoneName;
    private final List<Slide> slides;
    
    private int currentSlideIndex;
    private boolean isActive;
    private long sessionStartTime;
    private long lastSlideChangeTime;
    
    // Flag to indicate if a slide is currently being processed
    private boolean processingSlide;
    
    // Store original inventory items that were replaced
    private ItemStack[] originalHotbar;
    
    public PlayerSlideSession(Player player, String zoneName, List<Slide> slides) {
        this.playerId = player.getUniqueId();
        this.playerName = player.getName();
        this.zoneName = zoneName;
        this.slides = slides;
        this.currentSlideIndex = 0;
        this.isActive = false;
        this.processingSlide = false;
        this.sessionStartTime = System.currentTimeMillis();
        this.lastSlideChangeTime = sessionStartTime;
        
        // Store original hotbar (first 9 slots)
        this.originalHotbar = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null) {
                originalHotbar[i] = item.clone();
            }
        }
    }
    
    // Getters
    public UUID getPlayerId() {
        return playerId;
    }
    
    public String getPlayerName() {
        return playerName;
    }
    
    public String getZoneName() {
        return zoneName;
    }
    
    public List<Slide> getSlides() {
        return slides;
    }
    
    public int getCurrentSlideIndex() {
        return currentSlideIndex;
    }
    
    public int getCurrentSlideNumber() {
        return currentSlideIndex + 1;
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    public long getSessionStartTime() {
        return sessionStartTime;
    }
    
    public long getLastSlideChangeTime() {
        return lastSlideChangeTime;
    }
    
    public ItemStack[] getOriginalHotbar() {
        return originalHotbar.clone();
    }
    
    public boolean isProcessingSlide() {
        return processingSlide;
    }
    
    // Setters
    public void setActive(boolean active) {
        this.isActive = active;
        if (active && sessionStartTime == 0) {
            this.sessionStartTime = System.currentTimeMillis();
        }
    }
    
    public void setCurrentSlideIndex(int slideIndex) {
        if (slideIndex >= 0 && slideIndex < slides.size()) {
            this.currentSlideIndex = slideIndex;
            this.lastSlideChangeTime = System.currentTimeMillis();
        }
    }
    
    public void setProcessingSlide(boolean processing) {
        this.processingSlide = processing;
    }
    
    // Navigation methods
    public boolean hasSlides() {
        return slides != null && !slides.isEmpty();
    }
    
    public int getTotalSlides() {
        return slides != null ? slides.size() : 0;
    }
    
    public Slide getCurrentSlide() {
        if (!hasSlides() || currentSlideIndex < 0 || currentSlideIndex >= slides.size()) {
            return null;
        }
        return slides.get(currentSlideIndex);
    }
    
    public boolean hasNextSlide() {
        return currentSlideIndex < slides.size() - 1;
    }
    
    public boolean hasPreviousSlide() {
        return currentSlideIndex > 0;
    }
    
    public boolean nextSlide() {
        if (hasNextSlide()) {
            currentSlideIndex++;
            lastSlideChangeTime = System.currentTimeMillis();
            return true;
        }
        return false;
    }
    
    public boolean previousSlide() {
        if (hasPreviousSlide()) {
            currentSlideIndex--;
            lastSlideChangeTime = System.currentTimeMillis();
            return true;
        }
        return false;
    }
    
    public void goToSlide(int slideIndex) {
        if (slideIndex >= 0 && slideIndex < slides.size()) {
            this.currentSlideIndex = slideIndex;
            this.lastSlideChangeTime = System.currentTimeMillis();
        }
    }
    
    public void goToFirstSlide() {
        goToSlide(0);
    }
    
    public void goToLastSlide() {
        goToSlide(slides.size() - 1);
    }
    
    // Session info methods
    public long getSessionDuration() {
        return System.currentTimeMillis() - sessionStartTime;
    }
    
    public long getTimeSinceLastSlideChange() {
        return System.currentTimeMillis() - lastSlideChangeTime;
    }
    
    public String getProgressString() {
        return String.format("Slide %d/%d", getCurrentSlideNumber(), getTotalSlides());
    }
    
    public double getProgressPercentage() {
        if (getTotalSlides() == 0) return 0.0;
        return ((double) getCurrentSlideNumber() / getTotalSlides()) * 100.0;
    }
    
    // Utility methods
    public boolean isFirstSlide() {
        return currentSlideIndex == 0;
    }
    
    public boolean isLastSlide() {
        return currentSlideIndex == slides.size() - 1;
    }
    
    public boolean isValidSlideIndex(int index) {
        return index >= 0 && index < slides.size();
    }
    
    @Override
    public String toString() {
        return String.format("SlideSession{player='%s', zone='%s', slide=%d/%d, active=%s}", 
            playerName, zoneName, getCurrentSlideNumber(), getTotalSlides(), isActive);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        PlayerSlideSession session = (PlayerSlideSession) obj;
        return playerId.equals(session.playerId) && zoneName.equals(session.zoneName);
    }
    
    @Override
    public int hashCode() {
        return playerId.hashCode() * 31 + zoneName.hashCode();
    }
}