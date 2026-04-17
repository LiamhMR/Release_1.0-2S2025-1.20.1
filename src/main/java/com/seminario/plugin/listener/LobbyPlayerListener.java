package com.seminario.plugin.listener;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import com.seminario.plugin.manager.LobbyManager;
import com.seminario.plugin.manager.SpawnpointManager;

/**
 * Handles player interactions with lobby items
 */
public class LobbyPlayerListener implements Listener {

    private final LobbyManager lobbyManager;
    private final SpawnpointManager spawnpointManager;
    private final JavaPlugin plugin;

    public LobbyPlayerListener(LobbyManager lobbyManager, SpawnpointManager spawnpointManager, JavaPlugin plugin) {
        this.lobbyManager = lobbyManager;
        this.spawnpointManager = spawnpointManager;
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // Check if player has lobby inventory (is in lobby mode)
        if (!lobbyManager.hasLobbyInventory(player)) {
            return;
        }

        if (item == null || !item.hasItemMeta()) {
            return;
        }

        // Check for right-click actions
        if (!event.getAction().name().contains("RIGHT_CLICK")) {
            return;
        }

        String displayName = item.getItemMeta().getDisplayName();

        // Handle Super Jump Star (Nether Star)
        if (item.getType() == Material.NETHER_STAR && displayName.contains("Super Salto del Lobby")) {
            event.setCancelled(true);
            lobbyManager.useLobbySuperJump(player);
            return;
        }

        // Handle Positioning Compass
        if (item.getType() == Material.COMPASS && displayName.contains("Brújula de Posicionamiento")) {
            event.setCancelled(true);
            lobbyManager.usePositioningCompass(player);
            return;
        }

        // Handle Quest selector item
        if (item.getType() == Material.WRITABLE_BOOK && displayName.contains("QUEST")) {
            event.setCancelled(true);
            lobbyManager.openQuestSelector(player);
            return;
        }
    }

    // ── Inventory protection ──────────────────────────────────────────────────

    /**
     * Prevent players from moving lobby items within their inventory or to other containers.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!lobbyManager.hasLobbyInventory(player)) return;
        if (event.getClickedInventory() == player.getInventory()) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevent drag-splitting items across the lobby inventory.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!lobbyManager.hasLobbyInventory(player)) return;
        int topSize = event.getView().getTopInventory().getSize();
        for (int slot : event.getRawSlots()) {
            if (slot >= topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * Prevent players from dropping lobby items (Q key or drag out of window).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (lobbyManager.hasLobbyInventory(player)) {
            event.setCancelled(true);
        }
    }

    // ── Lobby death handling ──────────────────────────────────────────────────

    /**
     * When a player dies in the lobby world: suppress the death screen,
     * keep inventory, and schedule an instant respawn.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!lobbyManager.isLobbyWorld(player.getWorld())) return;

        event.setDeathMessage(null);
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.spigot().respawn();
            }
        }, 1L);
    }

    /**
     * After the instant respawn, set the respawn location to spawn.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!lobbyManager.isLobbyWorld(player.getWorld())) return;

        org.bukkit.Location spawnLoc = spawnpointManager.getSpawnpointLocation();
        if (spawnLoc != null) {
            event.setRespawnLocation(spawnLoc);
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                lobbyManager.giveLobbyInventoryToPlayer(player);
            }
        }, 1L);
    }
}
