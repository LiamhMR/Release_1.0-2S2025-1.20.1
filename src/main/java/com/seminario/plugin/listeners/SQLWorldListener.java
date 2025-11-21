package com.seminario.plugin.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.scheduler.BukkitRunnable;

import com.seminario.plugin.manager.SQLDungeonManager;
import com.seminario.plugin.model.SQLDungeonWorld;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Handles player world changes and SQL Dungeon auto-start
 */
public class SQLWorldListener implements Listener {
    
    private final SQLDungeonManager sqlDungeonManager;
    
    public SQLWorldListener(SQLDungeonManager sqlDungeonManager) {
        this.sqlDungeonManager = sqlDungeonManager;
    }
    
    /**
     * Handle player changing worlds - auto-start SQL Dungeon if entering SQL world
     */
    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        String newWorldName = player.getWorld().getName();
        String previousWorldName = event.getFrom().getName();
        
        // If player left an SQL world, clean up their session
        if (sqlDungeonManager.isSQLDungeon(previousWorldName)) {
            sqlDungeonManager.clearPlayerSession(player);
        }
        
        // Check if the new world is a SQL Dungeon
        if (sqlDungeonManager.isSQLDungeon(newWorldName)) {
            // Delay slightly to ensure player is fully loaded in the new world
            new BukkitRunnable() {
                @Override
                public void run() {
                    handleSQLWorldEntry(player, newWorldName);
                    // Ensure no rain in SQL Dungeons
                    ensureNoRain(player.getWorld());
                }
            }.runTaskLater(sqlDungeonManager.getPlugin(), 10L); // 0.5 second delay
        }
        
        // Check if entering spawn world (first world loaded or common spawn world names)
        if (isSpawnWorld(newWorldName)) {
            // Ensure no rain in spawn world
            ensureNoRain(player.getWorld());
        }
    }
    
    /**
     * Handle a player entering a SQL Dungeon world
     * @param player The player entering the world
     * @param worldName The SQL Dungeon world name
     */
    private void handleSQLWorldEntry(Player player, String worldName) {
        SQLDungeonWorld sqlWorld = sqlDungeonManager.getSQLDungeon(worldName);
        
        if (sqlWorld == null || !sqlWorld.isPlayable()) {
            player.sendMessage(Component.text("Este SQL Dungeon no está configurado correctamente.", NamedTextColor.RED));
            return;
        }
        
        // Welcome message
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("═══════════════════════════════", NamedTextColor.GOLD));
        player.sendMessage(Component.text("    🏰 BIENVENIDO AL SQL DUNGEON", NamedTextColor.GOLD));
        player.sendMessage(Component.text("═══════════════════════════════", NamedTextColor.GOLD));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("🎯 Objetivo: ", NamedTextColor.YELLOW)
            .append(Component.text("Resuelve consultas SQL para avanzar", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("📊 Mundo: ", NamedTextColor.AQUA)
            .append(Component.text(worldName, NamedTextColor.WHITE)));
        player.sendMessage(Component.text("🔢 Niveles disponibles: ", NamedTextColor.GREEN)
            .append(Component.text(String.valueOf(sqlWorld.getLevelCount()), NamedTextColor.WHITE)));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        
        // Start the player at level 1
        sqlDungeonManager.startPlayerSession(player, worldName);
        
        // Additional instructions
        player.sendMessage(Component.text("💡 Instrucciones:", NamedTextColor.LIGHT_PURPLE));
        player.sendMessage(Component.text("  • Busca bloques de entrada para iniciar niveles", NamedTextColor.GRAY));
        player.sendMessage(Component.text("  • Escribe tus consultas SQL en el chat", NamedTextColor.GRAY));
        player.sendMessage(Component.text("  • Usa '/sm sql info' para ver tu progreso", NamedTextColor.GRAY));
        player.sendMessage(Component.text("  • ¡Cuidado! Los errores te harán volar por los aires", NamedTextColor.RED));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("¡Buena suerte! 🍀", NamedTextColor.GREEN));
    }
    
    /**
     * Handle player leaving the server - clean up their session
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Always clean up session when player quits to prevent memory leaks
        sqlDungeonManager.clearPlayerSession(player);
    }
    
    /**
     * Prevent rain from starting in SQL Dungeons and spawn worlds
     */
    @EventHandler
    public void onWeatherChange(WeatherChangeEvent event) {
        String worldName = event.getWorld().getName();
        
        // Cancel rain in SQL Dungeons
        if (sqlDungeonManager.isSQLDungeon(worldName) && event.toWeatherState()) {
            event.setCancelled(true);
            return;
        }
        
        // Cancel rain in spawn worlds
        if (isSpawnWorld(worldName) && event.toWeatherState()) {
            event.setCancelled(true);
        }
    }
    
    /**
     * Check if a world is considered a spawn world
     * @param worldName The world name to check
     * @return true if it's a spawn world
     */
    private boolean isSpawnWorld(String worldName) {
        // Check common spawn world names
        String[] spawnWorldNames = {"world", "spawn", "lobby", "hub"};
        for (String spawnName : spawnWorldNames) {
            if (worldName.equalsIgnoreCase(spawnName)) {
                return true;
            }
        }
        
        // Check if it's the first world loaded (usually main spawn world)
        try {
            return sqlDungeonManager.getPlugin().getServer().getWorlds().get(0).getName().equals(worldName);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Ensure no rain is active in the given world
     * @param world The world to clear rain from
     */
    private void ensureNoRain(org.bukkit.World world) {
        if (world.hasStorm() || world.isThundering()) {
            world.setStorm(false);
            world.setThundering(false);
            world.setWeatherDuration(0);
            world.setClearWeatherDuration(Integer.MAX_VALUE);
        }
    }
}