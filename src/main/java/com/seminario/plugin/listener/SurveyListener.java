package com.seminario.plugin.listener;

import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.meta.FireworkMeta;

import com.seminario.plugin.manager.SurveyManager;
import com.seminario.plugin.model.SurveySession;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Handles player interactions with survey item frames
 */
public class SurveyListener implements Listener {

    private final SurveyManager surveyManager;

    public SurveyListener(SurveyManager surveyManager) {
        this.surveyManager = surveyManager;
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Handle breaking item frames (right-click or punch)
        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof ItemFrame)) {
            return;
        }

        Player player = (Player) event.getDamager();
        ItemFrame frame = (ItemFrame) event.getEntity();

        handleFrameInteraction(player, frame, event);
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        // Handle right-clicking item frames
        if (!(event.getRightClicked() instanceof ItemFrame)) {
            return;
        }

        Player player = event.getPlayer();
        ItemFrame frame = (ItemFrame) event.getRightClicked();

        handleFrameInteraction(player, frame, event);
    }

    private void handleFrameInteraction(Player player, ItemFrame frame, org.bukkit.event.Cancellable event) {
        // Check if player has an active survey session
        if (!surveyManager.hasActiveSession(player)) {
            return;
        }

        SurveySession session = surveyManager.getActiveSession(player);
        if (session == null || !session.isActive()) {
            return;
        }

        // Check if this frame belongs to the player's survey session
        if (!session.getFaceFrames().contains(frame)) {
            return;
        }

        // Cancel the event to prevent normal item frame behavior
        event.setCancelled(true);

        // Determine which rating was selected (1-5 based on frame position)
        int rating = session.getFaceFrames().indexOf(frame) + 1;

        // Provide feedback to player
        String[] ratingDescriptions = {
            "Muy insatisfecho", // 1
            "Insatisfecho",     // 2
            "Neutral",          // 3
            "Satisfecho",       // 4
            "Muy satisfecho"    // 5
        };

        player.sendMessage(Component.text("Respuesta seleccionada: " + rating + " - " + ratingDescriptions[rating - 1], 
                                        NamedTextColor.AQUA));

        // Play selection sound
        playSelectionSound(player, rating);
        
        // Spawn small colorful firework
        spawnSelectionFirework(frame.getLocation(), rating);

        // Handle the response
        surveyManager.handleResponse(player, rating);
    }
    
    /**
     * Play selection sound based on rating
     */
    private void playSelectionSound(Player player, int rating) {
        // Different sounds for different ratings
        Sound[] selectionSounds = {
            Sound.BLOCK_NOTE_BLOCK_BASS,     // Very sad (1) - low sound
            Sound.BLOCK_NOTE_BLOCK_SNARE,    // Sad (2) - drum sound
            Sound.BLOCK_NOTE_BLOCK_HAT,      // Neutral (3) - hat sound
            Sound.BLOCK_NOTE_BLOCK_BELL,     // Happy (4) - bell sound
            Sound.BLOCK_NOTE_BLOCK_CHIME     // Very happy (5) - chime sound
        };
        
        // Different pitches for variety
        float[] pitches = {0.8f, 0.9f, 1.0f, 1.1f, 1.2f};
        
        player.playSound(player.getLocation(), selectionSounds[rating - 1], 0.7f, pitches[rating - 1]);
        
        // Additional positive sound for higher ratings
        if (rating >= 4) {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
        }
    }
    
    /**
     * Spawn small colorful firework at selection location
     */
    private void spawnSelectionFirework(Location location, int rating) {
        // Colors based on rating
        Color[] ratingColors = {
            Color.RED,      // Very sad (1)
            Color.ORANGE,   // Sad (2)
            Color.YELLOW,   // Neutral (3)
            Color.LIME,     // Happy (4)
            Color.GREEN     // Very happy (5)
        };
        
        // Spawn firework slightly above the item frame
        Location fireworkLocation = location.clone().add(0, 0.5, 0);
        Firework firework = (Firework) location.getWorld().spawnEntity(fireworkLocation, EntityType.FIREWORK);
        
        FireworkMeta fireworkMeta = firework.getFireworkMeta();
        
        // Create firework effect
        FireworkEffect effect = FireworkEffect.builder()
            .withColor(ratingColors[rating - 1])
            .withFade(Color.WHITE)
            .with(FireworkEffect.Type.BALL)
            .trail(false)
            .flicker(true)
            .build();
        
        fireworkMeta.addEffect(effect);
        fireworkMeta.setPower(0); // Small firework
        
        firework.setFireworkMeta(fireworkMeta);
        
        // Detonate immediately for instant effect
        firework.detonate();
    }
}