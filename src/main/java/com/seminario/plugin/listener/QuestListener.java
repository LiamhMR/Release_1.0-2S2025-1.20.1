package com.seminario.plugin.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.seminario.plugin.manager.QuestManager;

public class QuestListener implements Listener {

    private final QuestManager questManager;

    public QuestListener(QuestManager questManager) {
        this.questManager = questManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (questManager.isQuestSelectorView(event.getView())) {
            if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
                event.setCancelled(true);
                return;
            }
            event.setCancelled(true);
            questManager.handleQuestSelectorClick(player, event.getRawSlot());
            return;
        }

        if (!questManager.isQuestActive(player)) {
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        questManager.handleQuestInventoryClick(player, event.getRawSlot(), event.getClick());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        if (questManager.isQuestSelectorView(event.getView())) {
            questManager.handleQuestSelectorClose(player);
            return;
        }

        if (!questManager.isQuestActive(player)) {
            return;
        }
        questManager.handleInventoryClose(player);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!questManager.isQuestActive(event.getPlayer())) {
            return;
        }
        questManager.handleQuestMovement(event.getPlayer(), event);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        questManager.forceEndQuest(event.getPlayer(), false, null);
    }
}