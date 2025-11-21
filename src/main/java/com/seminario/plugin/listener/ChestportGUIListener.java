package com.seminario.plugin.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

import com.seminario.plugin.gui.ChestportGUI;

/**
 * Handles GUI inventory events for chestport menus
 */
public class ChestportGUIListener implements Listener {
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        
        // Check if this is a chestport GUI
        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (title.contains("¿Quieres ser transportado?")) {
            event.setCancelled(true); // Cancel the click to prevent item manipulation
            ChestportGUI.handleGUIClick(player, event.getSlot());
        }
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getPlayer();
        
        // Check if this was a chestport GUI being closed
        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (title.contains("¿Quieres ser transportado?")) {
            // Clean up the session if player closes GUI without selecting
            ChestportGUI.cleanupSession(player);
        }
    }
}