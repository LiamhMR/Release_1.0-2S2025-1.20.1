package com.seminario.plugin.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import com.seminario.plugin.manager.LobbyManager;
import com.seminario.plugin.manager.SpawnpointManager;
import com.seminario.plugin.manager.TutorialSQLPresentationManager;

/**
 * Handles player interactions with lobby items
 */
public class LobbyPlayerListener implements Listener {

    private final LobbyManager lobbyManager;
    private final SpawnpointManager spawnpointManager;
    private final TutorialSQLPresentationManager tutorialSQLPresentationManager;
    private final JavaPlugin plugin;

    public LobbyPlayerListener(LobbyManager lobbyManager, SpawnpointManager spawnpointManager, TutorialSQLPresentationManager tutorialSQLPresentationManager, JavaPlugin plugin) {
        this.lobbyManager = lobbyManager;
        this.spawnpointManager = spawnpointManager;
        this.tutorialSQLPresentationManager = tutorialSQLPresentationManager;
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

        String actionName = event.getAction().name();
        boolean isRightClick = actionName.contains("RIGHT_CLICK");
        boolean isLeftClick = actionName.contains("LEFT_CLICK");

        if (tutorialSQLPresentationManager.hasActivePresentation(player)
                && tutorialSQLPresentationManager.isControllerItem(item)) {
            event.setCancelled(true);

            if (isRightClick) {
                tutorialSQLPresentationManager.advanceSlide(player);
                return;
            }

            if (isLeftClick) {
                tutorialSQLPresentationManager.closePresentation(player);
                return;
            }
        }

        if (!isRightClick) {
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

        if (item.getType() == Material.PAPER && displayName.contains("Tutorial SQL")) {
            event.setCancelled(true);
            tutorialSQLPresentationManager.openPresentation(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!tutorialSQLPresentationManager.hasActivePresentation(player)) {
            return;
        }

        if (!tutorialSQLPresentationManager.isPresentationFrame(event.getRightClicked(), player)) {
            return;
        }

        event.setCancelled(true);
        tutorialSQLPresentationManager.advanceSlide(player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }

        if (!tutorialSQLPresentationManager.hasActivePresentation(player)) {
            return;
        }

        if (!tutorialSQLPresentationManager.isPresentationFrame(event.getEntity(), player)) {
            return;
        }

        event.setCancelled(true);
        tutorialSQLPresentationManager.closePresentation(player);
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

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!tutorialSQLPresentationManager.hasActivePresentation(player)) {
            return;
        }

        tutorialSQLPresentationManager.handleHeldItemChange(player, event.getNewSlot());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!tutorialSQLPresentationManager.hasActivePresentation(player)) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) {
            return;
        }

        tutorialSQLPresentationManager.handlePlayerMovement(player, to);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        tutorialSQLPresentationManager.clearSession(event.getPlayer());
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
                tutorialSQLPresentationManager.clearSession(player);
                lobbyManager.giveLobbyInventoryToPlayer(player);
            }
        }, 1L);
    }
}
