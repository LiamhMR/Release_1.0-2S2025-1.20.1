package com.seminario.plugin.listener;

import org.bukkit.entity.Firework;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import com.seminario.plugin.manager.FireworkManager;
import com.seminario.plugin.model.FireworkTrigger;

/**
 * Listener for firework trigger activation and damage prevention
 */
public class FireworkTriggerListener implements Listener {
    
    private final FireworkManager fireworkManager;
    
    public FireworkTriggerListener(FireworkManager fireworkManager) {
        this.fireworkManager = fireworkManager;
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only trigger if player moved to a different block
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        
        // Check if player stepped on a firework trigger
        FireworkTrigger trigger = fireworkManager.getFireworkAt(event.getTo());
        if (trigger != null && trigger.isEnabled()) {
            fireworkManager.triggerFirework(event.getTo(), event.getPlayer());
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onFireworkDamage(EntityDamageByEntityEvent event) {
        // Cancel damage from fireworks to prevent player damage
        if (event.getDamager() instanceof Firework) {
            event.setCancelled(true);
        }
    }
}