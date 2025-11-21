package com.seminario.plugin.listener;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import com.seminario.plugin.config.ConfigManager;
import com.seminario.plugin.manager.SQLDungeonManager;
import com.seminario.plugin.model.MenuType;
import com.seminario.plugin.model.MenuZone;
import com.seminario.plugin.sql.SQLQueryResult;
import com.seminario.plugin.util.SQLResultBook;
import com.seminario.plugin.util.ZoneDetector;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Handles chat events when players are in laboratory zones
 * Allows free SQL experimentation and learning
 */
public class LaboratoryListener implements Listener {
    
    private final ConfigManager configManager;
    private final SQLDungeonManager sqlDungeonManager;
    
    // Track players currently in laboratory zones
    private final Set<UUID> playersInLaboratory;
    
    public LaboratoryListener(ConfigManager configManager, SQLDungeonManager sqlDungeonManager) {
        this.configManager = configManager;
        this.sqlDungeonManager = sqlDungeonManager;
        this.playersInLaboratory = new HashSet<>();
    }
    
    /**
     * Handle chat events when players are in laboratory zones
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        
        // Check if player is in a laboratory zone
        if (!isPlayerInLaboratory(player)) {
            return;
        }
        
        // Cancel normal chat processing
        event.setCancelled(true);
        
        String message = event.getMessage().trim();
        
        // Handle special commands
        if (message.equalsIgnoreCase("exit") || message.equalsIgnoreCase("salir")) {
            exitLaboratory(player);
            return;
        }
        
        if (message.equalsIgnoreCase("help") || message.equalsIgnoreCase("ayuda")) {
            showLaboratoryHelp(player);
            return;
        }
        
        if (message.equalsIgnoreCase("tables") || message.equalsIgnoreCase("tablas")) {
            showAvailableTables(player);
            return;
        }
        
        // Process as SQL query
        processSQLQuery(player, message);
    }
    
    /**
     * Clean up when player quits
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playersInLaboratory.remove(event.getPlayer().getUniqueId());
    }
    
    /**
     * Check if player is currently in a laboratory zone
     */
    private boolean isPlayerInLaboratory(Player player) {
        // First check if we already know they're in laboratory
        if (playersInLaboratory.contains(player.getUniqueId())) {
            return true;
        }
        
        // Check current location against all zones
        for (MenuZone zone : configManager.getAllMenuZones().values()) {
            if (zone.getMenuType() == MenuType.LABORATORY && 
                ZoneDetector.isLocationInZone(player.getLocation(), zone)) {
                // Add to laboratory set
                playersInLaboratory.add(player.getUniqueId());
                
                // Show welcome message
                showLaboratoryWelcome(player);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Process SQL query in laboratory mode
     */
    private void processSQLQuery(Player player, String query) {
        try {
            // Validate and execute query
            SQLQueryResult result = sqlDungeonManager.getValidationEngine().executeQueryForLaboratory(query);
            
            if (result.hasError()) {
                // Show error message
                player.sendMessage(Component.text("❌ Error en la consulta SQL:", NamedTextColor.RED));
                player.sendMessage(Component.text(result.getError(), NamedTextColor.YELLOW));
                player.sendMessage(Component.text("", NamedTextColor.WHITE));
                player.sendMessage(Component.text("💡 Escribe 'help' para ver comandos disponibles", NamedTextColor.AQUA));
                return;
            }
            
            // Success - create result book
            player.sendMessage(Component.text("✅ Consulta ejecutada exitosamente!", NamedTextColor.GREEN));
            
            // Generate and give result book
            ItemStack book = SQLResultBook.createResultBook(player, query, result.getResultSet(), true);
            if (book != null) {
                player.getInventory().addItem(book);
                player.sendMessage(Component.text("📖 Se ha generado un libro con los resultados", NamedTextColor.AQUA));
            }
            
            player.sendMessage(Component.text("", NamedTextColor.WHITE));
            
        } catch (Exception e) {
            player.sendMessage(Component.text("❌ Error interno al procesar la consulta:", NamedTextColor.RED));
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.YELLOW));
        }
    }
    
    /**
     * Show welcome message when entering laboratory
     */
    private void showLaboratoryWelcome(Player player) {
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("🧪 ¡Bienvenido al Laboratorio SQL!", NamedTextColor.GOLD));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("Aquí puedes experimentar libremente con consultas SQL.", NamedTextColor.GREEN));
        player.sendMessage(Component.text("Todas las consultas que escribas se ejecutarán contra la base de datos.", NamedTextColor.AQUA));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("📋 Comandos disponibles:", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("• help - Mostrar esta ayuda", NamedTextColor.WHITE));
        player.sendMessage(Component.text("• tables - Ver tablas disponibles", NamedTextColor.WHITE));
        player.sendMessage(Component.text("• exit - Salir del laboratorio", NamedTextColor.WHITE));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("✏️ Escribe cualquier consulta SQL para ejecutarla", NamedTextColor.GREEN));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
    }
    
    /**
     * Show laboratory help
     */
    private void showLaboratoryHelp(Player player) {
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("🧪 Laboratorio SQL - Ayuda", NamedTextColor.GOLD));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("📋 Comandos disponibles:", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("• help/ayuda - Mostrar esta ayuda", NamedTextColor.WHITE));
        player.sendMessage(Component.text("• tables/tablas - Ver tablas disponibles", NamedTextColor.WHITE));
        player.sendMessage(Component.text("• exit/salir - Salir del laboratorio", NamedTextColor.WHITE));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("📝 Ejemplos de consultas:", NamedTextColor.AQUA));
        player.sendMessage(Component.text("• SELECT * FROM Jugadores", NamedTextColor.GRAY));
        player.sendMessage(Component.text("• SELECT nombre FROM Jugadores WHERE nivel > 20", NamedTextColor.GRAY));
        player.sendMessage(Component.text("• SELECT COUNT(*) FROM Construcciones", NamedTextColor.GRAY));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("💡 Los resultados se mostrarán en un libro", NamedTextColor.GREEN));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
    }
    
    /**
     * Show available database tables
     */
    private void showAvailableTables(Player player) {
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("📊 Tablas disponibles en la base de datos:", NamedTextColor.GOLD));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("• Jugadores - Información de jugadores (id, nombre, nivel, mundo, diamantes, oro, esmeraldas)", NamedTextColor.GREEN));
        player.sendMessage(Component.text("• Inventarios - Items de inventarios (id, jugador_id, item, cantidad, rareza, encantado)", NamedTextColor.GREEN));
        player.sendMessage(Component.text("• Construcciones - Construcciones de jugadores (id, jugador_id, nombre, tipo, bloques_usados)", NamedTextColor.GREEN));
        player.sendMessage(Component.text("• Logros - Logros obtenidos (id, jugador_id, nombre, categoria, fecha_obtenido)", NamedTextColor.GREEN));
        player.sendMessage(Component.text("• Comercio - Transacciones comerciales (id, vendedor_id, comprador_id, item, cantidad, precio)", NamedTextColor.GREEN));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("💡 Usa 'SELECT * FROM [tabla]' para ver todo el contenido de una tabla", NamedTextColor.AQUA));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
    }
    
    /**
     * Handle exit from laboratory
     */
    private void exitLaboratory(Player player) {
        playersInLaboratory.remove(player.getUniqueId());
        player.sendMessage(Component.text("👋 Has salido del Laboratorio SQL", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("El chat ahora funciona normalmente", NamedTextColor.GREEN));
    }
    
    /**
     * Add player to laboratory (called from movement detection)
     */
    public void addPlayerToLaboratory(Player player) {
        if (!playersInLaboratory.contains(player.getUniqueId())) {
            playersInLaboratory.add(player.getUniqueId());
            showLaboratoryWelcome(player);
        }
    }
    
    /**
     * Remove player from laboratory (called from movement detection)
     */
    public void removePlayerFromLaboratory(Player player) {
        if (playersInLaboratory.remove(player.getUniqueId())) {
            player.sendMessage(Component.text("👋 Has salido del Laboratorio SQL", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("El chat ahora funciona normalmente", NamedTextColor.GREEN));
        }
    }
    
    /**
     * Check if player is currently tracked as being in laboratory
     */
    public boolean isInLaboratory(Player player) {
        return playersInLaboratory.contains(player.getUniqueId());
    }
}