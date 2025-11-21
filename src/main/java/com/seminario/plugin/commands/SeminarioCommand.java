package com.seminario.plugin.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import com.seminario.plugin.config.ConfigManager;
import com.seminario.plugin.manager.HarryNPCManager;
import com.seminario.plugin.manager.LobbyManager;
import com.seminario.plugin.manager.SQLDungeonManager;
import com.seminario.plugin.manager.SlideManager;
import com.seminario.plugin.manager.SpawnpointManager;
import com.seminario.plugin.manager.SurveyManager;
import com.seminario.plugin.model.FireworkTrigger;
import com.seminario.plugin.model.MenuType;
import com.seminario.plugin.model.MenuZone;
import com.seminario.plugin.model.Slide;
import com.seminario.plugin.model.Survey;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.SessionManager;

/**
 * Handles /sm commands for managing menu zones
 */
public class SeminarioCommand implements CommandExecutor, TabCompleter {
    
    private final ConfigManager configManager;
    private final SlideManager slideManager;
    private final SQLDungeonManager sqlDungeonManager;
    private final SpawnpointManager spawnpointManager;
    private final LobbyManager lobbyManager;
    private final SurveyManager surveyManager;
    private final com.seminario.plugin.manager.FireworkManager fireworkManager;
    private final HarryNPCManager harryNPCManager;
    private com.seminario.plugin.manager.FixSlideManager fixSlideManager;
    
    public SeminarioCommand(ConfigManager configManager, SlideManager slideManager, SQLDungeonManager sqlDungeonManager, SpawnpointManager spawnpointManager, LobbyManager lobbyManager, SurveyManager surveyManager, com.seminario.plugin.manager.FireworkManager fireworkManager, HarryNPCManager harryNPCManager) {
        this.configManager = configManager;
        this.slideManager = slideManager;
        this.sqlDungeonManager = sqlDungeonManager;
        this.spawnpointManager = spawnpointManager;
        this.lobbyManager = lobbyManager;
        this.surveyManager = surveyManager;
        this.fireworkManager = fireworkManager;
        this.harryNPCManager = harryNPCManager;
    }
    
