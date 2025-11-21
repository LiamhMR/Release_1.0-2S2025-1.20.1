package com.seminario.plugin.listener;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import com.seminario.plugin.manager.LobbyManager;

/**
 * Handles player interactions with lobby items
 */
public class LobbyPlayerListener implements Listener {

    private final LobbyManager lobbyManager;

    public LobbyPlayerListener(LobbyManager lobbyManager) {
        this.lobbyManager = lobbyManager;
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
    }
}