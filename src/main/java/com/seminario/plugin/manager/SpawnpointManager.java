package com.seminario.plugin.manager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.seminario.plugin.config.ConfigManager;
import com.seminario.plugin.util.SpawnpointEffects;

/**
 * Manages server spawnpoint functionality
 */
public class SpawnpointManager {
    
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    
    public SpawnpointManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }
    
    /**
     * Teleport player to server spawnpoint with effects
     * @param player The player to teleport
     * @param playEffects Whether to play special effects
     */
    public void teleportToSpawnpoint(Player player, boolean playEffects) {
        teleportToSpawnpoint(player, playEffects, true);
    }
    
    /**
     * Teleport player to server spawnpoint with effects and optional lobby setup
     * @param player The player to teleport
     * @param playEffects Whether to play special effects
     * @param setupLobby Whether to setup lobby inventory and Adventure mode
     */
    public void teleportToSpawnpoint(Player player, boolean playEffects, boolean setupLobby) {
        Location spawnpoint = getSpawnpointLocation();
        
        if (spawnpoint == null) {
            player.sendMessage("§cError: No se ha establecido un spawnpoint del servidor.");
            return;
        }
        
        // Teleport player
        player.teleport(spawnpoint);
        
        if (playEffects) {
            // Play special effects
            SpawnpointEffects.playSpawnpointEffects(player, plugin);
        } else {
            // Just play a simple welcome sound
            SpawnpointEffects.playWelcomeMelody(player, plugin);
        }
        
        if (setupLobby) {
            // Give lobby inventory and set Adventure mode after a short delay
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                // Get LobbyManager from main plugin
                com.seminario.plugin.App mainPlugin = (com.seminario.plugin.App) plugin;
                mainPlugin.getLobbyManager().giveLobbyInventoryToPlayer(player, true);
            }, 5L); // Small delay to ensure teleport is complete
        }
    }
    
    /**
     * Get the spawnpoint location
     * @return The spawnpoint location, or world spawn if not set
     */
    public Location getSpawnpointLocation() {
        // Try to get custom server spawnpoint
        Location customSpawn = configManager.getServerSpawnpoint();
        if (customSpawn != null) {
            return customSpawn;
        }
        
        // Fallback to world spawn
        org.bukkit.World world = Bukkit.getWorld("world");
        if (world != null) {
            return world.getSpawnLocation();
        }
        
        // Ultimate fallback - first available world
        if (!Bukkit.getWorlds().isEmpty()) {
            return Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        
        return null;
    }
    
    /**
     * Check if custom spawnpoint is set
     * @return true if custom spawnpoint is configured
     */
    public boolean hasCustomSpawnpoint() {
        return configManager.hasServerSpawnpoint();
    }
    
    /**
     * Set the server spawnpoint
     * @param location The location to set as spawnpoint
     */
    public void setSpawnpoint(Location location) {
        configManager.setServerSpawnpoint(location);
    }
}