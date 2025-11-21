package com.seminario.plugin.manager;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import com.seminario.plugin.config.ConfigManager;

/**
 * Manages lobby inventory and mechanics
 */
public class LobbyManager {
    
    private static final Logger logger = Logger.getLogger(LobbyManager.class.getName());
    
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final SpawnpointManager spawnpointManager;
    private final SurveyManager surveyManager;
    
    // Lobby super jump tracking
    private final Map<Player, Integer> playerLobbySuperJumps;
    private final Map<Player, BukkitTask> playerRechargeTask;
    private final Map<Player, Long> playerLastJumpTime;
    
    // Constants
    private static final int LOBBY_SUPER_JUMP_HEIGHT = 30;
    private static final int LOBBY_RECHARGE_TIME = 7; // seconds
    private static final int MAX_LOBBY_SUPER_JUMPS = 1;
    
    public LobbyManager(JavaPlugin plugin, ConfigManager configManager, SpawnpointManager spawnpointManager, SurveyManager surveyManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.spawnpointManager = spawnpointManager;
        this.surveyManager = surveyManager;
        this.playerLobbySuperJumps = new HashMap<>();
        this.playerRechargeTask = new HashMap<>();
        this.playerLastJumpTime = new HashMap<>();
        
        logger.info("LobbyManager initialized");
    }
    
    /**
     * Give lobby inventory to a player
     */
    public void giveLobbyInventoryToPlayer(Player player) {
        giveLobbyInventoryToPlayer(player, true);
    }
    
    /**
     * Give lobby inventory to a player with optional Adventure mode
     * @param player The player
     * @param setAdventureMode Whether to set Adventure mode after giving inventory
     */
    public void giveLobbyInventoryToPlayer(Player player, boolean setAdventureMode) {
        logger.info("Giving lobby inventory to " + player.getName());
        
        // Clear inventory completely (main inventory, armor, offhand)
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        
        // Slot 1 (index 0): Recommendations Book
        ItemStack book = createRecommendationsBook();
        player.getInventory().setItem(0, book);
        
        // Slot 8 (index 7): Lobby Super Jump Star
        ItemStack superJumpStar = createLobbySuperJumpStar(player);
        player.getInventory().setItem(7, superJumpStar);
        
        // Slot 9 (index 8): Positioning Compass
        ItemStack compass = createPositioningCompass();
        player.getInventory().setItem(8, compass);
        
        // Initialize super jumps for lobby
        playerLobbySuperJumps.put(player, MAX_LOBBY_SUPER_JUMPS);
        
        // Set Adventure mode AFTER giving inventory
        if (setAdventureMode) {
            player.setGameMode(GameMode.ADVENTURE);
        }
        
        logger.info("Gave lobby inventory to " + player.getName());
    }
    
    /**
     * Give lobby inventory to a player with Post-Test item (for SQL Dungeon completers)
     * @param player The player
     * @param setAdventureMode Whether to set Adventure mode after giving inventory
     */
    public void giveLobbyInventoryWithPostTest(Player player, boolean setAdventureMode) {
        logger.info("Giving lobby inventory with Post-Test to " + player.getName());
        
        // Clear inventory completely (main inventory, armor, offhand)
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        
        // Slot 1 (index 0): Recommendations Book
        ItemStack book = createRecommendationsBook();
        player.getInventory().setItem(0, book);
        
        // Slot 6 (index 5): Post-Test item (only if default survey is configured)
        if (surveyManager != null && surveyManager.hasDefaultSurvey()) {
            logger.info("[DEBUG] Creating Post-Test item for " + player.getName());
            ItemStack postTestItem = surveyManager.createPostTestItem();
            if (postTestItem != null) {
                player.getInventory().setItem(5, postTestItem);
                logger.info("[DEBUG] Post-Test item placed in slot 5 for " + player.getName());
            } else {
                logger.warning("[DEBUG] Failed to create Post-Test item for " + player.getName());
            }
        } else {
            if (surveyManager == null) {
                logger.warning("[DEBUG] SurveyManager is null, cannot give Post-Test item");
            } else {
                logger.info("[DEBUG] No default survey configured, skipping Post-Test item");
            }
        }
        
        // Slot 8 (index 7): Lobby Super Jump Star
        ItemStack superJumpStar = createLobbySuperJumpStar(player);
        player.getInventory().setItem(7, superJumpStar);
        
        // Slot 9 (index 8): Positioning Compass
        ItemStack compass = createPositioningCompass();
        player.getInventory().setItem(8, compass);
        
        // Initialize super jumps for lobby
        playerLobbySuperJumps.put(player, MAX_LOBBY_SUPER_JUMPS);
        
        // Set Adventure mode AFTER giving inventory
        if (setAdventureMode) {
            player.setGameMode(GameMode.ADVENTURE);
        }
        
        logger.info("Gave lobby inventory with Post-Test to " + player.getName());
    }
    
