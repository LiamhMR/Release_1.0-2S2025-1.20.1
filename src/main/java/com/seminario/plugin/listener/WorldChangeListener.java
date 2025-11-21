package com.seminario.plugin.listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import com.seminario.plugin.App;
import com.seminario.plugin.manager.LobbyManager;

public class WorldChangeListener implements Listener {
    
    private final App plugin;
    private final LobbyManager lobbyManager;
    
    // Track recent joins to avoid conflicts
    private final Map<UUID, Long> recentJoins = new HashMap<>();
    
    public WorldChangeListener(App plugin, LobbyManager lobbyManager) {
        this.plugin = plugin;
        this.lobbyManager = lobbyManager;
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Track when player joins to avoid WorldChange conflicts
        recentJoins.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
        
        // Clean up after 5 seconds
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            recentJoins.remove(event.getPlayer().getUniqueId());
        }, 100L); // 5 seconds
    }
    
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        
        // Skip if player recently joined (avoid conflicts with PlayerJoinListener)
        if (recentJoins.containsKey(player.getUniqueId())) {
            Long joinTime = recentJoins.get(player.getUniqueId());
            if (System.currentTimeMillis() - joinTime < 3000) { // 3 seconds grace period
                plugin.getLogger().info("WorldChangeListener: Skipping lobby inventory for " + player.getName() + " (recent join)");
                return;
            }
        }
        
        // Check if the player entered the lobby world
        if (lobbyManager.isLobbyWorld(player.getWorld())) {
            plugin.getLogger().info("WorldChangeListener: Player " + player.getName() + " entered lobby world");
            
            // Give lobby inventory after a short delay to ensure player is fully loaded
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                plugin.getLogger().info("WorldChangeListener: Giving lobby inventory to " + player.getName());
                lobbyManager.giveLobbyInventoryToPlayer(player);
            }, 10L); // 0.5 seconds delay
        }
    }
}