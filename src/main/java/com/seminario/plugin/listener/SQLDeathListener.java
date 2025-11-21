package com.seminario.plugin.listener;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import com.seminario.plugin.manager.SQLDungeonManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Handles player death and respawn events in SQL Dungeons
 */
public class SQLDeathListener implements Listener {
    
    private final SQLDungeonManager sqlDungeonManager;
    private final JavaPlugin plugin;
    
    public SQLDeathListener(SQLDungeonManager sqlDungeonManager, JavaPlugin plugin) {
        this.sqlDungeonManager = sqlDungeonManager;
        this.plugin = plugin;
    }
    
    /**
     * Handle player death in SQL Dungeons
     * @param event The death event
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        String worldName = player.getWorld().getName();
        
        // Check if player is in a SQL Dungeon
        if (sqlDungeonManager.isSQLDungeon(worldName)) {
            // Customize death message for SQL Dungeon
            event.setDeathMessage(player.getName() + " murió en el SQL Dungeon");
            
            // Keep inventory in SQL Dungeons
            event.setKeepInventory(true);
            event.getDrops().clear();
            
            // Keep experience
            event.setKeepLevel(true);
            event.setDroppedExp(0);
            
            // Remove one yellow heart from the counter (not from Minecraft health)
            // Only process if player is in an active session
            if (sqlDungeonManager.isPlayerInSession(player)) {
                boolean yellowHeartRemoved = sqlDungeonManager.removeYellowHeartOnDeath(player);
                
                if (yellowHeartRemoved) {
                    player.sendMessage(Component.text("💀 Has muerto - Perdiste una vida amarilla", NamedTextColor.RED));
                } else {
                    player.sendMessage(Component.text("💀 Has muerto - No tienes vidas amarillas", NamedTextColor.DARK_RED));
                }
                
                // Schedule heart GUI update after 1 second (20 ticks) to render the visual state
                org.bukkit.Bukkit.getScheduler().runTaskLater(
                    plugin,
                    () -> {
                        // Update the visual heart representation in player's health bar
                        sqlDungeonManager.updatePlayerHealth(player);
                    },
                    20L // 1 second delay
                );
            }
            
            player.sendMessage(Component.text("📦 Tu inventario y experiencia han sido conservados", NamedTextColor.GREEN));
            
            // IMPORTANT: Teleport to checkpoint IMMEDIATELY to prevent death loop
            // This happens right after death, regardless of heart removal
            org.bukkit.Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> {
                    Location checkpoint = sqlDungeonManager.getCurrentLevelCheckpoint(player);
                    if (checkpoint != null) {
                        player.teleport(checkpoint);
                        player.setHealth(player.getMaxHealth());
                        player.setFireTicks(0);
                        player.sendMessage(Component.text("📍 Teletransportado al checkpoint", NamedTextColor.AQUA));
                    }
                },
                5L // 0.25 seconds - teleport almost immediately to avoid death loop
            );
        }
    }
    
    /**
     * Handle player respawn in SQL Dungeons
     * @param event The respawn event
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        String worldName = player.getWorld().getName();
        
        // Check if player is in a SQL Dungeon
        if (sqlDungeonManager.isSQLDungeon(worldName)) {
            Location checkpoint = sqlDungeonManager.getCurrentLevelCheckpoint(player);
            
            if (checkpoint != null) {
                // Set respawn location to current level checkpoint
                event.setRespawnLocation(checkpoint);
                
                // Send message to inform player
                int currentProgress = sqlDungeonManager.getPlayerProgress(player, worldName);
                int currentLevel = currentProgress + 1;
                
                // Schedule message for after respawn (to ensure player receives it)
                org.bukkit.Bukkit.getScheduler().runTaskLater(
                    plugin, 
                    () -> {
                        player.sendMessage(Component.text("📍 Respawneado en el checkpoint del nivel " + currentLevel, NamedTextColor.GREEN));
                        player.sendMessage(Component.text("¡Continúa con el desafío SQL!", NamedTextColor.YELLOW));
                        
                        // If player had an active challenge, remind them
                        if (sqlDungeonManager.hasActiveSession(player)) {
                            player.sendMessage(Component.text("💡 Tienes un desafío SQL activo", NamedTextColor.AQUA));
                            player.sendMessage(Component.text("✏️ Escribe tu consulta SQL en el chat para continuar", NamedTextColor.GREEN));
                        }
                    }, 
                    10L // 0.5 second delay
                );
            } else {
                // Fallback: respawn at world spawn
                event.setRespawnLocation(player.getWorld().getSpawnLocation());
                
                org.bukkit.Bukkit.getScheduler().runTaskLater(
                    plugin, 
                    () -> {
                        player.sendMessage(Component.text("⚠️ No se encontró checkpoint, respawneado en el spawn del mundo", NamedTextColor.YELLOW));
                    }, 
                    10L
                );
            }
        }
    }
}