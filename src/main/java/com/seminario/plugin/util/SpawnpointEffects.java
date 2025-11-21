package com.seminario.plugin.util;

import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Utility class for spawnpoint special effects
 */
public class SpawnpointEffects {
    
    /**
     * Play spawnpoint effects for a player
     * @param player The player to play effects for
     * @param plugin The plugin instance for scheduling
     */
    public static void playSpawnpointEffects(Player player, JavaPlugin plugin) {
        // Play triumphant music sound
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        
        // Schedule fireworks and music over 5 seconds
        new BukkitRunnable() {
            private int ticksRun = 0;
            private final int totalTicks = 100; // 5 seconds (20 ticks per second)
            
            @Override
            public void run() {
                // Launch fireworks every 20 ticks (1 second)
                if (ticksRun % 20 == 0) {
                    launchCelestialFirework(player.getLocation());
                }
                
                // Play different musical notes at intervals
                if (ticksRun == 0) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.0f);
                } else if (ticksRun == 10) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.2f);
                } else if (ticksRun == 20) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.5f);
                } else if (ticksRun == 40) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
                } else if (ticksRun == 60) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 0.8f);
                } else if (ticksRun == 80) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.3f);
                }
                
                ticksRun++;
                
                // Stop after 5 seconds
                if (ticksRun >= totalTicks) {
                    // Final triumphant sound
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
    
    /**
     * Launch a celestial-colored firework at the given location
     * @param location The location to launch the firework
     */
    private static void launchCelestialFirework(Location location) {
        if (location.getWorld() == null) {
            return;
        }
        
        // Create firework entity
        Firework firework = (Firework) location.getWorld().spawnEntity(
            location.clone().add(0, 1, 0), EntityType.FIREWORK
        );
        
        // Configure firework meta
        FireworkMeta meta = firework.getFireworkMeta();
        
        // Create celestial colors (light blue, white, cyan)
        FireworkEffect effect = FireworkEffect.builder()
            .withColor(Color.AQUA, Color.WHITE, Color.BLUE)
            .withFade(Color.SILVER)
            .with(FireworkEffect.Type.BURST)
            .trail(true)
            .flicker(true)
            .build();
        
        meta.addEffect(effect);
        meta.setPower(1); // Medium height
        
        firework.setFireworkMeta(meta);
    }
    
    /**
     * Play a welcome melody for player joining the server
     * @param player The player
     */
    public static void playWelcomeSound(Player player) {
        // Play a welcoming melody over 3 seconds
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.0f);
        
        // Schedule additional notes to create a welcome melody
        player.getServer().getScheduler().runTaskLater(
            (JavaPlugin) player.getServer().getPluginManager().getPlugin("SeminarioPlugin"), () -> {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.2f);
            }, 8L);
        
        player.getServer().getScheduler().runTaskLater(
            (JavaPlugin) player.getServer().getPluginManager().getPlugin("SeminarioPlugin"), () -> {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.5f);
            }, 16L);
        
        player.getServer().getScheduler().runTaskLater(
            (JavaPlugin) player.getServer().getPluginManager().getPlugin("semi"), () -> {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 1.0f, 1.0f);
            }, 24L);
        
        player.getServer().getScheduler().runTaskLater(
            (JavaPlugin) player.getServer().getPluginManager().getPlugin("semi"), () -> {
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);
            }, 32L);
    }
    
    /**
     * Play welcome melody with plugin reference for better scheduling
     * @param player The player
     * @param plugin The plugin instance
     */
    public static void playWelcomeMelody(Player player, JavaPlugin plugin) {
        // Play a welcoming melody over 3 seconds
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.0f);
        
        // Schedule additional notes to create a welcome melody
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.2f);
        }, 8L);
        
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.5f);
        }, 16L);
        
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 1.0f, 1.0f);
        }, 24L);
        
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);
        }, 32L);
    }
}