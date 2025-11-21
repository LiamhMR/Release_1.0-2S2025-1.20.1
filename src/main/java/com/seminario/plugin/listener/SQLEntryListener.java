package com.seminario.plugin.listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import com.seminario.plugin.manager.SQLDungeonManager;
import com.seminario.plugin.model.SQLDungeonWorld;
import com.seminario.plugin.model.SQLLevel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Handles interactions with SQL entry blocks and query submissions
 */
public class SQLEntryListener implements Listener {
    
    private final SQLDungeonManager sqlDungeonManager;
    
    // Track players waiting to submit SQL queries
    private final Map<UUID, Integer> playersAwaitingQuery = new HashMap<>();
    
    public SQLEntryListener(SQLDungeonManager sqlDungeonManager) {
        this.sqlDungeonManager = sqlDungeonManager;
    }
    
    /**
     * Handle right-click on blocks in SQL dungeon worlds
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        
        if (block == null) {
            return;
        }
        
        String worldName = player.getWorld().getName();
        
        // Check if this is a SQL dungeon world
        if (!sqlDungeonManager.isSQLDungeon(worldName)) {
            return;
        }
        
        // Check if this block is an SQL entry point
        SQLLevel level = findLevelAtLocation(worldName, block.getLocation());
        if (level == null || !level.hasEntry()) {
            return;
        }
        
        // Check if this is the correct entry block (command block or similar)
        if (!isValidEntryBlock(block)) {
            return;
        }
        
        event.setCancelled(true);
        
        // Start SQL challenge for this level
        handleSQLEntryInteraction(player, level);
    }
    
    /**
     * Handle chat messages from players waiting to submit SQL queries
     */
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        // Check if player is waiting to submit a query
        if (!playersAwaitingQuery.containsKey(playerId)) {
            return;
        }
        
        // Cancel the chat event to prevent broadcasting
        event.setCancelled(true);
        
        String query = event.getMessage().trim();
        
        // Handle the SQL submission
        org.bukkit.Bukkit.getScheduler().runTask(
            org.bukkit.plugin.java.JavaPlugin.getPlugin(com.seminario.plugin.App.class),
            () -> handleSQLSubmission(player, query)
        );
    }
    
    /**
     * Handle SQL entry block interaction
     * @param player The player
     * @param level The SQL level
     */
    private void handleSQLEntryInteraction(Player player, SQLLevel level) {
        // Check if level is complete (has challenge and expected query)
        if (!level.isComplete()) {
            player.sendMessage(Component.text("Este nivel no está configurado completamente.", NamedTextColor.RED));
            if (player.hasPermission("seminario.sql.admin")) {
                player.sendMessage(Component.text("Configura el desafío y consulta esperada.", NamedTextColor.GRAY));
            }
            return;
        }
        
        // Start the challenge
        sqlDungeonManager.startChallenge(player, level.getLevelNumber());
        
        // Set player as awaiting query
        playersAwaitingQuery.put(player.getUniqueId(), level.getLevelNumber());
        
        // Instructions for submitting query
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("💡 Para enviar tu consulta SQL:", NamedTextColor.GOLD));
        player.sendMessage(Component.text("   Escribe tu consulta en el chat", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("   Ejemplo: SELECT nombre FROM Jugadores WHERE diamantes > 100", NamedTextColor.GRAY));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("⚠️  Para cancelar, escribe 'cancelar'", NamedTextColor.RED));
    }
    
    /**
     * Handle SQL query submission
     * @param player The player
     * @param query The SQL query
     */
    private void handleSQLSubmission(Player player, String query) {
        UUID playerId = player.getUniqueId();
        
        if (!playersAwaitingQuery.containsKey(playerId)) {
            return;
        }
        
        // Check for cancellation
        if (query.equalsIgnoreCase("cancelar") || query.equalsIgnoreCase("cancel")) {
            playersAwaitingQuery.remove(playerId);
            player.sendMessage(Component.text("Desafío SQL cancelado.", NamedTextColor.YELLOW));
            return;
        }
        
        // Remove from waiting list
        playersAwaitingQuery.remove(playerId);
        
        // Submit to SQL dungeon manager
        sqlDungeonManager.handleSQLSubmission(player, query);
    }
    
    /**
     * Find SQL level at a specific location
     * @param worldName The world name
     * @param location The location to check
     * @return SQLLevel if found, null otherwise
     */
    private SQLLevel findLevelAtLocation(String worldName, org.bukkit.Location location) {
        SQLDungeonWorld sqlWorld = sqlDungeonManager.getSQLDungeon(worldName);
        if (sqlWorld == null) {
            return null;
        }
        
        // Check all levels for entry points near this location
        for (SQLLevel level : sqlWorld.getLevels().values()) {
            if (level.hasEntry() && isNearLocation(level.getEntryLocation(), location, 2.0)) {
                return level;
            }
        }
        
        return null;
    }
    
    /**
     * Check if a block is a valid SQL entry block
     * @param block The block to check
     * @return true if valid
     */
    private boolean isValidEntryBlock(Block block) {
        Material type = block.getType();
        
        // Allow command blocks, lecterns, enchanting tables, etc.
        return type == Material.COMMAND_BLOCK ||
               type == Material.CHAIN_COMMAND_BLOCK ||
               type == Material.REPEATING_COMMAND_BLOCK ||
               type == Material.LECTERN ||
               type == Material.ENCHANTING_TABLE ||
               type == Material.BEACON ||
               type == Material.END_PORTAL_FRAME;
    }
    
    /**
     * Check if two locations are near each other
     * @param loc1 First location
     * @param loc2 Second location
     * @param maxDistance Maximum distance
     * @return true if within distance
     */
    private boolean isNearLocation(org.bukkit.Location loc1, org.bukkit.Location loc2, double maxDistance) {
        if (!loc1.getWorld().equals(loc2.getWorld())) {
            return false;
        }
        
        return loc1.distance(loc2) <= maxDistance;
    }
    
    /**
     * Clean up player session on disconnect
     * @param player The player
     */
    public void cleanupPlayerSession(Player player) {
        playersAwaitingQuery.remove(player.getUniqueId());
    }
    
    /**
     * Check if player is awaiting query submission
     * @param player The player
     * @return true if awaiting
     */
    public boolean isPlayerAwaitingQuery(Player player) {
        return playersAwaitingQuery.containsKey(player.getUniqueId());
    }
    
    /**
     * Get all players currently awaiting queries
     * @return Map of player UUIDs to level numbers
     */
    public Map<UUID, Integer> getPlayersAwaitingQuery() {
        return new HashMap<>(playersAwaitingQuery);
    }
}