    /**
     * Create recommendations book
     */
    private ItemStack createRecommendationsBook() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta bookMeta = (BookMeta) book.getItemMeta();
        
        bookMeta.setTitle("§6§lGuía del Lobby");
        bookMeta.setAuthor("§eSeminario SQL");
        
        // Add pages with recommendations
        bookMeta.addPage(
            "§l§6¡Bienvenido al Lobby!\n\n" +
            "§0Aquí tienes algunas recomendaciones:\n\n" +
            "§1• Usa la §eEstrella§1 para saltos súper altos\n" +
            "§1• La §eBrújula§1 te devuelve al spawn\n" +
            "§1• Explora los mundos SQL disponibles\n" +
            "§1• ¡Practica tus consultas SQL!"
        );
        
        bookMeta.addPage(
            "§l§4Super Saltos del Lobby\n\n" +
            "§0• §2Altura: §a30 bloques\n" +
            "§0• §2Recarga: §a7 segundos\n" +
            "§0• §2Cantidad: §a1 salto\n\n" +
            "§0La barra de experiencia muestra el tiempo de recarga restante."
        );
        
        bookMeta.addPage(
            "§l§3Navegación\n\n" +
            "§0• La brújula siempre apunta al spawn del lobby\n" +
            "§0• Úsala si te alejas demasiado\n" +
            "§0• Los portales te llevan a diferentes mundos SQL\n\n" +
            "§6¡Que tengas una buena aventura!"
        );
        
