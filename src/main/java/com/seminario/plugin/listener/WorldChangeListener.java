package com.seminario.plugin.listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

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
        Player player = event.getPlayer();

        // Track when player joins to avoid WorldChange conflicts
        recentJoins.put(player.getUniqueId(), System.currentTimeMillis());

        // If the player joins directly in a SQL Battle world, enforce its entry rules.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (plugin.getConfigManager().isSQLBattle(player.getWorld().getName())) {
                preparePlayerForSQLBattleWorld(player);
            }
        }, 5L);
        
        // Clean up after 5 seconds
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            recentJoins.remove(player.getUniqueId());
        }, 100L); // 5 seconds
    }
    
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        boolean leftSqlBattleWorld = plugin.getConfigManager().isSQLBattle(event.getFrom().getName())
            && !plugin.getConfigManager().isSQLBattle(player.getWorld().getName());

        if (leftSqlBattleWorld) {
            plugin.getSQLBattleManager().cleanupPlayerSession(player);
        }

        if (plugin.getConfigManager().isSQLBattle(player.getWorld().getName())) {
            plugin.getLogger().info("WorldChangeListener: Player " + player.getName() + " entered SQL Battle world");
            preparePlayerForSQLBattleWorld(player);
            return;
        }
        
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

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        if (!plugin.getConfigManager().isSQLBattle(player.getWorld().getName())) {
            return;
        }

        ItemStack inHand = event.getItem();
        if (plugin.getSQLBattleManager().isPrewaveLeaveItem(inHand)) {
            event.setCancelled(true);
            if (!plugin.getSQLBattleManager().leaveBattleSessionFromItem(player)) {
                player.sendMessage("§cNo tienes una sesion activa para abandonar.");
            }
            return;
        }

        if (!plugin.getSQLBattleManager().isPrewaveStartItem(inHand)) {
            return;
        }

        event.setCancelled(true);
        if (plugin.getSQLBattleManager().forceStartWaveFromPreparation(player)) {
            player.sendMessage("§a¡Voto registrado para iniciar la oleada!");
        } else {
            player.sendMessage("§cNo se pudo registrar tu voto de prewave.");
        }
    }

    private void preparePlayerForSQLBattleWorld(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        player.setGameMode(GameMode.ADVENTURE);
        plugin.getSQLBattleManager().clearPrewaveStartItem(player);
        if (!plugin.getSQLBattleManager().startForPlayer(player)) {
            player.sendMessage("§cNo se pudo registrar al jugador en SQL Battle.");
        } else {
            // Re-apply spectator mode next tick in case another listener overrides gamemode on world change.
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (!plugin.getConfigManager().isSQLBattle(player.getWorld().getName())) {
                    return;
                }
                if (plugin.getSQLBattleManager().isPlayerBattleSpectator(player)) {
                    player.setGameMode(GameMode.SPECTATOR);
                }
            }, 1L);
        }
        player.updateInventory();
    }
}