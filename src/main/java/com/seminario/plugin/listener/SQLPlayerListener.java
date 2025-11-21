package com.seminario.plugin.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import com.seminario.plugin.manager.SQLDungeonManager;
import com.seminario.plugin.manager.SpawnpointManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class SQLPlayerListener implements Listener {
    private final SQLDungeonManager sqlDungeonManager;
    private final SpawnpointManager spawnpointManager;

    public SQLPlayerListener(SQLDungeonManager sqlDungeonManager, SpawnpointManager spawnpointManager) {
        this.sqlDungeonManager = sqlDungeonManager;
        this.spawnpointManager = spawnpointManager;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        // Only handle right-clicks
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // Prevent off-hand duplicate
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        // Check if player is currently in a SQL dungeon session
        if (!sqlDungeonManager.isPlayerInSession(player)) {
            return;
        }

        // Check item in main hand
        if (player.getInventory().getItemInMainHand() == null) return;
        String displayName = "";
        if (player.getInventory().getItemInMainHand().getItemMeta() != null && player.getInventory().getItemInMainHand().getItemMeta().hasDisplayName()) {
            // Use legacy method to get display name as string for now
            displayName = org.bukkit.ChatColor.stripColor(player.getInventory().getItemInMainHand().getItemMeta().getDisplayName());
        }

        // Use displayName contains because we used Adventure components; fallbacks supported
        if (displayName != null && displayName.toLowerCase().contains("super salto")) {
            event.setCancelled(true);
            boolean used = sqlDungeonManager.useSuperJump(player);
            if (used) {
                player.sendMessage(Component.text("¡Super Salto activado!", NamedTextColor.GOLD));
            } else {
                player.sendMessage(Component.text("No tienes Super Saltos disponibles.", NamedTextColor.RED));
            }
            return;
        }

        if (displayName != null && displayName.toLowerCase().contains("invocador de profesor")) {
            event.setCancelled(true);
            sqlDungeonManager.invokeProfessor(player);
            return;
        }

        if (displayName != null && displayName.toLowerCase().contains("salir al lobby")) {
            event.setCancelled(true);
            // Teleport player to spawnpoint with effects and lobby setup
            spawnpointManager.teleportToSpawnpoint(player, true, true);
            // Clear player's SQL session
            sqlDungeonManager.clearPlayerSession(player);
            player.sendMessage(Component.text("Has sido enviado al lobby.", NamedTextColor.GREEN));
            return;
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Nothing specific for now; leave cleanup to SQLDungeonManager
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        
        // Check if the entity is a villager
        if (!(event.getRightClicked() instanceof org.bukkit.entity.Villager)) {
            return;
        }
        
        org.bukkit.entity.Villager villager = (org.bukkit.entity.Villager) event.getRightClicked();
        
        // Check if this is a SQL professor villager
        if (villager.getPersistentDataContainer().has(
            new org.bukkit.NamespacedKey(sqlDungeonManager.getPlugin(), "sql_professor"), 
            org.bukkit.persistence.PersistentDataType.STRING)) {
            
            event.setCancelled(true);
            sqlDungeonManager.handleProfessorInteraction(player, villager);
        }
    }



    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Nothing specific here for SQL items
    }
}
