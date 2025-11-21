package com.seminario.plugin.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.seminario.plugin.manager.SurveyManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Listener for Post-Test item interactions
 */
public class PostTestListener implements Listener {
    
    private final SurveyManager surveyManager;
    
    public PostTestListener(SurveyManager surveyManager) {
        this.surveyManager = surveyManager;
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) {
            return;
        }
        
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        
        Component displayName = item.getItemMeta().displayName();
        if (displayName == null) {
            return;
        }
        
        String itemName = PlainTextComponentSerializer.plainText().serialize(displayName);
        
        // Debug: Log all item interactions
        org.bukkit.Bukkit.getLogger().info(String.format("[DEBUG] Player %s right-clicked item: %s", player.getName(), itemName));
        
        // Check if this is a Post-Test item
        if (itemName.equals("Post-Test")) {
            org.bukkit.Bukkit.getLogger().info(String.format("[DEBUG] Post-Test item detected! Player: %s", player.getName()));
            event.setCancelled(true);
            
            // Check if default survey is configured
            if (!surveyManager.hasDefaultSurvey()) {
                player.sendMessage(Component.text("El sistema de Post-Test no está configurado.", NamedTextColor.RED));
                player.sendMessage(Component.text("Contacta con un administrador.", NamedTextColor.GRAY));
                return;
            }
            
            // Check if player already has an active survey
            if (surveyManager.hasActiveSession(player)) {
                player.sendMessage(Component.text("Ya tienes una encuesta activa.", NamedTextColor.YELLOW));
                return;
            }
            
            // Start default survey
            player.sendMessage(Component.text("¡Iniciando Post-Test!", NamedTextColor.GREEN));
            player.sendMessage(Component.text("Serás transportado a la zona de encuesta...", NamedTextColor.YELLOW));
            
            // Remove the post-test item BEFORE starting survey
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                player.getInventory().setItem(player.getInventory().getHeldItemSlot(), null);
            }
            
            // Small delay before starting survey to allow message to be seen
            org.bukkit.plugin.Plugin plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("SeminarioPlugin");
            if (plugin == null) {
                player.sendMessage(Component.text("Error interno del plugin.", NamedTextColor.RED));
                return;
            }
            
            org.bukkit.Bukkit.getScheduler().runTaskLater(
                plugin, 
                () -> {
                    if (player.isOnline() && !surveyManager.hasActiveSession(player)) {
                        org.bukkit.Bukkit.getLogger().info(String.format("[DEBUG] Starting default survey for %s", player.getName()));
                        boolean success = surveyManager.startDefaultSurvey(player);
                        if (!success) {
                            player.sendMessage(Component.text("Error al iniciar el Post-Test. Inténtalo de nuevo.", NamedTextColor.RED));
                        }
                    }
                }, 
                20L // 1 second delay
            );
        }
    }
}