        book.setItemMeta(bookMeta);
        return book;
    }
    
    /**
     * Create lobby super jump star
     */
    private ItemStack createLobbySuperJumpStar(Player player) {
        ItemStack star = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = star.getItemMeta();
        
        int jumps = playerLobbySuperJumps.getOrDefault(player, MAX_LOBBY_SUPER_JUMPS);
        
        meta.setDisplayName("§e§lSuper Salto del Lobby");
        meta.setLore(java.util.Arrays.asList(
            "§7Saltos disponibles: §a" + jumps,
            "§7Altura: §630 bloques",
            "§7Recarga: §b7 segundos",
            "",
            "§eClick derecho para usar"
        ));
        
        star.setItemMeta(meta);
        return star;
    }
    
    /**
     * Create positioning compass
     */
    private ItemStack createPositioningCompass() {
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();
        
        meta.setDisplayName("§b§lBrújula de Posicionamiento");
        meta.setLore(java.util.Arrays.asList(
            "§7Te devuelve al spawn del lobby",
            "",
            "§eClick derecho para usar"
        ));
        
        compass.setItemMeta(meta);
        return compass;
    }
    
    /**
     * Use lobby super jump
     */
    public boolean useLobbySuperJump(Player player) {
        int jumps = playerLobbySuperJumps.getOrDefault(player, 0);
        
        if (jumps <= 0) {
            player.sendMessage("§cNo tienes super saltos disponibles. Espera a que se recargue.");
            return false;
        }
        
        // Apply super jump velocity with strong directional boost
        Vector direction = player.getLocation().getDirection();
        Vector velocity = player.getVelocity();
        
        // Vertical component (moderate height)
        velocity.setY(Math.sqrt(2 * 9.8 * LOBBY_SUPER_JUMP_HEIGHT) * 0.25);
        
        // Horizontal component (VERY strong forward momentum)
        velocity.setX(direction.getX() * 3.5 + velocity.getX() * 0.3);
        velocity.setZ(direction.getZ() * 3.5 + velocity.getZ() * 0.3);
        
        player.setVelocity(velocity);
        
        // Apply feather falling effect for safe landing (reduced to 5 seconds)
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 1));
        
        // Play sound effect
        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.5f);
        
        // Consume super jump
        playerLobbySuperJumps.put(player, jumps - 1);
        playerLastJumpTime.put(player, System.currentTimeMillis());
        
        // Update item
        updateLobbySuperJumpItem(player);
        
        // Start recharge timer if no jumps left
        if (jumps - 1 <= 0) {
            startRechargeTimer(player);
        }
        
        logger.info(player.getName() + " used lobby super jump. Remaining: " + (jumps - 1));
        return true;
    }
    
    /**
     * Use positioning compass
     */
    public void usePositioningCompass(Player player) {
        Location spawnLocation = spawnpointManager.getSpawnpointLocation();
        
        if (spawnLocation != null) {
            player.teleport(spawnLocation);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            player.sendMessage("§a¡Has sido teletransportado al spawn del lobby!");
        } else {
            // Fallback to world spawn
            Location worldSpawn = player.getWorld().getSpawnLocation();
            player.teleport(worldSpawn);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            player.sendMessage("§a¡Has sido teletransportado al spawn!");
        }
        
        logger.info(player.getName() + " used positioning compass");
    }
    
    /**
     * Start recharge timer with experience bar animation
     */
    private void startRechargeTimer(Player player) {
        // Cancel existing recharge task if any
        BukkitTask existingTask = playerRechargeTask.get(player);
        if (existingTask != null) {
            existingTask.cancel();
        }
        
        // Create new recharge task
        BukkitTask task = new BukkitRunnable() {
            int ticksElapsed = 0;
            final int totalTicks = LOBBY_RECHARGE_TIME * 20; // 7 seconds * 20 ticks/second
            
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                
                ticksElapsed++;
                float progress = (float) ticksElapsed / totalTicks;
                
                // Update experience bar
                player.setExp(progress);
                player.setLevel(LOBBY_RECHARGE_TIME - (ticksElapsed / 20));
                
                // Check if recharge is complete
                if (ticksElapsed >= totalTicks) {
                    // Recharge complete
                    playerLobbySuperJumps.put(player, MAX_LOBBY_SUPER_JUMPS);
                    updateLobbySuperJumpItem(player);
                    
                    // Reset experience bar
                    player.setExp(0f);
                    player.setLevel(0);
                    
                    // Play recharge sound
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
                    player.sendMessage("§a¡Super salto recargado!");
                    
                    // Remove task from tracking
                    playerRechargeTask.remove(player);
                    
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        
        playerRechargeTask.put(player, task);
    }
    
    /**
     * Update lobby super jump item in player inventory
     */
    private void updateLobbySuperJumpItem(Player player) {
        ItemStack newStar = createLobbySuperJumpStar(player);
        player.getInventory().setItem(7, newStar);
    }
    
    /**
     * Clear lobby session for player
     */
    public void clearLobbySession(Player player) {
        // Cancel recharge task if any
        BukkitTask task = playerRechargeTask.remove(player);
        if (task != null) {
            task.cancel();
        }
        
        // Clear tracking
        playerLobbySuperJumps.remove(player);
        playerLastJumpTime.remove(player);
        
        // Reset experience bar
        player.setExp(0f);
        player.setLevel(0);
        
        logger.info("Cleared lobby session for " + player.getName());
    }
    
    /**
     * Check if player has lobby inventory (is in lobby mode)
     */
    public boolean hasLobbyInventory(Player player) {
        return playerLobbySuperJumps.containsKey(player);
    }
    
    /**
     * Check if a world is the lobby world
     */
    public boolean isLobbyWorld(org.bukkit.World world) {
        Location spawnLocation = spawnpointManager.getSpawnpointLocation();
        return spawnLocation != null && spawnLocation.getWorld().equals(world);
    }
    
    /**
     * Shutdown lobby manager
     */
    public void shutdown() {
        logger.info("Shutting down Lobby system...");
        
        // Cancel all recharge tasks
        for (BukkitTask task : playerRechargeTask.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        
        // Clear all tracking maps
        playerLobbySuperJumps.clear();
        playerRechargeTask.clear();
        playerLastJumpTime.clear();
        
        logger.info("Lobby system shut down complete");
    }
}