    /**
     * Set the FixSlideManager (called after plugin initialization)
     */
    public void setFixSlideManager(com.seminario.plugin.manager.FixSlideManager fixSlideManager) {
        this.fixSlideManager = fixSlideManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("seminario.admin")) {
            sender.sendMessage(ChatColor.RED + "No tienes permisos para usar este comando.");
            return true;
        }
        
        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "create":
                return handleCreateCommand(sender, args);
            case "remove":
                return handleRemoveCommand(sender, args);
            case "list":
                return handleListCommand(sender);
            case "info":
                return handleInfoCommand(sender, args);
            case "set":
                return handleSetCommand(sender, args);
            case "slide":
                return handleSlideCommand(sender, args);
            case "fixslide":
                return handleFixSlideCommand(sender, args);
            case "disabled":
            case "enabled":
                return handleDisabledCommand(sender, args);
            case "chestport":
                return handleChestportCommand(sender, args);
            case "sql":
                return handleSQLCommand(sender, args);
            case "db":
                return handleDBCommand(sender, args);
            case "spawnpoint":
                return handleSpawnpointCommand(sender, args);
            case "lobby":
                return handleLobbyCommand(sender, args);
            case "survey":
                return handleSurveyCommand(sender, args);
            case "defaultsurvey":
                return handleDefaultSurveyCommand(sender, args);
            case "createfire":
                return handleCreateFireCommand(sender, args);
            case "createcreeperfire":
                return handleCreateCreeperFireCommand(sender, args);
            case "firework":
                return handleFireworkCommand(sender, args);
            case "newharry":
                return handleNewHarryCommand(sender, args);
            case "harry":
                return handleHarryCommand(sender, args);
            case "reload":
                return handleReloadCommand(sender);
            case "test":
                return handleTestCommand(sender, args);
            case "debug":
                return handleDebugCommand(sender, args);
            default:
                sendHelpMessage(sender);
                return true;
        }
    }
    
    private boolean handleCreateCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Este comando solo puede ser usado por jugadores.");
            return true;
        }
        
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Uso: /sm create <menuzone|fixslide|SQLDUNGEON> <nombre> [args]");
            sender.sendMessage(ChatColor.YELLOW + "Ejemplos:");
            sender.sendMessage(ChatColor.GRAY + "  /sm create menuzone mi_zona");
            sender.sendMessage(ChatColor.GRAY + "  /sm create fixslide mi_fixslide mi_zona_slide");
            sender.sendMessage(ChatColor.GRAY + "  /sm create SQLDUNGEON nombre_mundo");
            return true;
        }
        
        String createType = args[1].toLowerCase();
        if (createType.equals("sqldungeon")) {
            return handleCreateSQLDungeon(sender, args);
        } else if (createType.equals("fixslide")) {
            return handleCreateFixSlide(sender, args);
        } else if (!createType.equals("menuzone")) {
            sender.sendMessage(ChatColor.RED + "Tipo desconocido: " + createType);
            sender.sendMessage(ChatColor.GRAY + "Tipos válidos: menuzone, fixslide, SQLDUNGEON");
            return true;
        }
        
        Player player = (Player) sender;
        String zoneName = args[2];
        
        // Check if zone name already exists
        if (configManager.hasMenuZone(zoneName)) {
            sender.sendMessage(ChatColor.RED + "Ya existe una zona con el nombre '" + zoneName + "'.");
            return true;
        }
        
        try {
            // Get WorldEdit selection
            SessionManager sessionManager = WorldEdit.getInstance().getSessionManager();
            LocalSession session = sessionManager.get(BukkitAdapter.adapt(player));
            Region selection = session.getSelection(BukkitAdapter.adapt(player.getWorld()));
            
            if (selection == null) {
                sender.sendMessage(ChatColor.RED + "No tienes una selección de WorldEdit activa. Usa //wand para seleccionar una zona.");
                return true;
            }
            
            // Convert WorldEdit selection to Bukkit locations
            org.bukkit.World bukkitWorld = player.getWorld();
            com.sk89q.worldedit.math.BlockVector3 min = selection.getMinimumPoint();
            com.sk89q.worldedit.math.BlockVector3 max = selection.getMaximumPoint();
            
            Location pos1 = new Location(bukkitWorld, min.getX(), min.getY(), min.getZ());
            Location pos2 = new Location(bukkitWorld, max.getX(), max.getY(), max.getZ());
            
            // Create menu zone
            MenuZone menuZone = new MenuZone(zoneName, pos1, pos2);
            
            // Save to config
            if (configManager.addMenuZone(menuZone)) {
                sender.sendMessage(ChatColor.GREEN + "¡Zona de menú '" + zoneName + "' creada exitosamente!");
                sender.sendMessage(ChatColor.GRAY + "Mundo: " + bukkitWorld.getName());
                sender.sendMessage(ChatColor.GRAY + "Desde: " + formatLocation(pos1));
                sender.sendMessage(ChatColor.GRAY + "Hasta: " + formatLocation(pos2));
            } else {
                sender.sendMessage(ChatColor.RED + "Error al guardar la zona de menú.");
            }
            
        } catch (IncompleteRegionException e) {
            sender.sendMessage(ChatColor.RED + "Selección incompleta. Selecciona ambas posiciones con //wand.");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error al obtener la selección de WorldEdit: " + e.getMessage());
        }
        
        return true;
    }
    
    private boolean handleRemoveCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /sm remove <nombre>");
            return true;
        }
        
        String zoneName = args[1];
        
        if (configManager.removeMenuZone(zoneName)) {
            sender.sendMessage(ChatColor.GREEN + "Zona de menú '" + zoneName + "' eliminada exitosamente.");
        } else {
            sender.sendMessage(ChatColor.RED + "No se encontró una zona con el nombre '" + zoneName + "'.");
        }
        
        return true;
    }
    
    private boolean handleListCommand(CommandSender sender) {
        var zones = configManager.getAllMenuZones();
        
        if (zones.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No hay zonas de menú configuradas.");
            return true;
        }
        
        sender.sendMessage(ChatColor.GREEN + "=== Zonas de Menú ===");
        for (MenuZone zone : zones.values()) {
            sender.sendMessage(ChatColor.WHITE + "• " + zone.getName() + 
                ChatColor.GRAY + " (" + zone.getWorld().getName() + ")");
        }
        sender.sendMessage(ChatColor.GRAY + "Total: " + zones.size() + " zonas");
        
        return true;
    }
    
    private boolean handleInfoCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /sm info <nombre>");
            return true;
        }
        
        String zoneName = args[1];
        MenuZone zone = configManager.getMenuZone(zoneName);
        
        if (zone == null) {
            sender.sendMessage(ChatColor.RED + "No se encontró una zona con el nombre '" + zoneName + "'.");
            return true;
        }
        
        sender.sendMessage(ChatColor.GREEN + "=== Información de Zona: " + zoneName + " ===");
        sender.sendMessage(ChatColor.WHITE + "Mundo: " + ChatColor.GRAY + zone.getWorld().getName());
        sender.sendMessage(ChatColor.WHITE + "Tipo de Menú: " + ChatColor.GRAY + 
            (zone.hasMenuType() ? zone.getMenuType().getName() : "No establecido"));
        sender.sendMessage(ChatColor.WHITE + "Posición 1: " + ChatColor.GRAY + formatLocation(zone.getPos1()));
        sender.sendMessage(ChatColor.WHITE + "Posición 2: " + ChatColor.GRAY + formatLocation(zone.getPos2()));
        
        return true;
    }
    
    private boolean handleSetCommand(CommandSender sender, String[] args) {
        if (args.length < 4 || !args[1].equalsIgnoreCase("menutype")) {
            sender.sendMessage(ChatColor.RED + "Uso: /sm set menutype <zona> <tipo>");
            sender.sendMessage(ChatColor.GRAY + "Tipos disponibles: " + String.join(", ", MenuType.getAvailableTypes()));
            return true;
        }
        
        String zoneName = args[2];
        String typeStr = args[3];
        
        // Check if zone exists
        if (!configManager.hasMenuZone(zoneName)) {
            sender.sendMessage(ChatColor.RED + "No se encontró una zona con el nombre '" + zoneName + "'.");
            return true;
        }
        
        // Validate menu type
        MenuType menuType = MenuType.fromString(typeStr);
        if (menuType == null) {
            sender.sendMessage(ChatColor.RED + "Tipo de menú inválido: '" + typeStr + "'.");
            sender.sendMessage(ChatColor.GRAY + "Tipos disponibles: " + String.join(", ", MenuType.getAvailableTypes()));
            return true;
        }
        
        // Update menu type
        if (configManager.updateMenuType(zoneName, menuType)) {
            sender.sendMessage(ChatColor.GREEN + "Tipo de menú actualizado para la zona '" + zoneName + "' a: " + menuType.getName());
        } else {
            sender.sendMessage(ChatColor.RED + "Error al actualizar el tipo de menú.");
        }
        
        return true;
    }
    
    private boolean handleSlideCommand(CommandSender sender, String[] args) {
        // /sm slide <zoneName> <add/edit/delete/fix> [slideNumber] [url]
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Uso: /sm slide <zona> <add|edit|delete|fix> [args...]");
            return true;
        }
        
        String zoneName = args[1];
        String slideAction = args[2].toLowerCase();
        
        // Verify zone exists and is slide type
        MenuZone zone = configManager.getMenuZone(zoneName);
        if (zone == null) {
            sender.sendMessage(ChatColor.RED + "No se encontró una zona con el nombre '" + zoneName + "'.");
            return true;
        }
        
        if (!zone.hasMenuType() || zone.getMenuType() != MenuType.SLIDE) {
            sender.sendMessage(ChatColor.RED + "La zona '" + zoneName + "' debe ser de tipo 'slide' para usar este comando.");
            sender.sendMessage(ChatColor.GRAY + "Usa: /sm set menutype " + zoneName + " slide");
            return true;
        }
        
        switch (slideAction) {
            case "add":
                return handleSlideAdd(sender, zoneName, args);
            case "edit":
                return handleSlideEdit(sender, zoneName, args);
            case "delete":
                return handleSlideDelete(sender, zoneName, args);
            case "fix":
                return handleSlideFixDirection(sender, zoneName, args);
            case "posfix":
                return handleSlidePositionFix(sender, zoneName, args);
            default:
                sender.sendMessage(ChatColor.RED + "Acción inválida. Usa: add, edit, delete, fix o posfix");
                return true;
        }
    }
    
    private boolean handleSlideAdd(CommandSender sender, String zoneName, String[] args) {
        // /sm slide <zoneName> add <url>
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Uso: /sm slide " + zoneName + " add <url>");
            return true;
        }
        
        String url = args[3];
        
        // Validate URL format
        if (!isValidImageUrl(url)) {
            sender.sendMessage(ChatColor.RED + "URL inválida. Debe ser una URL de imagen válida (http/https).");
            return true;
        }
        
        Slide newSlide = slideManager.addSlide(zoneName, url);
        if (newSlide != null) {
            sender.sendMessage(ChatColor.GREEN + "¡Slide #" + newSlide.getSlideNumber() + " agregado exitosamente!");
            sender.sendMessage(ChatColor.GRAY + "URL: " + url);
            sender.sendMessage(ChatColor.YELLOW + "La imagen se procesará automáticamente.");
        } else {
            sender.sendMessage(ChatColor.RED + "Error al agregar el slide.");
        }
        
        return true;
    }
    
    private boolean handleSlideEdit(CommandSender sender, String zoneName, String[] args) {
        // /sm slide <zoneName> edit <slideNumber> <url>
        if (args.length < 5) {
            sender.sendMessage(ChatColor.RED + "Uso: /sm slide " + zoneName + " edit <número> <url>");
            return true;
        }
        
        int slideNumber;
        try {
            slideNumber = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Número de slide inválido: " + args[3]);
            return true;
        }
        
        String newUrl = args[4];
        
        // Validate URL format
        if (!isValidImageUrl(newUrl)) {
            sender.sendMessage(ChatColor.RED + "URL inválida. Debe ser una URL de imagen válida (http/https).");
            return true;
        }
        
        if (slideManager.editSlide(zoneName, slideNumber, newUrl)) {
            sender.sendMessage(ChatColor.GREEN + "¡Slide #" + slideNumber + " editado exitosamente!");
            sender.sendMessage(ChatColor.GRAY + "Nueva URL: " + newUrl);
            sender.sendMessage(ChatColor.YELLOW + "La imagen se reprocesará automáticamente.");
        } else {
            sender.sendMessage(ChatColor.RED + "Error: No se encontró el slide #" + slideNumber + " en la zona '" + zoneName + "'.");
        }
        
        return true;
    }
    
    private boolean handleSlideDelete(CommandSender sender, String zoneName, String[] args) {
        // /sm slide <zoneName> delete <slideNumber>
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Uso: /sm slide " + zoneName + " delete <número>");
            return true;
        }
        
        int slideNumber;
        try {
            slideNumber = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Número de slide inválido: " + args[3]);
            return true;
        }
        
        if (slideManager.deleteSlide(zoneName, slideNumber)) {
            sender.sendMessage(ChatColor.GREEN + "¡Slide #" + slideNumber + " eliminado exitosamente!");
            sender.sendMessage(ChatColor.GRAY + "Los slides siguientes han sido renumerados.");
        } else {
            sender.sendMessage(ChatColor.RED + "Error: No se encontró el slide #" + slideNumber + " en la zona '" + zoneName + "'.");
        }
        
        return true;
    }
    
    private boolean handleSlideFixDirection(CommandSender sender, String zoneName, String[] args) {
        // /sm slide <zoneName> fix <+X/-X/+Z/-Z/none>
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Uso: /sm slide " + zoneName + " fix <+X|-X|+Z|-Z|none>");
            sender.sendMessage(ChatColor.GRAY + "+X: Este, -X: Oeste, +Z: Sur, -Z: Norte, none: Sin dirección fija");
            return true;
        }
        
        String direction = args[3].toUpperCase();
        
        // Validate direction
        if (!direction.equals("+X") && !direction.equals("-X") && 
            !direction.equals("+Z") && !direction.equals("-Z") && 
            !direction.equalsIgnoreCase("NONE")) {
            sender.sendMessage(ChatColor.RED + "Dirección inválida. Usa: +X, -X, +Z, -Z o none");
            sender.sendMessage(ChatColor.GRAY + "+X: Este, -X: Oeste, +Z: Sur, -Z: Norte");
            return true;
        }
        
        // Get the zone
        MenuZone zone = configManager.getMenuZone(zoneName);
        if (zone == null) {
            sender.sendMessage(ChatColor.RED + "Error: No se encontró la zona '" + zoneName + "'.");
            return true;
        }
        
        // Set or clear fixed direction
        if (direction.equalsIgnoreCase("NONE")) {
            zone.setSlideDirection(null);
            configManager.saveConfig();
            sender.sendMessage(ChatColor.GREEN + "¡Dirección fija eliminada de la zona '" + zoneName + "'!");
            sender.sendMessage(ChatColor.GRAY + "Las diapositivas ahora se mostrarán en la dirección del jugador.");
        } else {
            zone.setSlideDirection(direction);
            configManager.saveConfig();
            sender.sendMessage(ChatColor.GREEN + "¡Dirección fija configurada para la zona '" + zoneName + "'!");
            sender.sendMessage(ChatColor.GRAY + "Dirección: " + direction + " (" + getDirectionName(direction) + ")");
            sender.sendMessage(ChatColor.YELLOW + "Todas las diapositivas de esta zona se mostrarán en esta dirección.");
        }
        
        return true;
    }
    
    private boolean handleSlidePositionFix(CommandSender sender, String zoneName, String[] args) {
        // /sm slide <zoneName> posfix [none]
        // Solo jugadores pueden usar este comando porque necesita posición
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Este comando solo puede ser ejecutado por jugadores.");
            return true;
        }
        
        Player player = (Player) sender;
        
        // Get the zone
        MenuZone zone = configManager.getMenuZone(zoneName);
        if (zone == null) {
            sender.sendMessage(ChatColor.RED + "Error: No se encontró la zona '" + zoneName + "'.");
            return true;
        }
        
        // Check for "none" argument to clear
        if (args.length >= 4 && args[3].equalsIgnoreCase("none")) {
            zone.clearSlideFixedLocation();
            configManager.saveConfig();
            sender.sendMessage(ChatColor.GREEN + "¡Posición fija eliminada de la zona '" + zoneName + "'!");
            sender.sendMessage(ChatColor.GRAY + "Las diapositivas ahora se moverán con el jugador.");
            return true;
        }
        
        // Set fixed position from player's current location
        Location playerLoc = player.getEyeLocation();
        zone.setSlideFixedLocation(playerLoc);
        
        // Calculate facing direction from yaw
        float yaw = playerLoc.getYaw();
        String facing;
        if (yaw >= -45 && yaw < 45) {
            facing = "SOUTH"; // +Z
        } else if (yaw >= 45 && yaw < 135) {
            facing = "WEST";  // -X
        } else if (yaw >= 135 || yaw < -135) {
            facing = "NORTH"; // -Z
        } else {
            facing = "EAST";  // +X
        }
        
        zone.setSlideFixedFacing(facing);
        configManager.saveConfig();
        
        sender.sendMessage(ChatColor.GREEN + "¡Posición de renderizado fijada para la zona '" + zoneName + "'!");
        sender.sendMessage(ChatColor.GRAY + "Ubicación: " + String.format("(%.1f, %.1f, %.1f)", playerLoc.getX(), playerLoc.getY(), playerLoc.getZ()));
        sender.sendMessage(ChatColor.GRAY + "Mirando hacia: " + facing);
        sender.sendMessage(ChatColor.YELLOW + "Las diapositivas se mostrarán siempre desde este punto, sin moverse con el jugador.");
        sender.sendMessage(ChatColor.GRAY + "Para desactivar usa: /sm slide " + zoneName + " posfix none");
        
        return true;
    }
    
    // ==================== FIXSLIDE COMMANDS ====================
    
    private boolean handleCreateFixSlide(CommandSender sender, String[] args) {
        // /sm create fixslide <nombre_fixslide> <nombre_zona_slide>
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Uso: /sm create fixslide <nombre_fixslide> <nombre_zona_slide>");
            sender.sendMessage(ChatColor.GRAY + "Crea una zona de presentación fija que comparte slides con una zona SLIDE");
            sender.sendMessage(ChatColor.YELLOW + "Ejemplo: /sm create fixslide mi_presentacion zona_slides");
            return true;
        }
        
        Player player = (Player) sender;
        String fixSlideName = args[2];
        String slideZoneName = args[3];
        
        // Check if fixslide name already exists
        if (configManager.hasMenuZone(fixSlideName)) {
            sender.sendMessage(ChatColor.RED + "Ya existe una zona con el nombre '" + fixSlideName + "'.");
            sender.sendMessage(ChatColor.GRAY + "Usa /sm fixslide list para ver las zonas existentes.");
            return true;
        }
        
        // Check if linked slide zone exists and is SLIDE type
        MenuZone slideZone = configManager.getMenuZone(slideZoneName);
        if (slideZone == null) {
            sender.sendMessage(ChatColor.RED + "No se encontró la zona SLIDE '" + slideZoneName + "'.");
            sender.sendMessage(ChatColor.GRAY + "Usa /sm list para ver las zonas disponibles.");
            return true;
        }
        
        if (!slideZone.hasMenuType() || slideZone.getMenuType() != MenuType.SLIDE) {
            sender.sendMessage(ChatColor.RED + "La zona '" + slideZoneName + "' debe ser de tipo SLIDE.");
            sender.sendMessage(ChatColor.GRAY + "Tipo actual: " + (slideZone.hasMenuType() ? slideZone.getMenuType().getName() : "ninguno"));
            sender.sendMessage(ChatColor.GRAY + "Usa: /sm set menutype " + slideZoneName + " slide");
            return true;
        }
        
        // Create FIXSLIDE zone WITHOUT physical boundaries (it doesn't need them)
        // We use dummy locations since FIXSLIDE zones are not region-based triggers
        Location dummyPos = player.getLocation();
        MenuZone fixSlideZone = new MenuZone(fixSlideName, dummyPos, dummyPos, MenuType.FIXSLIDE);
        fixSlideZone.setLinkedSlideZone(slideZoneName);
        
        configManager.addMenuZone(fixSlideZone);
        configManager.saveConfig();
        
        sender.sendMessage(ChatColor.GREEN + "¡Zona FIXSLIDE '" + fixSlideName + "' creada exitosamente!");
        sender.sendMessage(ChatColor.GRAY + "Vinculada a la zona SLIDE: " + slideZoneName);
        sender.sendMessage(ChatColor.YELLOW + "Próximos pasos:");
        sender.sendMessage(ChatColor.GRAY + "1. /sm fixslide " + fixSlideName + " fix - Fijar posición de renderizado");
        sender.sendMessage(ChatColor.GRAY + "2. /sm fixslide " + fixSlideName + " <+X/-X/+Z/-Z> - Configurar dirección");
        sender.sendMessage(ChatColor.GRAY + "3. /sm fixslide " + fixSlideName + " nextbutton - Posicionar botón siguiente");
        sender.sendMessage(ChatColor.GRAY + "4. /sm fixslide " + fixSlideName + " backbutton - Posicionar botón anterior");
        
        return true;
    }
    
    private boolean handleFixSlideCommand(CommandSender sender, String[] args) {
        // /sm fixslide <list|nombre> [fix|nextbutton|backbutton|+X|-X|+Z|-Z]
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /sm fixslide <list|nombre_zona> [acción]");
            sender.sendMessage(ChatColor.YELLOW + "Acciones disponibles:");
            sender.sendMessage(ChatColor.GRAY + "  list - Listar todas las zonas FIXSLIDE");
            sender.sendMessage(ChatColor.GRAY + "  fix - Fijar posición de renderizado");
            sender.sendMessage(ChatColor.GRAY + "  nextbutton - Posicionar botón siguiente");
            sender.sendMessage(ChatColor.GRAY + "  backbutton - Posicionar botón anterior");
            sender.sendMessage(ChatColor.GRAY + "  +X/-X/+Z/-Z - Cambiar dirección de vista");
            return true;
        }
        
        // Handle list subcommand
        if (args[1].equalsIgnoreCase("list")) {
            return handleFixSlideListCommand(sender);
        }
        
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Este comando solo puede ser usado por jugadores.");
            return true;
        }
        
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Uso: /sm fixslide <nombre> <fix|nextbutton|backbutton|+X|-X|+Z|-Z>");
            return true;
        }
        
        Player player = (Player) sender;
        String zoneName = args[1];
        String action = args[2].toLowerCase();
        
        // Get zone
        MenuZone zone = configManager.getMenuZone(zoneName);
        if (zone == null) {
            sender.sendMessage(ChatColor.RED + "No se encontró la zona '" + zoneName + "'.");
            sender.sendMessage(ChatColor.GRAY + "Usa /sm fixslide list para ver las zonas FIXSLIDE disponibles.");
            return true;
        }
        
        if (!zone.hasMenuType() || zone.getMenuType() != MenuType.FIXSLIDE) {
            sender.sendMessage(ChatColor.RED + "La zona '" + zoneName + "' no es de tipo FIXSLIDE.");
            sender.sendMessage(ChatColor.GRAY + "Tipo actual: " + (zone.hasMenuType() ? zone.getMenuType().getName() : "ninguno"));
            return true;
        }
        
        switch (action) {
            case "fix":
                return handleFixSlideSetPosition(sender, player, zone);
            case "nextbutton":
                return handleFixSlideSetButton(sender, player, zone, true);
            case "backbutton":
                return handleFixSlideSetButton(sender, player, zone, false);
            case "+x":
            case "-x":
            case "+z":
            case "-z":
                return handleFixSlideSetDirection(sender, zone, action.toUpperCase());
            default:
                sender.sendMessage(ChatColor.RED + "Acción inválida. Usa: fix, nextbutton, backbutton, +X, -X, +Z o -Z");
                return true;
        }
    }
    
    private boolean handleFixSlideSetPosition(CommandSender sender, Player player, MenuZone zone) {
        Location playerLoc = player.getEyeLocation();
        zone.setFixSlideRenderLocation(playerLoc);
        
        // Calculate default direction from player yaw
        float yaw = playerLoc.getYaw();
        String direction;
        if (yaw >= -45 && yaw < 45) {
            direction = "+Z"; // South
        } else if (yaw >= 45 && yaw < 135) {
            direction = "-X"; // West
        } else if (yaw >= 135 || yaw < -135) {
            direction = "-Z"; // North
        } else {
            direction = "+X"; // East
        }
        
        zone.setFixSlideDirection(direction);
        configManager.saveConfig();
        
        sender.sendMessage(ChatColor.GREEN + "¡Posición de renderizado fijada para '" + zone.getName() + "'!");
        sender.sendMessage(ChatColor.GRAY + "Ubicación: " + String.format("(%.1f, %.1f, %.1f)", playerLoc.getX(), playerLoc.getY(), playerLoc.getZ()));
        sender.sendMessage(ChatColor.GRAY + "Dirección: " + direction + " (" + getDirectionName(direction) + ")");
        sender.sendMessage(ChatColor.YELLOW + "La presentación se mostrará permanentemente en esta ubicación.");
        
        // Refresh the FIXSLIDE presentation
        if (fixSlideManager != null) {
            fixSlideManager.refreshFixSlide(zone.getName());
            sender.sendMessage(ChatColor.GREEN + "✓ Presentación actualizada");
        }
        
        return true;
    }
    
    private boolean handleFixSlideSetButton(CommandSender sender, Player player, MenuZone zone, boolean isNext) {
        Location buttonLoc = player.getLocation();
        
        if (isNext) {
            zone.setNextButtonLocation(buttonLoc);
            sender.sendMessage(ChatColor.GREEN + "¡Botón 'Siguiente' posicionado!");
        } else {
            zone.setBackButtonLocation(buttonLoc);
            sender.sendMessage(ChatColor.GREEN + "¡Botón 'Anterior' posicionado!");
        }
        
        configManager.saveConfig();
        
        sender.sendMessage(ChatColor.GRAY + "Ubicación: " + String.format("(%.1f, %.1f, %.1f)", 
            buttonLoc.getX(), buttonLoc.getY(), buttonLoc.getZ()));
        sender.sendMessage(ChatColor.YELLOW + "El botón aparecerá en esta ubicación.");
        
        // Refresh only the buttons (not the entire screen)
        if (fixSlideManager != null) {
            fixSlideManager.refreshButtons(zone.getName());
            sender.sendMessage(ChatColor.GREEN + "✓ Botones actualizados");
        }
        
        return true;
    }
    
    private boolean handleFixSlideSetDirection(CommandSender sender, MenuZone zone, String direction) {
        zone.setFixSlideDirection(direction);
        configManager.saveConfig();
        
        sender.sendMessage(ChatColor.GREEN + "¡Dirección configurada para '" + zone.getName() + "'!");
        sender.sendMessage(ChatColor.GRAY + "Dirección: " + direction + " (" + getDirectionName(direction) + ")");
        sender.sendMessage(ChatColor.YELLOW + "La presentación se mostrará en esta dirección.");
        
        // Refresh the FIXSLIDE presentation with new direction
        if (fixSlideManager != null) {
            fixSlideManager.refreshFixSlide(zone.getName());
            sender.sendMessage(ChatColor.GREEN + "✓ Presentación actualizada");
        }
        
        return true;
    }
    
    private boolean handleFixSlideListCommand(CommandSender sender) {
        var zones = configManager.getAllMenuZones();
        
        // Filter only FIXSLIDE zones
        var fixSlideZones = zones.values().stream()
            .filter(zone -> zone.hasMenuType() && zone.getMenuType() == MenuType.FIXSLIDE)
            .collect(java.util.stream.Collectors.toList());
        
        if (fixSlideZones.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No hay zonas FIXSLIDE configuradas.");
            sender.sendMessage(ChatColor.GRAY + "Crea una con: /sm create fixslide <nombre> <zona_slide>");
            return true;
        }
        
        sender.sendMessage(ChatColor.GREEN + "=== Zonas FIXSLIDE ===");
        for (MenuZone zone : fixSlideZones) {
            String status = zone.isDisabled() ? ChatColor.RED + " [DESHABILITADA]" : ChatColor.GREEN + " [ACTIVA]";
            sender.sendMessage(ChatColor.WHITE + "• " + zone.getName() + status);
            
            String linkedZone = zone.getLinkedSlideZone();
            sender.sendMessage(ChatColor.GRAY + "  Vinculada a: " + 
                (linkedZone != null ? linkedZone : ChatColor.RED + "ninguna"));
            
            if (zone.hasFixSlideRenderLocation()) {
                Location loc = zone.getFixSlideRenderLocation();
                sender.sendMessage(ChatColor.GRAY + "  Posición: (" + 
                    String.format("%.1f, %.1f, %.1f", loc.getX(), loc.getY(), loc.getZ()) + ")");
            } else {
                sender.sendMessage(ChatColor.DARK_GRAY + "  Posición: no configurada");
            }
            
            String direction = zone.getFixSlideDirection();
            if (direction != null && !direction.isEmpty()) {
                sender.sendMessage(ChatColor.GRAY + "  Dirección: " + direction);
            } else {
                sender.sendMessage(ChatColor.DARK_GRAY + "  Dirección: no configurada");
            }
            
            boolean hasNextButton = zone.getNextButtonLocation() != null;
            boolean hasBackButton = zone.getBackButtonLocation() != null;
            sender.sendMessage(ChatColor.GRAY + "  Botones: " + 
                (hasNextButton ? "✓" : "✗") + " siguiente, " + 
                (hasBackButton ? "✓" : "✗") + " anterior");
        }
        sender.sendMessage(ChatColor.GRAY + "Total: " + fixSlideZones.size() + " zonas FIXSLIDE");
        
        return true;
    }
    
    // ==================== DISABLED COMMAND ====================
    
    private boolean handleDisabledCommand(CommandSender sender, String[] args) {
        // /sm disabled <nombre_zona>
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /sm disabled <nombre_zona>");
            return true;
        }
        
        String zoneName = args[1];
        MenuZone zone = configManager.getMenuZone(zoneName);
        
        if (zone == null) {
            sender.sendMessage(ChatColor.RED + "No se encontró la zona '" + zoneName + "'.");
            return true;
        }
        
        // Toggle disabled state
        boolean newState = !zone.isDisabled();
        zone.setDisabled(newState);
        configManager.saveConfig();
        
        if (newState) {
            sender.sendMessage(ChatColor.YELLOW + "Zona '" + zoneName + "' DESHABILITADA.");
            sender.sendMessage(ChatColor.GRAY + "La zona no funcionará hasta que sea habilitada nuevamente.");
        } else {
            sender.sendMessage(ChatColor.GREEN + "Zona '" + zoneName + "' HABILITADA.");
            sender.sendMessage(ChatColor.GRAY + "La zona ahora está activa y funcionando.");
        }
        
        return true;
    }
    
    private String getDirectionName(String direction) {
        switch (direction) {
            case "+X": return "Este";
            case "-X": return "Oeste";
            case "+Z": return "Sur";
            case "-Z": return "Norte";
            default: return "Desconocida";
        }
    }
    
    private boolean isValidImageUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        
        url = url.toLowerCase().trim();
        return url.startsWith("http://") || url.startsWith("https://");
    }
    
    private boolean handleReloadCommand(CommandSender sender) {
        // Reload menu zones configuration
        configManager.reload();
        
        // Reload slides configuration  
        slideManager.reload();
        
        sender.sendMessage(ChatColor.GREEN + "Configuración recargada exitosamente.");
        sender.sendMessage(ChatColor.GRAY + "Zonas de menú y slides recargados desde archivos YAML.");
        return true;
    }
    
    private boolean handleChestportCommand(CommandSender sender, String[] args) {
        // Usage: /sm chestport edit {zona} {x} {y} {z} {mundo}
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /sm chestport edit <zona> <x> <y> <z> <mundo>");
            return true;
        }
        
        if (!args[1].equalsIgnoreCase("edit")) {
            sender.sendMessage(ChatColor.RED + "Uso: /sm chestport edit <zona> <x> <y> <z> <mundo>");
            return true;
        }
        
        if (args.length < 7) {
            sender.sendMessage(ChatColor.RED + "Uso: /sm chestport edit <zona> <x> <y> <z> <mundo>");
            return true;
        }
        
        String zoneName = args[2];
        
        // Check if zone exists
        MenuZone zone = configManager.getMenuZone(zoneName);
        if (zone == null) {
            sender.sendMessage(ChatColor.RED + "La zona '" + zoneName + "' no existe.");
            return true;
        }
        
        // Check if zone is CHESTPORT type
        if (zone.getMenuType() != MenuType.CHESTPORT) {
            sender.sendMessage(ChatColor.RED + "La zona '" + zoneName + "' no es de tipo CHESTPORT.");
            sender.sendMessage(ChatColor.GRAY + "Usa: /sm set menutype " + zoneName + " CHESTPORT");
            return true;
        }
        
        try {
            // Parse coordinates
            double x = Double.parseDouble(args[3]);
            double y = Double.parseDouble(args[4]);
            double z = Double.parseDouble(args[5]);
            String worldName = args[6];
            
            // Validate world
            World world = org.bukkit.Bukkit.getWorld(worldName);
            if (world == null) {
                sender.sendMessage(ChatColor.RED + "El mundo '" + worldName + "' no existe.");
                return true;
            }
            
            // Set teleport location
            Location teleportLoc = new Location(world, x, y, z);
            zone.setTeleportLocation(teleportLoc);
            
            // Save configuration
            configManager.saveConfig();
            
            sender.sendMessage(ChatColor.GREEN + "¡Ubicación de teleport configurada para la zona '" + zoneName + "'!");
            sender.sendMessage(ChatColor.GRAY + "Destino: " + formatLocation(teleportLoc));
            sender.sendMessage(ChatColor.GRAY + "Los jugadores que ingresen a la zona verán opciones de teleport.");
            
            return true;
            
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Las coordenadas deben ser números válidos.");
            return true;
        }
    }
    
    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage(ChatColor.GREEN + "=== Comandos de Seminario ===");
        sender.sendMessage(ChatColor.WHITE + "/sm create menuzone <nombre>" + ChatColor.GRAY + " - Crear zona de menú");
        sender.sendMessage(ChatColor.WHITE + "/sm set menutype <zona> <tipo>" + ChatColor.GRAY + " - Establecer tipo de menú");
        sender.sendMessage(ChatColor.WHITE + "/sm slide <zona> add <url>" + ChatColor.GRAY + " - Agregar slide");
        sender.sendMessage(ChatColor.WHITE + "/sm slide <zona> edit <nro> <url>" + ChatColor.GRAY + " - Editar slide");
        sender.sendMessage(ChatColor.WHITE + "/sm slide <zona> delete <nro>" + ChatColor.GRAY + " - Eliminar slide");
        sender.sendMessage(ChatColor.WHITE + "/sm slide <zona> fix <dirección>" + ChatColor.GRAY + " - Fijar dirección de presentación (+X/-X/+Z/-Z/none)");
        sender.sendMessage(ChatColor.WHITE + "/sm chestport edit <zona> <x> <y> <z> <mundo>" + ChatColor.GRAY + " - Configurar teleport");
        sender.sendMessage(ChatColor.WHITE + "/sm createfire <color>" + ChatColor.GRAY + " - Crear fuego artificial trigger");
        sender.sendMessage(ChatColor.WHITE + "/sm newharry <nombre>" + ChatColor.GRAY + " - Crear NPC profesor Harry");
        sender.sendMessage(ChatColor.WHITE + "/sm harry <nombre> addLine <texto>" + ChatColor.GRAY + " - Agregar línea de diálogo");
        sender.sendMessage(ChatColor.WHITE + "/sm spawnpoint set" + ChatColor.GRAY + " - Establecer spawnpoint del servidor");
        sender.sendMessage(ChatColor.WHITE + "/sm remove <nombre>" + ChatColor.GRAY + " - Eliminar zona");
        sender.sendMessage(ChatColor.WHITE + "/sm list" + ChatColor.GRAY + " - Listar todas las zonas");
        sender.sendMessage(ChatColor.WHITE + "/sm info <nombre>" + ChatColor.GRAY + " - Información de zona");
        sender.sendMessage(ChatColor.WHITE + "/sm reload" + ChatColor.GRAY + " - Recargar configuración");
        sender.sendMessage(ChatColor.GRAY + "Tipos disponibles: " + String.join(", ", MenuType.getAvailableTypes()));
    }
    
    private String formatLocation(Location loc) {
        return String.format("%.1f, %.1f, %.1f", loc.getX(), loc.getY(), loc.getZ());
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("seminario.admin")) {
            return Arrays.asList();
        }
        
        if (args.length == 1) {
            return Arrays.asList("create", "remove", "list", "info", "set", "slide", "fixslide", "disabled", "enabled", "reload", "sql", "spawnpoint", "lobby", "survey", "defaultsurvey", "createfire", "createcreeperfire", "firework", "newharry", "harry")
                .stream()
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("create")) {
                return Arrays.asList("menuzone", "fixslide", "SQLDUNGEON")
                    .stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
            
            if (args[0].equalsIgnoreCase("set")) {
                return Arrays.asList("menutype");
            }
            
            if (args[0].equalsIgnoreCase("spawnpoint")) {
                return Arrays.asList("set")
                    .stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
            
            if (args[0].equalsIgnoreCase("lobby")) {
                return Arrays.asList("inventory")
                    .stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
            
            if (args[0].equalsIgnoreCase("slide")) {
                return configManager.getAllMenuZones().keySet()
                    .stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
            
            if (args[0].equalsIgnoreCase("fixslide")) {
                // Suggest "list" or FIXSLIDE zone names
                List<String> suggestions = new java.util.ArrayList<>();
                suggestions.add("list");
                suggestions.addAll(
                    configManager.getAllMenuZones().values()
                        .stream()
                        .filter(zone -> zone.hasMenuType() && zone.getMenuType() == MenuType.FIXSLIDE)
                        .map(MenuZone::getName)
                        .collect(Collectors.toList())
                );
                return suggestions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
            
            if (args[0].equalsIgnoreCase("disabled")) {
                // Suggest all zone names
                return configManager.getAllMenuZones().keySet()
                    .stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
            
            if (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("info")) {
                return configManager.getAllMenuZones().keySet()
                    .stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }
        
        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("create") && args[1].equalsIgnoreCase("fixslide")) {
                // Suggest FIXSLIDE zone name (user types this)
                return Arrays.asList("<nombre_fixslide>");
            }
            
            if (args[0].equalsIgnoreCase("create") && args[1].equalsIgnoreCase("SQLDUNGEON")) {
                // Suggest world names from server
                return org.bukkit.Bukkit.getWorlds().stream()
                    .map(org.bukkit.World::getName)
                    .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
            }
            
            if (args[0].equalsIgnoreCase("fixslide")) {
                // Don't suggest subcommands if "list" was typed
                if (args[1].equalsIgnoreCase("list")) {
                    return Arrays.asList();
                }
                
                // Suggest fixslide subcommands
                return Arrays.asList("list", "fix", "nextbutton", "backbutton", "+X", "-X", "+Z", "-Z")
                    .stream()
                    .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }
        
        if (args.length == 4) {
            if (args[0].equalsIgnoreCase("create") && args[1].equalsIgnoreCase("fixslide")) {
                // Suggest SLIDE zone names to link with
                return configManager.getAllMenuZones().values()
                    .stream()
                    .filter(zone -> zone.hasMenuType() && zone.getMenuType() == MenuType.SLIDE)
                    .map(MenuZone::getName)
                    .filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }
        
        if (args.length == 3 && args[0].equalsIgnoreCase("set") && args[1].equalsIgnoreCase("menutype")) {
            return configManager.getAllMenuZones().keySet()
                .stream()
                .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        if (args.length == 3 && args[0].equalsIgnoreCase("slide")) {
            return Arrays.asList("add", "edit", "delete")
                .stream()
                .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        if (args.length == 4 && args[0].equalsIgnoreCase("set") && args[1].equalsIgnoreCase("menutype")) {
            return Arrays.stream(MenuType.getAvailableTypes())
                .filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        // SQL command tab completion
        if (args[0].equalsIgnoreCase("sql")) {
            if (args.length == 2) {
                return Arrays.asList("here", "schema", "bank", "info", "repair")
                    .stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
            
            if (args.length == 3) {
                if (args[1].equalsIgnoreCase("here")) {
                    return Arrays.asList("lvl", "set")
                        .stream()
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
                }
                
                if (args[1].equalsIgnoreCase("bank")) {
                    return Arrays.asList("info", "regenerate")
                        .stream()
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
                }
            }
            
            if (args.length == 4) {
                if (args[1].equalsIgnoreCase("here") && args[2].equalsIgnoreCase("lvl")) {
                    return Arrays.asList("add", "delete", "edit")
                        .stream()
                        .filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase()))
                        .collect(Collectors.toList());
                }
                
                if (args[1].equalsIgnoreCase("here") && args[2].equalsIgnoreCase("set")) {
                    return Arrays.asList("entry")
                        .stream()
                        .filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase()))
                        .collect(Collectors.toList());
                }
                
                if (args[1].equalsIgnoreCase("bank") && args[2].equalsIgnoreCase("regenerate")) {
                    // Suggest SQL dungeon world names
                    return sqlDungeonManager.getAllSQLWorlds().keySet()
                        .stream()
                        .filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase()))
                        .collect(Collectors.toList());
                }
            }
            
            if (args.length == 5) {
                if (args[1].equalsIgnoreCase("bank") && args[2].equalsIgnoreCase("regenerate")) {
                    // Suggest level numbers for the specified world
                    String worldName = args[3];
                    if (sqlDungeonManager.isSQLDungeon(worldName)) {
                        com.seminario.plugin.model.SQLDungeonWorld sqlWorld = sqlDungeonManager.getSQLDungeon(worldName);
                        return sqlWorld.getLevels().keySet().stream()
                            .map(String::valueOf)
                            .filter(s -> s.startsWith(args[4]))
                            .collect(Collectors.toList());
                    }
                }
            }
            
            if (args.length == 6 && args[1].equalsIgnoreCase("here") && args[2].equalsIgnoreCase("lvl") && args[3].equalsIgnoreCase("add")) {
                return Arrays.asList("1", "2", "3", "4", "5")
                    .stream()
                    .filter(s -> s.toLowerCase().startsWith(args[5].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }
        
        // Survey command tab completion
        if (args[0].equalsIgnoreCase("survey")) {
            if (args.length == 2) {
                return Arrays.asList("create", "add", "edit", "start", "list", "info", "stats")
                    .stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
            
            if (args.length == 3) {
                // For commands that need survey names, suggest existing surveys
                if (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("edit") || 
                    args[1].equalsIgnoreCase("start") || args[1].equalsIgnoreCase("info") || 
                    args[1].equalsIgnoreCase("stats")) {
                    return surveyManager.getAllSurveys().keySet()
                        .stream()
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
                }
            }
        }
        
        // Default survey command tab completion
        if (args[0].equalsIgnoreCase("defaultsurvey")) {
            if (args.length == 2) {
                // Suggest existing survey names
                return surveyManager.getAllSurveys().keySet()
                    .stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }
        
        // Survey edit command specific tab completion
        if (args[0].equalsIgnoreCase("survey") && args.length == 4 && args[1].equalsIgnoreCase("edit")) {
            // For edit command, suggest question numbers
            String surveyName = args[2];
            if (surveyManager.surveyExists(surveyName)) {
                Survey survey = surveyManager.getSurvey(surveyName);
                List<String> questionNumbers = new ArrayList<>();
                for (int i = 1; i <= survey.getQuestionCount(); i++) {
                    questionNumbers.add(String.valueOf(i));
                }
                return questionNumbers.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }
        
        // Firework command tab completion
        if (args[0].equalsIgnoreCase("createfire") || args[0].equalsIgnoreCase("createcreeperfire")) {
            if (args.length >= 2 && args.length <= 4) {
                return com.seminario.plugin.manager.FireworkManager.getAvailableColors()
                    .stream()
                    .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }
        
        if (args[0].equalsIgnoreCase("firework")) {
            if (args.length == 2) {
                return Arrays.asList("list", "remove")
                    .stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
            
            if (args.length == 3 && args[1].equalsIgnoreCase("remove")) {
                return fireworkManager.getAllFireworks()
                    .stream()
                    .map(FireworkTrigger::getId)
                    .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }
        
        if (args[0].equalsIgnoreCase("harry")) {
            if (args.length == 2) {
                // Suggest existing Harry NPC IDs
                return harryNPCManager.getAllNPCs().keySet()
                    .stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
            
            if (args.length == 3) {
                // Suggest actions for Harry NPCs
                return Arrays.asList("addLine", "editLine", "lines", "remove", "reset")
                    .stream()
                    .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }
        
        return Arrays.asList();
    }
    
    /**
     * Handle SQL Dungeon creation
     */
    private boolean handleCreateSQLDungeon(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Este comando solo puede ser usado por jugadores.");
            return true;
        }
        
        Player player = (Player) sender;
        String worldName = args[2];
        
        // Check if world exists
        World world = player.getServer().getWorld(worldName);
        if (world == null) {
            sender.sendMessage(ChatColor.RED + "El mundo '" + worldName + "' no existe.");
            return true;
        }
        
        // Check if already is SQL dungeon
        if (sqlDungeonManager.isSQLDungeon(worldName)) {
            sender.sendMessage(ChatColor.RED + "El mundo '" + worldName + "' ya es un SQL Dungeon.");
            return true;
        }
        
        // Create SQL dungeon
        if (sqlDungeonManager.createSQLDungeon(world)) {
            sender.sendMessage(ChatColor.GREEN + "¡SQL Dungeon creado en el mundo '" + worldName + "'!");
            sender.sendMessage(ChatColor.GRAY + "Usa '/sm sql here lvl add <number> <difficulty>' para agregar niveles.");
        } else {
            sender.sendMessage(ChatColor.RED + "Error al crear el SQL Dungeon.");
        }
        
        return true;
    }
    
    /**
     * Handle SQL commands
     */
    private boolean handleSQLCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Este comando solo puede ser usado por jugadores.");
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /sm sql <here|schema|bank|info|repair>");
            return true;
        }
        
        Player player = (Player) sender;
        String sqlAction = args[1].toLowerCase();
        
        switch (sqlAction) {
            case "here":
                return handleSQLHereCommand(player, args);
            case "schema":
                return handleSQLSchemaCommand(player);
            case "bank":
                return handleSQLBankCommand(sender, args);
            case "info":
                return handleSQLInfoCommand(sender, args);
            case "repair":
                return handleSQLRepairCommand(player, args);
            default:
                sender.sendMessage(ChatColor.RED + "Uso: /sm sql <here|schema|bank|info|repair>");
                return true;
        }
    }
    
    /**
     * Handle SQL 'here' commands (lvl add/delete/edit, set entry)
     */
    private boolean handleSQLHereCommand(Player player, String[] args) {
        String worldName = player.getWorld().getName();
        
        // Check if current world is SQL dungeon
        if (!sqlDungeonManager.isSQLDungeon(worldName)) {
            player.sendMessage(ChatColor.RED + "Este mundo no es un SQL Dungeon. Usa '/sm create SQLDUNGEON " + worldName + "'");
            return true;
        }
        
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Uso: /sm sql here <lvl|set> <args>");
            return true;
        }
        
        String hereAction = args[2].toLowerCase();
        
        switch (hereAction) {
            case "lvl":
                return handleSQLLevelCommand(player, args);
            case "set":
                return handleSQLSetCommand(player, args);
            default:
                player.sendMessage(ChatColor.RED + "Uso: /sm sql here <lvl|set> <args>");
                return true;
        }
    }
    
    /**
     * Handle SQL level commands (add/delete/edit)
     */
    private boolean handleSQLLevelCommand(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(ChatColor.RED + "Uso: /sm sql here lvl <add|delete|edit> <args>");
            return true;
        }
        
        String levelAction = args[3].toLowerCase();
        String worldName = player.getWorld().getName();
        
        switch (levelAction) {
            case "add":
                if (args.length < 6) {
                    player.sendMessage(ChatColor.RED + "Uso: /sm sql here lvl add <number> <difficulty>");
                    player.sendMessage(ChatColor.GRAY + "Dificultad: 1-5 (1=básico, 5=maestro)");
                    return true;
                }
                
                try {
                    int levelNumber = Integer.parseInt(args[4]);
                    int difficulty = Integer.parseInt(args[5]);
                    
                    if (difficulty < 1 || difficulty > 5) {
                        player.sendMessage(ChatColor.RED + "La dificultad debe ser entre 1 y 5.");
                        return true;
                    }
                    
                    Location location = player.getLocation();
                    
                    if (sqlDungeonManager.addLevel(worldName, levelNumber, difficulty, location)) {
                        player.sendMessage(ChatColor.GREEN + "¡Nivel " + levelNumber + " agregado!");
                        player.sendMessage(ChatColor.GRAY + "Dificultad: " + difficulty + "/5");
                        player.sendMessage(ChatColor.YELLOW + "Recuerda configurar el punto de entrada con '/sm sql here set entry'");
                    } else {
                        player.sendMessage(ChatColor.RED + "Error: El nivel " + levelNumber + " ya existe.");
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Los números de nivel y dificultad deben ser enteros válidos.");
                }
                return true;
                
            case "delete":
                if (args.length < 5) {
                    player.sendMessage(ChatColor.RED + "Uso: /sm sql here lvl delete <number>");
                    return true;
                }
                
                try {
                    int levelNumber = Integer.parseInt(args[4]);
                    
                    if (sqlDungeonManager.removeLevel(worldName, levelNumber)) {
                        player.sendMessage(ChatColor.GREEN + "Nivel " + levelNumber + " eliminado.");
                    } else {
                        player.sendMessage(ChatColor.RED + "El nivel " + levelNumber + " no existe.");
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "El número de nivel debe ser un entero válido.");
                }
                return true;
                
            case "edit":
                player.sendMessage(ChatColor.YELLOW + "Función de edición en desarrollo.");
                return true;
                
            default:
                player.sendMessage(ChatColor.RED + "Uso: /sm sql here lvl <add|delete|edit> <args>");
                return true;
        }
    }
    
    /**
     * Handle SQL set commands
     */
    private boolean handleSQLSetCommand(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(ChatColor.RED + "Uso: /sm sql here set <entry>");
            return true;
        }
        
        String setAction = args[3].toLowerCase();
        
        if (setAction.equals("entry")) {
            // Find which level this entry belongs to (for now, ask user to specify)
            if (args.length < 5) {
                player.sendMessage(ChatColor.RED + "Uso: /sm sql here set entry <levelNumber>");
                return true;
            }
            
            try {
                int levelNumber = Integer.parseInt(args[4]);
                String worldName = player.getWorld().getName();
                Location location = player.getLocation();
                
                if (sqlDungeonManager.setLevelEntry(worldName, levelNumber, location)) {
                    player.sendMessage(ChatColor.GREEN + "¡Punto de entrada configurado para el nivel " + levelNumber + "!");
                    player.sendMessage(ChatColor.GRAY + "Los jugadores podrán ingresar consultas SQL aquí.");
                } else {
                    player.sendMessage(ChatColor.RED + "Error: El nivel " + levelNumber + " no existe.");
                }
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "El número de nivel debe ser un entero válido.");
            }
        } else {
            player.sendMessage(ChatColor.RED + "Uso: /sm sql here set <entry>");
        }
        
        return true;
    }
    
    /**
     * Handle SQL schema command
     */
    private boolean handleSQLSchemaCommand(Player player) {
        String schema = sqlDungeonManager.getDatabaseSchema();
        
        // Send schema info line by line to avoid chat limit
        String[] lines = schema.split("\n");
        for (String line : lines) {
            player.sendMessage(ChatColor.AQUA + line);
        }
        
        return true;
    }
    
    /**
     * Handle SQL bank commands for challenge management
     */
    private boolean handleSQLBankCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Uso: /sm sql bank <info|regenerate>");
            sender.sendMessage(ChatColor.GRAY + "  info - Muestra información sobre el banco de consultas");
            sender.sendMessage(ChatColor.GRAY + "  regenerate <mundo> <nivel> - Regenera el challenge de un nivel");
            return true;
        }
        
        String bankAction = args[2].toLowerCase();
        
        switch (bankAction) {
            case "info":
                return handleBankInfoCommand(sender);
            case "regenerate":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Este comando solo puede ser usado por jugadores.");
                    return true;
                }
                return handleRegenerateCommand((Player) sender, args);
            default:
                sender.sendMessage(ChatColor.RED + "Uso: /sm sql bank <info|regenerate>");
                return true;
        }
    }
    
    /**
     * Handle bank info command
     */
    private boolean handleBankInfoCommand(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== BANCO DE CONSULTAS SQL ===");
        sender.sendMessage(ChatColor.YELLOW + "Total de challenges: " + ChatColor.WHITE + sqlDungeonManager.getTotalChallenges());
        sender.sendMessage("");
        
        // Show challenges by difficulty
        for (com.seminario.plugin.model.SQLDifficulty difficulty : com.seminario.plugin.model.SQLDifficulty.values()) {
            int count = sqlDungeonManager.getAvailableChallenges(difficulty);
            sender.sendMessage(ChatColor.AQUA + difficulty.name() + ": " + ChatColor.WHITE + count + " challenges");
        }
        
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GREEN + "Los challenges se asignan automáticamente al crear niveles.");
        sender.sendMessage(ChatColor.GRAY + "Usa '/sm sql bank regenerate <mundo> <nivel>' para cambiar el challenge de un nivel.");
        
        return true;
    }
    
    /**
     * Handle regenerate challenge command
     */
    private boolean handleRegenerateCommand(Player player, String[] args) {
        if (args.length < 5) {
            player.sendMessage(ChatColor.RED + "Uso: /sm sql bank regenerate <mundo> <nivel>");
            return true;
        }
        
        String worldName = args[3];
        
        if (!sqlDungeonManager.isSQLDungeon(worldName)) {
            player.sendMessage(ChatColor.RED + "El mundo '" + worldName + "' no es un SQL Dungeon.");
            return true;
        }
        
        try {
            int levelNumber = Integer.parseInt(args[4]);
            
            if (sqlDungeonManager.regenerateChallenge(worldName, levelNumber)) {
                player.sendMessage(ChatColor.GREEN + "Challenge regenerado para el nivel " + levelNumber + " en " + worldName);
            } else {
                player.sendMessage(ChatColor.RED + "No se pudo regenerar el challenge. Verifica que el nivel exista.");
            }
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "El número de nivel debe ser un número válido.");
        }
        
        return true;
    }
    
    /**
     * Handle SQL info commands
     */
    private boolean handleSQLInfoCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Este comando solo puede ser usado por jugadores.");
            return true;
        }
        
        Player player = (Player) sender;
        String worldName = player.getWorld().getName();
        
        if (!sqlDungeonManager.isSQLDungeon(worldName)) {
            player.sendMessage(ChatColor.RED + "No estás en un mundo SQL Dungeon.");
            return true;
        }
        
        com.seminario.plugin.model.SQLDungeonWorld sqlWorld = sqlDungeonManager.getSQLDungeon(worldName);
        
        // Get player's progress
        int playerProgress = sqlDungeonManager.getPlayerProgress(player, worldName);
        int currentLevel = sqlDungeonManager.getPlayerCurrentLevel(player);
        boolean hasActiveSession = sqlDungeonManager.hasActiveSession(player);
        
        player.sendMessage(ChatColor.GOLD + "=== SQL DUNGEON INFO ===");
        player.sendMessage(ChatColor.YELLOW + "Mundo: " + ChatColor.WHITE + worldName);
        player.sendMessage(ChatColor.YELLOW + "Niveles configurados: " + ChatColor.WHITE + sqlWorld.getLevelCount());
        player.sendMessage(ChatColor.YELLOW + "¿Es jugable?: " + (sqlWorld.isPlayable() ? ChatColor.GREEN + "Sí" : ChatColor.RED + "No"));
        player.sendMessage("");
        
        // Player progress section
        player.sendMessage(ChatColor.LIGHT_PURPLE + "=== TU PROGRESO ===");
        player.sendMessage(ChatColor.AQUA + "Niveles completados: " + ChatColor.WHITE + playerProgress + "/" + sqlWorld.getLevelCount());
        
        if (hasActiveSession && currentLevel > 0) {
            player.sendMessage(ChatColor.YELLOW + "Nivel actual: " + ChatColor.WHITE + currentLevel);
            player.sendMessage(ChatColor.GREEN + "✓ Sesión activa");
        } else {
            if (playerProgress < sqlWorld.getLevelCount()) {
                int nextLevel = playerProgress + 1;
                player.sendMessage(ChatColor.YELLOW + "Siguiente nivel: " + ChatColor.WHITE + nextLevel);
                player.sendMessage(ChatColor.GRAY + "Entra al mundo para comenzar automáticamente");
            } else {
                player.sendMessage(ChatColor.GOLD + "🏆 ¡Has completado todos los niveles!");
            }
        }
        
        if (sqlWorld.getLevelCount() > 0) {
            player.sendMessage("");
            player.sendMessage(ChatColor.AQUA + "Información de niveles:");
            
            for (Integer levelNum : sqlWorld.getLevels().keySet()) {
                com.seminario.plugin.model.SQLLevel level = sqlWorld.getLevel(levelNum);
                String entryStatus = level.hasEntry() ? ChatColor.GREEN + "✓" : ChatColor.RED + "✗";
                String completedStatus = levelNum <= playerProgress ? ChatColor.GREEN + "✓" : ChatColor.GRAY + "○";
                player.sendMessage(ChatColor.GRAY + "  " + completedStatus + " Nivel " + levelNum + " (" + level.getDifficulty() + ") - Entrada: " + entryStatus);
            }
        }
        
        return true;
    }
    
    /**
     * Handle SQL repair command - fixes levels with missing challenges
     */
    private boolean handleSQLRepairCommand(Player player, String[] args) {
        String worldName = player.getWorld().getName();
        
        if (!sqlDungeonManager.isSQLDungeon(worldName)) {
            player.sendMessage(ChatColor.RED + "No estás en un mundo SQL Dungeon.");
            return true;
        }
        
        com.seminario.plugin.model.SQLDungeonWorld sqlWorld = sqlDungeonManager.getSQLDungeon(worldName);
        
        player.sendMessage(ChatColor.YELLOW + "🔧 Reparando niveles en el mundo: " + worldName);
        
        int repairedCount = 0;
        
        for (Integer levelNum : sqlWorld.getLevels().keySet()) {
            com.seminario.plugin.model.SQLLevel level = sqlWorld.getLevel(levelNum);
            
            // Check if level needs repair (missing challenge data)
            if (level.getChallenge() == null || level.getExpectedQuery() == null) {
                player.sendMessage(ChatColor.GRAY + "  Reparando nivel " + levelNum + "...");
                
                if (sqlDungeonManager.regenerateChallenge(worldName, levelNum)) {
                    repairedCount++;
                    player.sendMessage(ChatColor.GREEN + "  ✓ Nivel " + levelNum + " reparado correctamente");
                } else {
                    player.sendMessage(ChatColor.RED + "  ✗ Error al reparar nivel " + levelNum);
                }
            } else {
                player.sendMessage(ChatColor.GREEN + "  ✓ Nivel " + levelNum + " ya está correcto");
            }
        }
        
        if (repairedCount > 0) {
            player.sendMessage("");
            player.sendMessage(ChatColor.GOLD + "🎉 Reparación completada!");
            player.sendMessage(ChatColor.GREEN + "✓ " + repairedCount + " niveles reparados");
            player.sendMessage(ChatColor.YELLOW + "Ahora puedes usar '/sm sql info' para verificar el estado");
        } else {
            player.sendMessage("");
            player.sendMessage(ChatColor.GREEN + "✓ Todos los niveles ya estaban correctos");
        }
        
        return true;
    }
    
    /**
     * Handle test command for debugging
     */
    private boolean handleTestCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Este comando solo puede ser usado por jugadores.");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length < 2) {
            player.sendMessage(ChatColor.YELLOW + "Comandos de test disponibles:");
            player.sendMessage(ChatColor.GRAY + "/sm test chestport - Prueba el lanzamiento del chestport NO");
            player.sendMessage(ChatColor.GRAY + "/sm test slide <zona> - Fuerza iniciar slideshow en zona");
            player.sendMessage(ChatColor.GRAY + "/sm test db - Prueba la base de datos SQL");
            return true;
        }
        
        String testType = args[1].toLowerCase();
        
        switch (testType) {
            case "chestport":
                player.sendMessage(ChatColor.GREEN + "Probando lanzamiento del chestport...");
                com.seminario.plugin.gui.ChestportGUI.handleNoChoice(player);
                return true;
                
            case "slide":
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "Uso: /sm test slide <zona>");
                    return true;
                }
                String zoneName = args[2];
                player.sendMessage(ChatColor.GREEN + "Probando slideshow en zona: " + zoneName);
                
                com.seminario.plugin.manager.SlideShowManager slideShowManager = 
                    ((com.seminario.plugin.App) org.bukkit.plugin.java.JavaPlugin.getPlugin(com.seminario.plugin.App.class))
                    .getSlideShowManager();
                
                if (slideShowManager.startSlideshow(player, zoneName)) {
                    player.sendMessage(ChatColor.GREEN + "Slideshow iniciado correctamente");
                } else {
                    player.sendMessage(ChatColor.RED + "Error al iniciar slideshow");
                }
                return true;
                
            case "db":
                player.sendMessage(ChatColor.GREEN + "Probando base de datos SQL...");
                
                // Test database connection
                if (!sqlDungeonManager.getValidationEngine().testDatabase()) {
                    player.sendMessage(ChatColor.RED + "❌ Error: Base de datos no funciona");
                    return true;
                }
                
                // Test simple query
                try {
                    String testQuery = "SELECT nombre FROM Jugadores WHERE mundo = 'overworld'";
                    com.seminario.plugin.sql.SQLValidationEngine.ValidationResult result = 
                        sqlDungeonManager.getValidationEngine().validateQuery(testQuery, testQuery);
                    
                    if (result.isCorrect()) {
                        player.sendMessage(ChatColor.GREEN + "✅ Base de datos funciona correctamente");
                        player.sendMessage(ChatColor.YELLOW + "Resultados encontrados: " + result.getActualResults().size());
                        if (result.getActualResults().size() > 0) {
                            player.sendMessage(ChatColor.GRAY + "Ejemplo: " + result.getActualResults().get(0));
                        }
                    } else {
                        player.sendMessage(ChatColor.RED + "❌ Error en consulta: " + result.getFeedback());
                        if (result.hasError()) {
                            player.sendMessage(ChatColor.GRAY + "Error técnico: " + result.getError());
                        }
                    }
                } catch (Exception e) {
                    player.sendMessage(ChatColor.RED + "❌ Excepción: " + e.getMessage());
                }
                return true;
                
            default:
                player.sendMessage(ChatColor.RED + "Tipo de test desconocido: " + testType);
                player.sendMessage(ChatColor.GRAY + "Tipos disponibles: chestport, slide, db");
                return true;
        }
    }
    
    /**
     * Handle debug command for troubleshooting
     */
    private boolean handleDebugCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Este comando solo puede ser usado por jugadores.");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length < 2) {
            player.sendMessage(ChatColor.GOLD + "=== COMANDO DEBUG ===");
            player.sendMessage(ChatColor.YELLOW + "Uso: /sm debug <info|worlds|sql>");
            player.sendMessage(ChatColor.GRAY + "  info - Información general del sistema");
            player.sendMessage(ChatColor.GRAY + "  worlds - Lista todos los mundos del servidor");
            player.sendMessage(ChatColor.GRAY + "  sql - Información de mundos SQL Dungeon");
            return true;
        }
        
        String debugType = args[1].toLowerCase();
        
        switch (debugType) {
            case "info":
                return handleDebugInfo(player);
                
            case "worlds":
                return handleDebugWorlds(player);
                
            case "sql":
                return handleDebugSQL(player);
                
            default:
                player.sendMessage(ChatColor.RED + "Tipo de debug desconocido: " + debugType);
                player.sendMessage(ChatColor.GRAY + "Tipos disponibles: info, worlds, sql");
                return true;
        }
    }
    
    private boolean handleDebugInfo(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== INFORMACIÓN DEL SISTEMA ===");
        player.sendMessage(ChatColor.YELLOW + "Mundo actual: " + ChatColor.WHITE + player.getWorld().getName());
        player.sendMessage(ChatColor.YELLOW + "Posición: " + ChatColor.WHITE + 
            (int)player.getLocation().getX() + ", " + 
            (int)player.getLocation().getY() + ", " + 
            (int)player.getLocation().getZ());
        
        // Check if current world is SQL Dungeon
        String worldName = player.getWorld().getName();
        boolean isSQLWorld = sqlDungeonManager.isSQLDungeon(worldName);
        player.sendMessage(ChatColor.YELLOW + "¿Es SQL Dungeon?: " + 
            (isSQLWorld ? ChatColor.GREEN + "SÍ" : ChatColor.RED + "NO"));
        
        if (isSQLWorld) {
            com.seminario.plugin.model.SQLDungeonWorld sqlWorld = sqlDungeonManager.getSQLDungeon(worldName);
            player.sendMessage(ChatColor.YELLOW + "Niveles configurados: " + ChatColor.WHITE + sqlWorld.getLevelCount());
            player.sendMessage(ChatColor.YELLOW + "¿Es jugable?: " + 
                (sqlWorld.isPlayable() ? ChatColor.GREEN + "SÍ" : ChatColor.RED + "NO"));
        }
        
        // Menu zones count
        int menuZoneCount = configManager.getAllMenuZones().size();
        player.sendMessage(ChatColor.YELLOW + "Zonas de menú: " + ChatColor.WHITE + menuZoneCount);
        
        return true;
    }
    
    private boolean handleDebugWorlds(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== MUNDOS DEL SERVIDOR ===");
        
        var worlds = player.getServer().getWorlds();
        player.sendMessage(ChatColor.YELLOW + "Total de mundos: " + ChatColor.WHITE + worlds.size());
        player.sendMessage("");
        
        for (org.bukkit.World world : worlds) {
            String worldName = world.getName();
            boolean isSQLWorld = sqlDungeonManager.isSQLDungeon(worldName);
            String sqlStatus = isSQLWorld ? ChatColor.GREEN + "[SQL]" : ChatColor.GRAY + "[Normal]";
            
            player.sendMessage(ChatColor.WHITE + "• " + worldName + " " + sqlStatus);
            player.sendMessage(ChatColor.GRAY + "  Jugadores: " + world.getPlayerCount() + 
                " | Ambiente: " + world.getEnvironment());
        }
        
        return true;
    }
    
    private boolean handleDebugSQL(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== DEBUG SQL DUNGEON ===");
        
        var allSQLWorlds = sqlDungeonManager.getAllSQLWorlds();
        player.sendMessage(ChatColor.YELLOW + "SQL Dungeons registrados: " + ChatColor.WHITE + allSQLWorlds.size());
        player.sendMessage("");
        
        if (allSQLWorlds.isEmpty()) {
            player.sendMessage(ChatColor.RED + "¡No hay mundos SQL Dungeon registrados!");
            player.sendMessage(ChatColor.GRAY + "Esto podría indicar un problema de carga de configuración.");
            return true;
        }
        
        for (Map.Entry<String, com.seminario.plugin.model.SQLDungeonWorld> entry : allSQLWorlds.entrySet()) {
            String worldName = entry.getKey();
            com.seminario.plugin.model.SQLDungeonWorld sqlWorld = entry.getValue();
            
            // Check if Bukkit world exists
            org.bukkit.World bukkitWorld = player.getServer().getWorld(worldName);
            String worldStatus = bukkitWorld != null ? ChatColor.GREEN + "✓ Existe" : ChatColor.RED + "✗ No encontrado";
            
            player.sendMessage(ChatColor.AQUA + "• " + worldName);
            player.sendMessage(ChatColor.GRAY + "  Estado: " + worldStatus);
            player.sendMessage(ChatColor.GRAY + "  Niveles: " + sqlWorld.getLevelCount() + 
                " | Jugable: " + (sqlWorld.isPlayable() ? "Sí" : "No"));
            player.sendMessage(ChatColor.GRAY + "  Activo: " + (sqlWorld.isActive() ? "Sí" : "No"));
        }
        
        return true;
    }
    
    /**
     * Handle database commands
     */
    private boolean handleDBCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Este comando solo puede ser usado por jugadores.");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length < 2) {
            player.sendMessage(ChatColor.GOLD + "=== COMANDO DB ===");
            player.sendMessage(ChatColor.YELLOW + "Uso: /sm db table <nombre_tabla>");
            player.sendMessage(ChatColor.GRAY + "Tablas disponibles:");
            player.sendMessage(ChatColor.GRAY + "  • Jugadores");
            player.sendMessage(ChatColor.GRAY + "  • Inventarios");
            player.sendMessage(ChatColor.GRAY + "  • Construcciones");
            player.sendMessage(ChatColor.GRAY + "  • Logros");
            player.sendMessage(ChatColor.GRAY + "  • Comercio");
            return true;
        }
        
        String dbAction = args[1].toLowerCase();
        
        if (dbAction.equals("table")) {
            if (args.length < 3) {
                player.sendMessage(ChatColor.RED + "Uso: /sm db table <nombre_tabla>");
                return true;
            }
            
            String tableName = args[2];
            return handleDBTableCommand(player, tableName);
        } else {
            player.sendMessage(ChatColor.RED + "Comando desconocido: " + dbAction);
            player.sendMessage(ChatColor.GRAY + "Usa: /sm db table <nombre_tabla>");
            return true;
        }
    }
    
    /**
     * Handle database table inspection command
     */
    private boolean handleDBTableCommand(Player player, String tableName) {
        // Validate table name
        String[] validTables = {"jugadores", "inventarios", "construcciones", "logros", "comercio"};
        String lowerTableName = tableName.toLowerCase();
        
        boolean isValidTable = false;
        String properTableName = "";
        for (String validTable : validTables) {
            if (validTable.equals(lowerTableName)) {
                isValidTable = true;
                // Convert to proper case
                properTableName = validTable.substring(0, 1).toUpperCase() + validTable.substring(1);
                break;
            }
        }
        
        if (!isValidTable) {
            player.sendMessage(ChatColor.RED + "Tabla no válida: " + tableName);
            player.sendMessage(ChatColor.GRAY + "Tablas disponibles: Jugadores, Inventarios, Construcciones, Logros, Comercio");
            return true;
        }
        
        // Remove existing result books
        com.seminario.plugin.util.SQLResultBook.removeExistingResultBooks(player);
        
        try {
            // Create query to get all data from table
            String query = "SELECT * FROM " + properTableName;
            
            // Use the validation engine to execute the query
            com.seminario.plugin.sql.SQLQueryResult result = 
                sqlDungeonManager.getValidationEngine().validateQueryWithResults(query, query);
            
            // Generate book with table data
            org.bukkit.inventory.ItemStack resultBook = null;
            
            if (result.hasError()) {
                player.sendMessage(ChatColor.RED + "Error al consultar tabla: " + result.getError());
                return true;
            } else if (result.hasResults()) {
                // Create book with all table data
                resultBook = com.seminario.plugin.util.SQLResultBook.createResultBook(
                    player, query, result.getResultSet(), true);
                player.sendMessage(ChatColor.GREEN + "📊 Datos de tabla " + properTableName + " generados");
            } else {
                // Create no results book
                resultBook = com.seminario.plugin.util.SQLResultBook.createNoResultsBook(
                    player, query, true);
                player.sendMessage(ChatColor.YELLOW + "📊 Tabla " + properTableName + " está vacía");
            }
            
            // Give book to player
            if (resultBook != null) {
                player.getInventory().addItem(resultBook);
                player.sendMessage(ChatColor.AQUA + "📖 Revisa el libro para ver todos los datos de la tabla");
            }
            
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Error inesperado: " + e.getMessage());
            e.printStackTrace();
        }
        
        return true;
    }
    
    /**
     * Handle spawnpoint command
     * @param sender The command sender
     * @param args Command arguments
     * @return true if handled
     */
    private boolean handleSpawnpointCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Este comando solo puede ser usado por jugadores.");
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /sm spawnpoint set");
            return true;
        }
        
        String action = args[1].toLowerCase();
        if (!action.equals("set")) {
            sender.sendMessage(ChatColor.RED + "Uso: /sm spawnpoint set");
            return true;
        }
        
        Player player = (Player) sender;
        Location currentLocation = player.getLocation();
        
        // Save spawnpoint using SpawnpointManager
        spawnpointManager.setSpawnpoint(currentLocation);
        
        sender.sendMessage(ChatColor.GREEN + "¡Spawnpoint del servidor establecido exitosamente!");
        sender.sendMessage(ChatColor.GRAY + "Mundo: " + currentLocation.getWorld().getName());
        sender.sendMessage(ChatColor.GRAY + "Ubicación: " + formatLocation(currentLocation));
        
        return true;
    }
    
    /**
     * Handle lobby command
     */
    private boolean handleLobbyCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Este comando solo puede ser usado por jugadores.");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /sm lobby <inventory>");
            return true;
        }
        
        String lobbyAction = args[1].toLowerCase();
        
        switch (lobbyAction) {
            case "inventory":
                return handleLobbyInventoryCommand(player);
            default:
                sender.sendMessage(ChatColor.RED + "Uso: /sm lobby <inventory>");
                return true;
        }
    }
    
    /**
     * Handle lobby inventory command
     */
    private boolean handleLobbyInventoryCommand(Player player) {
        lobbyManager.giveLobbyInventoryToPlayer(player);
        player.sendMessage(ChatColor.GREEN + "¡Inventario del lobby otorgado exitosamente!");
        player.sendMessage(ChatColor.GRAY + "Libro de recomendaciones en slot 1");
        player.sendMessage(ChatColor.GRAY + "Super salto del lobby en slot 8");
        player.sendMessage(ChatColor.GRAY + "Brújula de posicionamiento en slot 9");
        return true;
    }
    
    /**
     * Handle survey command
     */
    private boolean handleSurveyCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendSurveyHelpMessage(sender);
            return true;
        }
        
        String surveyAction = args[1].toLowerCase();
        
        switch (surveyAction) {
            case "create":
                return handleSurveyCreateCommand(sender, args);
            case "add":
                return handleSurveyAddCommand(sender, args);
            case "edit":
                return handleSurveyEditCommand(sender, args);
            case "start":
                return handleSurveyStartCommand(sender, args);
            case "list":
                return handleSurveyListCommand(sender);
            case "info":
                return handleSurveyInfoCommand(sender, args);
            case "stats":
                return handleSurveyStatsCommand(sender, args);
            default:
                sendSurveyHelpMessage(sender);
                return true;
        }
    }
    
    /**
     * Handle survey create command
     */
    private boolean handleSurveyCreateCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Uso: /sm survey create <nombre_encuesta>");
            return true;
        }
        
        String surveyName = args[2];
        
        if (surveyManager.surveyExists(surveyName)) {
            sender.sendMessage(ChatColor.RED + "Ya existe una encuesta con el nombre '" + surveyName + "'.");
            return true;
        }
        
        if (surveyManager.createSurvey(surveyName)) {
            sender.sendMessage(ChatColor.GREEN + "¡Encuesta '" + surveyName + "' creada exitosamente!");
            sender.sendMessage(ChatColor.GRAY + "Usa '/sm survey add " + surveyName + " <pregunta>' para agregar preguntas.");
        } else {
            sender.sendMessage(ChatColor.RED + "Error al crear la encuesta.");
        }
        
        return true;
    }
    
    /**
     * Handle survey add question command
     */
    private boolean handleSurveyAddCommand(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Uso: /sm survey add <nombre_encuesta> <pregunta>");
            return true;
        }
        
        String surveyName = args[2];
        
        if (!surveyManager.surveyExists(surveyName)) {
            sender.sendMessage(ChatColor.RED + "No existe una encuesta con el nombre '" + surveyName + "'.");
            return true;
        }
        
        // Join remaining args as the question
        StringBuilder questionBuilder = new StringBuilder();
        for (int i = 3; i < args.length; i++) {
            questionBuilder.append(args[i]);
            if (i < args.length - 1) {
                questionBuilder.append(" ");
            }
        }
        String question = questionBuilder.toString();
        
        if (surveyManager.addQuestion(surveyName, question)) {
            Survey survey = surveyManager.getSurvey(surveyName);
            sender.sendMessage(ChatColor.GREEN + "¡Pregunta agregada exitosamente!");
            sender.sendMessage(ChatColor.GRAY + "Encuesta: " + surveyName);
            sender.sendMessage(ChatColor.GRAY + "Pregunta #" + survey.getQuestionCount() + ": " + question);
        } else {
            sender.sendMessage(ChatColor.RED + "Error al agregar la pregunta.");
        }
        
        return true;
    }
    
    /**
     * Handle survey edit question command
     */
    private boolean handleSurveyEditCommand(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(ChatColor.RED + "Uso: /sm survey edit <nombre_encuesta> <nro_pregunta> <nueva_pregunta>");
            return true;
        }
        
        String surveyName = args[2];
        
        if (!surveyManager.surveyExists(surveyName)) {
            sender.sendMessage(ChatColor.RED + "No existe una encuesta con el nombre '" + surveyName + "'.");
            return true;
        }
        
        int questionNumber;
        try {
            questionNumber = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "El número de pregunta debe ser un número válido.");
            return true;
        }
        
        // Join remaining args as the new question
        StringBuilder questionBuilder = new StringBuilder();
        for (int i = 4; i < args.length; i++) {
            questionBuilder.append(args[i]);
            if (i < args.length - 1) {
                questionBuilder.append(" ");
            }
        }
        String newQuestion = questionBuilder.toString();
        
        if (surveyManager.editQuestion(surveyName, questionNumber, newQuestion)) {
            sender.sendMessage(ChatColor.GREEN + "¡Pregunta editada exitosamente!");
            sender.sendMessage(ChatColor.GRAY + "Encuesta: " + surveyName);
            sender.sendMessage(ChatColor.GRAY + "Pregunta #" + questionNumber + ": " + newQuestion);
        } else {
            sender.sendMessage(ChatColor.RED + "Error al editar la pregunta. Verifica que el número de pregunta sea válido.");
        }
        
        return true;
    }
    
    /**
     * Handle survey start command
     */
    private boolean handleSurveyStartCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Este comando solo puede ser usado por jugadores.");
            return true;
        }
        
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Uso: /sm survey start <nombre_encuesta>");
            return true;
        }
        
        Player player = (Player) sender;
        String surveyName = args[2];
        
        if (!surveyManager.surveyExists(surveyName)) {
            sender.sendMessage(ChatColor.RED + "No existe una encuesta con el nombre '" + surveyName + "'.");
            return true;
        }
        
        if (surveyManager.startSurvey(player, surveyName)) {
            // Success message is handled by SurveyManager
        } else {
            sender.sendMessage(ChatColor.RED + "Error al iniciar la encuesta.");
        }
        
        return true;
    }
    
    /**
     * Handle survey list command
     */
    private boolean handleSurveyListCommand(CommandSender sender) {
        var surveys = surveyManager.getAllSurveys();
        
        if (surveys.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No hay encuestas creadas.");
            return true;
        }
        
        sender.sendMessage(ChatColor.GREEN + "=== Lista de Encuestas ===");
        for (Survey survey : surveys.values()) {
            sender.sendMessage(ChatColor.GRAY + "• " + ChatColor.WHITE + survey.getName() + 
                             ChatColor.GRAY + " (" + survey.getQuestionCount() + " preguntas, " + 
                             survey.getTotalResponses() + " respuestas)");
        }
        
        return true;
    }
    
    /**
     * Handle survey info command
     */
    private boolean handleSurveyInfoCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Uso: /sm survey info <nombre_encuesta>");
            return true;
        }
        
        String surveyName = args[2];
        Survey survey = surveyManager.getSurvey(surveyName);
        
        if (survey == null) {
            sender.sendMessage(ChatColor.RED + "No existe una encuesta con el nombre '" + surveyName + "'.");
            return true;
        }
        
        sender.sendMessage(ChatColor.GREEN + "=== Información de Encuesta: " + survey.getName() + " ===");
        sender.sendMessage(ChatColor.GRAY + "Preguntas: " + survey.getQuestionCount());
        sender.sendMessage(ChatColor.GRAY + "Respuestas: " + survey.getTotalResponses());
        sender.sendMessage(ChatColor.GRAY + "Promedio general: " + String.format("%.2f", survey.getOverallAverage()) + "/5");
        
        sender.sendMessage(ChatColor.YELLOW + "Preguntas:");
        for (int i = 0; i < survey.getQuestionCount(); i++) {
            sender.sendMessage(ChatColor.GRAY + "" + (i + 1) + ". " + ChatColor.WHITE + survey.getQuestion(i));
        }
        
        return true;
    }
    
    /**
     * Handle survey stats command
     */
    private boolean handleSurveyStatsCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Uso: /sm survey stats <nombre_encuesta>");
            return true;
        }
        
        String surveyName = args[2];
        Survey survey = surveyManager.getSurvey(surveyName);
        
        if (survey == null) {
            sender.sendMessage(ChatColor.RED + "No existe una encuesta con el nombre '" + surveyName + "'.");
            return true;
        }
        
        sender.sendMessage(ChatColor.GREEN + "=== Estadísticas de: " + survey.getName() + " ===");
        sender.sendMessage(ChatColor.GRAY + "Total de respuestas: " + survey.getTotalResponses());
        sender.sendMessage(ChatColor.GRAY + "Promedio general: " + String.format("%.2f", survey.getOverallAverage()) + "/5");
        
        for (int i = 0; i < survey.getQuestionCount(); i++) {
            sender.sendMessage(ChatColor.YELLOW + "Pregunta " + (i + 1) + ": " + survey.getQuestion(i));
            sender.sendMessage(ChatColor.GRAY + "  Promedio: " + String.format("%.2f", survey.getQuestionAverage(i)) + "/5");
            
            var distribution = survey.getQuestionDistribution(i);
            StringBuilder distStr = new StringBuilder("  Distribución: ");
            for (int rating = 1; rating <= 5; rating++) {
                distStr.append(rating).append("★: ").append(distribution.get(rating)).append("  ");
            }
            sender.sendMessage(ChatColor.GRAY + distStr.toString());
        }
        
        return true;
    }
    
    /**
     * Send survey help message
     */
    private void sendSurveyHelpMessage(CommandSender sender) {
        sender.sendMessage(ChatColor.GREEN + "=== Comandos de Encuestas ===");
        sender.sendMessage(ChatColor.YELLOW + "/sm survey create <nombre> - Crear nueva encuesta");
        sender.sendMessage(ChatColor.YELLOW + "/sm survey add <nombre> <pregunta> - Agregar pregunta");
        sender.sendMessage(ChatColor.YELLOW + "/sm survey edit <nombre> <nro> <pregunta> - Editar pregunta");
        sender.sendMessage(ChatColor.YELLOW + "/sm survey start <nombre> - Iniciar encuesta");
        sender.sendMessage(ChatColor.YELLOW + "/sm survey list - Listar encuestas");
        sender.sendMessage(ChatColor.YELLOW + "/sm survey info <nombre> - Info de encuesta");
        sender.sendMessage(ChatColor.YELLOW + "/sm survey stats <nombre> - Estadísticas");
    }
    
    /**
     * Handle create fire command
     */
    private boolean handleCreateFireCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Este comando solo puede ser usado por jugadores.");
            return true;
        }
        
        if (args.length < 2 || args.length > 4) {
            sender.sendMessage(ChatColor.RED + "Uso: /sm createfire <color> [color2] [color3]");
            sender.sendMessage(ChatColor.GRAY + "Colores disponibles: " + String.join(", ", com.seminario.plugin.manager.FireworkManager.getAvailableColors()));
            return true;
        }
        
        Player player = (Player) sender;
        Location location = player.getLocation();
        String id = "firework_" + location.getBlockX() + "_" + location.getBlockY() + "_" + location.getBlockZ();
        
        // Parse colors
        List<Color> colors = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            Color color = com.seminario.plugin.manager.FireworkManager.parseColor(args[i]);
            if (color == null) {
                sender.sendMessage(ChatColor.RED + "Color inválido: " + args[i]);
                sender.sendMessage(ChatColor.GRAY + "Colores disponibles: " + String.join(", ", com.seminario.plugin.manager.FireworkManager.getAvailableColors()));
                return true;
            }
            colors.add(color);
        }
        
        boolean success = fireworkManager.createFirework(id, location, colors, FireworkEffect.Type.BURST);
        if (success) {
            sender.sendMessage(ChatColor.GREEN + "¡Fuego artificial creado en " + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + "!");
            sender.sendMessage(ChatColor.GRAY + "Colores: " + String.join(", ", args).substring(args[0].length() + 1));
            sender.sendMessage(ChatColor.GRAY + "Los jugadores que pisen este bloque activarán el fuego artificial.");
        } else {
            sender.sendMessage(ChatColor.RED + "No se pudo crear el fuego artificial. Ya existe uno en esta posición o el ID está en uso.");
        }
        
        return true;
    }
    
    /**
     * Handle create creeper fire command
     */
    private boolean handleCreateCreeperFireCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Este comando solo puede ser usado por jugadores.");
            return true;
        }
        
        if (args.length < 2 || args.length > 4) {
            sender.sendMessage(ChatColor.RED + "Uso: /sm createcreeperfire <color> [color2] [color3]");
            sender.sendMessage(ChatColor.GRAY + "Colores disponibles: " + String.join(", ", com.seminario.plugin.manager.FireworkManager.getAvailableColors()));
            return true;
        }
        
        Player player = (Player) sender;
        Location location = player.getLocation();
        String id = "creeper_" + location.getBlockX() + "_" + location.getBlockY() + "_" + location.getBlockZ();
        
        // Parse colors
        List<Color> colors = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            Color color = com.seminario.plugin.manager.FireworkManager.parseColor(args[i]);
            if (color == null) {
                sender.sendMessage(ChatColor.RED + "Color inválido: " + args[i]);
                sender.sendMessage(ChatColor.GRAY + "Colores disponibles: " + String.join(", ", com.seminario.plugin.manager.FireworkManager.getAvailableColors()));
                return true;
            }
            colors.add(color);
        }
        
        boolean success = fireworkManager.createFirework(id, location, colors, FireworkEffect.Type.CREEPER);
        if (success) {
            sender.sendMessage(ChatColor.GREEN + "¡Fuego artificial con forma de creeper creado en " + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + "!");
            sender.sendMessage(ChatColor.GRAY + "Colores: " + String.join(", ", args).substring(args[0].length() + 1));
            sender.sendMessage(ChatColor.GRAY + "Los jugadores que pisen este bloque activarán el fuego artificial.");
        } else {
            sender.sendMessage(ChatColor.RED + "No se pudo crear el fuego artificial. Ya existe uno en esta posición o el ID está en uso.");
        }
        
        return true;
    }
    
    /**
     * Handle firework command (list/remove)
     */
    private boolean handleFireworkCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /sm firework <list|remove> [id]");
            return true;
        }
        
        String subCommand = args[1].toLowerCase();
        
        switch (subCommand) {
            case "list":
                return handleFireworkList(sender);
            case "remove":
                return handleFireworkRemove(sender, args);
            default:
                sender.sendMessage(ChatColor.RED + "Subcomando inválido. Usa: list, remove");
                return true;
        }
    }
    
    /**
     * Handle firework list command
     */
    private boolean handleFireworkList(CommandSender sender) {
        List<FireworkTrigger> fireworks = fireworkManager.getAllFireworks();
        
        if (fireworks.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No hay fuegos artificiales configurados.");
            return true;
        }
        
        sender.sendMessage(ChatColor.GREEN + "=== Fuegos Artificiales (" + fireworks.size() + ") ===");
        for (FireworkTrigger trigger : fireworks) {
            Location loc = trigger.getLocation();
            sender.sendMessage(ChatColor.YELLOW + trigger.getId() + ChatColor.GRAY + " - " + 
                             loc.getWorld().getName() + " " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + 
                             " (" + trigger.getColors().size() + " colores, " + trigger.getType().name().toLowerCase() + ")");
        }
        
        return true;
    }
    
    /**
     * Handle firework remove command
     */
    private boolean handleFireworkRemove(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Uso: /sm firework remove <id>");
            sender.sendMessage(ChatColor.GRAY + "Usa '/sm firework list' para ver los IDs disponibles.");
            return true;
        }
        
        String id = args[2];
        FireworkTrigger trigger = fireworkManager.getFireworkById(id);
        
        if (trigger == null) {
            sender.sendMessage(ChatColor.RED + "No existe un fuego artificial con el ID '" + id + "'.");
            sender.sendMessage(ChatColor.GRAY + "Usa '/sm firework list' para ver los IDs disponibles.");
            return true;
        }
        
        boolean success = fireworkManager.removeFirework(id);
        if (success) {
            Location loc = trigger.getLocation();
            sender.sendMessage(ChatColor.GREEN + "¡Fuego artificial '" + id + "' eliminado!");
            sender.sendMessage(ChatColor.GRAY + "Ubicación: " + loc.getWorld().getName() + " " + 
                             loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());
        } else {
            sender.sendMessage(ChatColor.RED + "Error al eliminar el fuego artificial.");
        }
        
        return true;
    }
    
    /**
     * Handle newharry command to create Harry NPCs
     */
    private boolean handleNewHarryCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cEste comando solo puede ser usado por jugadores.");
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage("§cUso: /sm newHarry <nombre>");
            sender.sendMessage("§7Crea un NPC Harry en tu posición actual.");
            return true;
        }
        
        Player player = (Player) sender;
        String npcId = args[1];
        String displayName = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : npcId;
        
        boolean success = harryNPCManager.createNPC(npcId, displayName, player.getLocation(), player);
        return success;
    }
    
    /**
     * Handle harry command for managing Harry NPCs
     */
    private boolean handleHarryCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cEste comando solo puede ser usado por jugadores.");
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage("§cUso: /sm harry <nombre> <acción>");
            sender.sendMessage("§7Acciones disponibles:");
            sender.sendMessage("§7  addLine <texto> - Agregar línea de diálogo");
            sender.sendMessage("§7  editLine <número> <texto> - Editar línea específica");
            sender.sendMessage("§7  lines - Ver todas las líneas");
            sender.sendMessage("§7  remove - Eliminar el NPC");
            sender.sendMessage("§7  reset - Resetear NPC y limpiar hologramas");
            return true;
        }
        
        Player player = (Player) sender;
        String npcId = args[1];
        
        if (args.length < 3) {
            sender.sendMessage("§cEspecifica una acción para Harry '" + npcId + "'.");
            sender.sendMessage("§7Usa: /sm harry " + npcId + " <addLine|editLine|lines|remove|reset>");
            return true;
        }
        
        String action = args[2].toLowerCase();
        
        switch (action) {
            case "addline":
                return handleHarryAddLine(player, npcId, args);
            case "editline":
                return handleHarryEditLine(player, npcId, args);
            case "lines":
                return handleHarryLines(player, npcId);
            case "remove":
                return handleHarryRemove(player, npcId);
            case "reset":
                return handleHarryReset(player, npcId);
            default:
                sender.sendMessage("§cAcción desconocida: " + action);
                sender.sendMessage("§7Acciones válidas: addLine, editLine, lines, remove, reset");
                return true;
        }
    }
    
    /**
     * Handle adding line to Harry NPC
     */
    private boolean handleHarryAddLine(Player player, String npcId, String[] args) {
        if (args.length < 4) {
            player.sendMessage("§cUso: /sm harry " + npcId + " addLine <texto>");
            return true;
        }
        
        String text = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        return harryNPCManager.addLine(npcId, text, player);
    }
    
    /**
     * Handle editing line of Harry NPC
     */
    private boolean handleHarryEditLine(Player player, String npcId, String[] args) {
        if (args.length < 5) {
            player.sendMessage("§cUso: /sm harry " + npcId + " editLine <número> <texto>");
            return true;
        }
        
        try {
            int lineNumber = Integer.parseInt(args[3]);
            String text = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
            return harryNPCManager.editLine(npcId, lineNumber, text, player);
        } catch (NumberFormatException e) {
            player.sendMessage("§cEl número de línea debe ser un número válido.");
            return true;
        }
    }
    
    /**
     * Handle showing lines of Harry NPC
     */
    private boolean handleHarryLines(Player player, String npcId) {
        return harryNPCManager.showLines(npcId, player);
    }
    
    /**
     * Handle removing Harry NPC
     */
    private boolean handleHarryRemove(Player player, String npcId) {
        return harryNPCManager.removeNPC(npcId, player);
    }
    
    /**
     * Handle resetting Harry NPC
     */
    private boolean handleHarryReset(Player player, String npcId) {
        return harryNPCManager.resetNPC(npcId, player);
    }
    
    /**
     * Handle default survey command
     */
    private boolean handleDefaultSurveyCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cEste comando solo puede ser usado por jugadores.");
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage("§cUso: /sm defaultsurvey <nombre_encuesta>");
            sender.sendMessage("§7Establece una encuesta como Post-Test por defecto.");
            sender.sendMessage("§7El Post-Test se activará en la posición actual.");
            return true;
        }
        
        Player player = (Player) sender;
        String surveyName = args[1];
        
        // Check if survey exists
        if (surveyManager.getSurvey(surveyName) == null) {
            player.sendMessage("§cLa encuesta '" + surveyName + "' no existe.");
            player.sendMessage("§7Usa '/sm survey list' para ver las encuestas disponibles.");
            return true;
        }
        
        // Set as default survey at player's current location
        boolean success = surveyManager.setDefaultSurvey(surveyName, player.getLocation());
        
        if (success) {
            player.sendMessage("§a✓ Encuesta '" + surveyName + "' configurada como Post-Test por defecto!");
            player.sendMessage("§7Ubicación: " + formatLocation(player.getLocation()));
            player.sendMessage("§7Los jugadores que completen SQL DUNGEON recibirán un item Post-Test.");
        } else {
            player.sendMessage("§cError al configurar la encuesta por defecto.");
        }
        
        return true;
    }
}