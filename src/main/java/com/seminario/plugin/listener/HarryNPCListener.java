package com.seminario.plugin.listener;

import com.seminario.plugin.manager.HarryNPCManager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

/**
 * Listener for Harry NPC interactions with Villagers
 */
public class HarryNPCListener implements Listener {
    
    private final HarryNPCManager harryManager;
    
    public HarryNPCListener(HarryNPCManager harryManager) {
        this.harryManager = harryManager;
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager)) {
            return;
        }
        
        Villager villager = (Villager) event.getRightClicked();
        Player player = event.getPlayer();
        
        // Check if this is a Harry NPC by checking custom name
        if (villager.customName() != null && 
            villager.customName().toString().contains("Profesor Harry")) {
            
            event.setCancelled(true);
            harryManager.handleNPCInteraction(villager, player);
        }
    }
}