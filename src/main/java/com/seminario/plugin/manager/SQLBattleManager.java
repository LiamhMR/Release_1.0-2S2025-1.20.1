package com.seminario.plugin.manager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Ravager;
import org.bukkit.entity.Spider;
import org.bukkit.entity.Witch;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import com.seminario.plugin.config.ConfigManager;
import com.seminario.plugin.model.SQLBattleWorld;
import com.seminario.plugin.sql.battle.BattleExecutionResult;
import com.seminario.plugin.sql.battle.BattleQueryExecutor;
import com.seminario.plugin.sql.battle.BattleQueryValidator;
import com.seminario.plugin.sql.battle.BattleSQLDatabase;
import com.seminario.plugin.sql.battle.BattleValidationResult;

/**
 * Manages SQL Battle world configuration and scenario setup.
 */
public class SQLBattleManager {

    private static final Logger logger = Logger.getLogger(SQLBattleManager.class.getName());
    private static final long BETWEEN_WAVES_TIME = 1000L; // daytime
    private static final long ACTIVE_WAVE_TIME = 12500L;  // dusk/night edge: no undead burning, still visible
    private static final double PREPARATION_RADIUS = 4.5D;
    private static final double ENTRY_ZONE_VERTICAL_TOLERANCE = 2.0D;
    private static final int DEFAULT_WAVE_NUMBER = 1;
    private static final int MIN_ACTION_POINT_COST = 1;
    private static final int FIRST_WAVE_STAGE = 1;
    private static final int GOLEM_SUMMON_ITEM_ID = 10;
    private static final int MAX_SUMMONED_GOLEMS = 4;
    private static final int MAX_PREVIEW_ROWS = 5;
    private static final int MAX_BOOK_ROWS = 30;

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final NamespacedKey sqlBattleOwnerKey;
    private final NamespacedKey sqlBattleSessionKey;
    private final NamespacedKey sqlBattleRoleKey;
    private final Map<UUID, Integer> playerForcedStage;
    private final Map<String, Boolean> worldWaveActive;
    private final Map<UUID, BattlePlayerSession> playerSessions;

    public SQLBattleManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.sqlBattleOwnerKey = new NamespacedKey(plugin, "sqlbattle_owner");
        this.sqlBattleSessionKey = new NamespacedKey(plugin, "sqlbattle_session");
        this.sqlBattleRoleKey = new NamespacedKey(plugin, "sqlbattle_role");
        this.playerForcedStage = new HashMap<>();
        this.worldWaveActive = new HashMap<>();
        this.playerSessions = new HashMap<>();
    }

    public boolean createSQLBattle(World world) {
        if (configManager.isSQLBattle(world.getName())) {
            return false;
        }

        SQLBattleWorld battleWorld = new SQLBattleWorld(world.getName());
        configManager.addSQLBattle(battleWorld);
        setWaveActive(world.getName(), false); // inactivo: mobs desactivados mientras no inicia la oleada
        logger.info("Created SQL Battle in world: " + world.getName());
        return true;
    }

    public boolean removeSQLBattle(String worldName) {
        stopSessionsForWorld(worldName);
        worldWaveActive.remove(worldName);
        return configManager.removeSQLBattle(worldName);
    }

    public boolean isSQLBattle(String worldName) {
        return configManager.isSQLBattle(worldName);
    }

    public SQLBattleWorld getSQLBattle(String worldName) {
        return configManager.getSQLBattle(worldName);
    }

    public Map<String, SQLBattleWorld> getAllSQLBattles() {
        return configManager.getAllSQLBattles();
    }

    public boolean setStartLocation(String worldName, Location location) {
        return setWaveStartLocation(worldName, location);
    }

    public boolean setWorldEntryLocation(String worldName, Location location) {
        SQLBattleWorld battleWorld = configManager.getSQLBattle(worldName);
        if (battleWorld == null) {
            return false;
        }

        battleWorld.setWorldEntryLocation(location);
        configManager.updateSQLBattle(battleWorld);
        return true;
    }

    public boolean setEntryZone(String worldName, Location pos1, Location pos2) {
        SQLBattleWorld battleWorld = configManager.getSQLBattle(worldName);
        if (battleWorld == null) {
            return false;
        }

        battleWorld.setEntryZonePos1(pos1);
        battleWorld.setEntryZonePos2(pos2);
        configManager.updateSQLBattle(battleWorld);
        return true;
    }

    public boolean setWaveStartLocation(String worldName, Location location) {
        SQLBattleWorld battleWorld = configManager.getSQLBattle(worldName);
        if (battleWorld == null) {
            return false;
        }

        battleWorld.setWaveStartLocation(location);
        configManager.updateSQLBattle(battleWorld);
        return true;
    }

    public boolean setPreparationLocation(String worldName, Location location) {
        SQLBattleWorld battleWorld = configManager.getSQLBattle(worldName);
        if (battleWorld == null) {
            return false;
        }

        battleWorld.setPreparationLocation(location);
        configManager.updateSQLBattle(battleWorld);
        return true;
    }

    public boolean setCheckpointLocation(String worldName, Location location) {
        SQLBattleWorld battleWorld = configManager.getSQLBattle(worldName);
        if (battleWorld == null) {
            return false;
        }

        battleWorld.setCheckpointLocation(location);
        configManager.updateSQLBattle(battleWorld);
        return true;
    }

    public boolean setSummonZone(String worldName, Location pos1, Location pos2) {
        SQLBattleWorld battleWorld = configManager.getSQLBattle(worldName);
        if (battleWorld == null) {
            return false;
        }

        battleWorld.setSummonZonePos1(pos1);
        battleWorld.setSummonZonePos2(pos2);
        configManager.updateSQLBattle(battleWorld);
        return true;
    }

    public boolean setEnemySpawnZone(String worldName, Location pos1, Location pos2) {
        SQLBattleWorld battleWorld = configManager.getSQLBattle(worldName);
        if (battleWorld == null) {
            return false;
        }

        battleWorld.setEnemySpawnPos1(pos1);
        battleWorld.setEnemySpawnPos2(pos2);
        configManager.updateSQLBattle(battleWorld);
        return true;
    }

    public boolean isReady(String worldName) {
        SQLBattleWorld battleWorld = configManager.getSQLBattle(worldName);
        return battleWorld != null && battleWorld.isConfigured();
    }

    public boolean isExpandedReady(String worldName) {
        SQLBattleWorld battleWorld = configManager.getSQLBattle(worldName);
        return battleWorld != null && battleWorld.isExpandedConfigured();
    }

    /**
     * Sends SQL Battle configuration diagnostics and paints configured zones with particles.
     */
    public boolean debugShowConfiguration(Player viewer, String worldName) {
        SQLBattleWorld battleWorld = configManager.getSQLBattle(worldName);
        if (battleWorld == null) {
            return false;
        }

        viewer.sendMessage(ChatColor.GOLD + "=== SQL Battle Debug: " + worldName + " ===");
        viewer.sendMessage(ChatColor.WHITE + "Configuración base completa: "
            + (battleWorld.isConfigured() ? ChatColor.GREEN + "sí" : ChatColor.RED + "no"));
        viewer.sendMessage(ChatColor.WHITE + "Modelo extendido completo: "
            + (battleWorld.isExpandedConfigured() ? ChatColor.GREEN + "sí" : ChatColor.RED + "no"));

        if (battleWorld.isExpandedConfigured()) {
            viewer.sendMessage(ChatColor.GREEN + "Estado: Mundo completamente configurado para SQL Battle.");
        } else {
            viewer.sendMessage(ChatColor.RED + "Estado: Configuración incompleta para SQL Battle.");
            viewer.sendMessage(ChatColor.YELLOW + "Faltantes detectados:");
            if (!battleWorld.hasEntryZone() && !battleWorld.hasWorldEntryLocation()) {
                viewer.sendMessage(ChatColor.GRAY + "- Zona de entrada SQL (//wand + 'entry')");
            }
            if (!battleWorld.hasWaveStartLocation()) {
                viewer.sendMessage(ChatColor.GRAY + "- Punto de inicio de oleada (wavestart)");
            }
            if (!battleWorld.hasCheckpointLocation()) {
                viewer.sendMessage(ChatColor.GRAY + "- Checkpoint");
            }
            if (!battleWorld.hasPreparationLocation()) {
                viewer.sendMessage(ChatColor.GRAY + "- Zona prewave");
            }
            if (!battleWorld.hasSummonZone()) {
                viewer.sendMessage(ChatColor.GRAY + "- Zona de invocación (summonzone)");
            }
            if (!battleWorld.hasEnemySpawnZone()) {
                viewer.sendMessage(ChatColor.GRAY + "- Zona de spawn enemigo (enemyspawn)");
            }
        }

        if (battleWorld.hasEntryZone()) {
            paintRegionDebug(viewer, battleWorld.getEntryZonePos1(), battleWorld.getEntryZonePos2(), Particle.END_ROD, "EntryZone");
        } else {
            paintPointDebug(viewer, battleWorld.getWorldEntryLocation(), Particle.END_ROD, "Entry");
        }
        paintPointDebug(viewer, battleWorld.getWaveStartLocation(), Particle.CRIT, "WaveStart");
        paintPointDebug(viewer, battleWorld.getCheckpointLocation(), Particle.TOTEM, "Checkpoint");
        paintPointDebug(viewer, battleWorld.getPreparationLocation(), Particle.ENCHANTMENT_TABLE, "Prewave");
        paintRegionDebug(viewer, battleWorld.getSummonZonePos1(), battleWorld.getSummonZonePos2(), Particle.VILLAGER_HAPPY, "SummonZone");
        paintRegionDebug(viewer, battleWorld.getEnemySpawnPos1(), battleWorld.getEnemySpawnPos2(), Particle.FLAME, "EnemySpawn");

        viewer.sendMessage(ChatColor.AQUA + "Partículas debug emitidas para zonas configuradas.");
        return true;
    }

    private void paintPointDebug(Player viewer, Location location, Particle particle, String label) {
        if (location == null || location.getWorld() == null) {
            return;
        }

        Location base = location.clone().add(0.0D, 0.2D, 0.0D);
        for (int index = 0; index < 12; index++) {
            double y = index * 0.2D;
            viewer.spawnParticle(particle, base.getX(), base.getY() + y, base.getZ(), 1, 0.05D, 0.05D, 0.05D, 0.0D);
        }

        double radius = 1.3D;
        for (int angle = 0; angle < 360; angle += 18) {
            double rad = Math.toRadians(angle);
            double x = base.getX() + (Math.cos(rad) * radius);
            double z = base.getZ() + (Math.sin(rad) * radius);
            viewer.spawnParticle(particle, x, base.getY(), z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }

        viewer.sendMessage(ChatColor.GRAY + "[Debug] " + label + ": "
            + location.getWorld().getName() + " @ "
            + String.format("%.1f, %.1f, %.1f", location.getX(), location.getY(), location.getZ()));
    }

    private void paintRegionDebug(Player viewer, Location pos1, Location pos2, Particle particle, String label) {
        if (pos1 == null || pos2 == null || pos1.getWorld() == null || pos2.getWorld() == null) {
            return;
        }

        double minX = Math.min(pos1.getX(), pos2.getX());
        double maxX = Math.max(pos1.getX(), pos2.getX()) + 1.0D;
        double minY = Math.min(pos1.getY(), pos2.getY());
        double maxY = Math.max(pos1.getY(), pos2.getY()) + 1.0D;
        double minZ = Math.min(pos1.getZ(), pos2.getZ());
        double maxZ = Math.max(pos1.getZ(), pos2.getZ()) + 1.0D;

        // Lower rectangle
        drawLine(viewer, new Location(pos1.getWorld(), minX, minY, minZ), new Location(pos1.getWorld(), maxX, minY, minZ), particle);
        drawLine(viewer, new Location(pos1.getWorld(), maxX, minY, minZ), new Location(pos1.getWorld(), maxX, minY, maxZ), particle);
        drawLine(viewer, new Location(pos1.getWorld(), maxX, minY, maxZ), new Location(pos1.getWorld(), minX, minY, maxZ), particle);
        drawLine(viewer, new Location(pos1.getWorld(), minX, minY, maxZ), new Location(pos1.getWorld(), minX, minY, minZ), particle);

        // Upper rectangle
        drawLine(viewer, new Location(pos1.getWorld(), minX, maxY, minZ), new Location(pos1.getWorld(), maxX, maxY, minZ), particle);
        drawLine(viewer, new Location(pos1.getWorld(), maxX, maxY, minZ), new Location(pos1.getWorld(), maxX, maxY, maxZ), particle);
        drawLine(viewer, new Location(pos1.getWorld(), maxX, maxY, maxZ), new Location(pos1.getWorld(), minX, maxY, maxZ), particle);
        drawLine(viewer, new Location(pos1.getWorld(), minX, maxY, maxZ), new Location(pos1.getWorld(), minX, maxY, minZ), particle);

        // Vertical edges
        drawLine(viewer, new Location(pos1.getWorld(), minX, minY, minZ), new Location(pos1.getWorld(), minX, maxY, minZ), particle);
        drawLine(viewer, new Location(pos1.getWorld(), maxX, minY, minZ), new Location(pos1.getWorld(), maxX, maxY, minZ), particle);
        drawLine(viewer, new Location(pos1.getWorld(), maxX, minY, maxZ), new Location(pos1.getWorld(), maxX, maxY, maxZ), particle);
        drawLine(viewer, new Location(pos1.getWorld(), minX, minY, maxZ), new Location(pos1.getWorld(), minX, maxY, maxZ), particle);

        viewer.sendMessage(ChatColor.GRAY + "[Debug] " + label + ": "
            + pos1.getWorld().getName() + " ["
            + String.format("%.1f, %.1f, %.1f", pos1.getX(), pos1.getY(), pos1.getZ())
            + " -> "
            + String.format("%.1f, %.1f, %.1f", pos2.getX(), pos2.getY(), pos2.getZ())
            + "]");
    }

    private void drawLine(Player viewer, Location from, Location to, Particle particle) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double distance = Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
        int points = Math.max(6, Math.min(180, (int) (distance * 4.0D)));

        for (int index = 0; index <= points; index++) {
            double t = (double) index / (double) points;
            double x = from.getX() + (dx * t);
            double y = from.getY() + (dy * t);
            double z = from.getZ() + (dz * t);
            viewer.spawnParticle(particle, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    public void setWaveActive(String worldName, boolean active) {
        worldWaveActive.put(worldName, active);
        World world = plugin.getServer().getWorld(worldName);
        if (world != null) {
            world.setDifficulty(active ? Difficulty.NORMAL : Difficulty.PEACEFUL);
            world.setStorm(false);
            world.setThundering(false);
            world.setWeatherDuration(0);
            world.setThunderDuration(0);
            world.setTime(active ? ACTIVE_WAVE_TIME : BETWEEN_WAVES_TIME);
        }
    }

    public boolean isWaveActive(String worldName) {
        return worldWaveActive.getOrDefault(worldName, false);
    }

    public boolean setWorldDifficulty(String worldName, Difficulty difficulty) {
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            return false;
        }
        world.setDifficulty(difficulty);
        return true;
    }

    public Difficulty getWorldDifficulty(String worldName) {
        World world = plugin.getServer().getWorld(worldName);
        return world != null ? world.getDifficulty() : null;
    }

    /**
     * Starts SQL Battle test flow for a single player by teleporting to start and
     * setting checkpoint respawn.
     */
    public boolean startForPlayer(Player player) {
        String worldName = player.getWorld().getName();
        SQLBattleWorld battleWorld = configManager.getSQLBattle(worldName);
        if (battleWorld == null || !battleWorld.isConfigured()) {
            return false;
        }

        return beginPreparationSession(player, battleWorld);
    }

    /**
     * Starts SQL Battle test flow for all players currently in the configured world.
     */
    public int startForWorld(World world) {
        SQLBattleWorld battleWorld = configManager.getSQLBattle(world.getName());
        if (battleWorld == null || !battleWorld.isConfigured()) {
            return -1;
        }

        int count = 0;
        for (Player player : world.getPlayers()) {
            if (beginPreparationSession(player, battleWorld)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Sends player to the configured checkpoint and updates respawn there.
     */
    public boolean respawnAtCheckpoint(Player player) {
        String worldName = player.getWorld().getName();
        SQLBattleWorld battleWorld = configManager.getSQLBattle(worldName);
        if (battleWorld == null || !battleWorld.hasCheckpointLocation()) {
            return false;
        }

        player.teleport(battleWorld.getCheckpointLocation());
        player.setBedSpawnLocation(battleWorld.getCheckpointLocation(), true);
        return true;
    }

    /**
     * Resets a player state for manual SQL Battle testing.
     */
    public boolean resetPlayerForDebug(Player player) {
        String worldName = player.getWorld().getName();
        SQLBattleWorld battleWorld = configManager.getSQLBattle(worldName);
        if (battleWorld == null || (!battleWorld.hasStartLocation() && !battleWorld.hasCheckpointLocation())) {
            return false;
        }

        endPreparationSession(player, true);

        if (battleWorld.hasCheckpointLocation()) {
            player.teleport(battleWorld.getCheckpointLocation());
            player.setBedSpawnLocation(battleWorld.getCheckpointLocation(), true);
        } else {
            player.teleport(battleWorld.getStartLocation());
        }

        double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH) != null
            ? player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue()
            : 20.0D;
        player.setHealth(Math.min(maxHealth, 20.0D));
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setFireTicks(0);
        player.setFallDistance(0f);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        clearForcedStage(player);
        return true;
    }

    public int resetWorldForDebug(World world) {
        SQLBattleWorld battleWorld = configManager.getSQLBattle(world.getName());
        if (battleWorld == null || (!battleWorld.hasStartLocation() && !battleWorld.hasCheckpointLocation())) {
            return -1;
        }

        setWaveActive(world.getName(), false); // sin oleada activa: PEACEFUL

        int count = 0;
        for (Player player : world.getPlayers()) {
            if (resetPlayerForDebug(player)) {
                count++;
            }
        }
        return count;
    }

    public void setForcedStage(Player player, int stage) {
        playerForcedStage.put(player.getUniqueId(), stage);
    }

    public int getForcedStage(Player player) {
        return playerForcedStage.getOrDefault(player.getUniqueId(), -1);
    }

    public void clearForcedStage(Player player) {
        playerForcedStage.remove(player.getUniqueId());
        updatePreparationSidebar(player);
    }

    public boolean shouldCapturePreparationChat(Player player) {
        BattlePlayerSession session = getActiveSession(player);
        return session != null
            && session.phase == BattleSessionPhase.PREPARATION
            && isPlayerInPreparationZone(player);
    }

    public void handlePreparationChat(Player player, String message) {
        BattlePlayerSession session = getActiveSession(player);
        if (session == null) {
            return;
        }

        String trimmed = message.trim();
        if (trimmed.isEmpty()) {
            player.sendMessage(ChatColor.RED + "La consulta está vacía.");
            return;
        }

        if (trimmed.equalsIgnoreCase("help") || trimmed.equalsIgnoreCase("ayuda")) {
            showPreparationHelp(player);
            return;
        }

        if (trimmed.equalsIgnoreCase("costos") || trimmed.equalsIgnoreCase("costes") || trimmed.equalsIgnoreCase("costs")) {
            showPreparationCosts(player);
            return;
        }

        if (trimmed.equalsIgnoreCase("status") || trimmed.equalsIgnoreCase("estado")) {
            showPreparationStatus(player, session);
            return;
        }

        if (trimmed.equalsIgnoreCase("exit") || trimmed.equalsIgnoreCase("salir")) {
            endPreparationSession(player, true);
            player.sendMessage(ChatColor.YELLOW + "Sesión SQL Battle finalizada.");
            return;
        }

        processBattleQuery(player, session, trimmed);
    }

    public void cleanupPlayerSession(Player player) {
        endPreparationSession(player, true);
    }

    public void showSchemaOverview(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== SQL Battle Schema (Provisional) ===");
        player.sendMessage(ChatColor.YELLOW + "jugador" + ChatColor.GRAY + "(id, nombre, hp, mana, puntos_accion, oleada_actual, etapa_actual)");
        player.sendMessage(ChatColor.YELLOW + "tipos_item" + ChatColor.GRAY + "(id, nombre, categoria, costo_mana, etapa_activacion)");
        player.sendMessage(ChatColor.YELLOW + "almacen" + ChatColor.GRAY + "(item_id, cantidad)");
        player.sendMessage(ChatColor.YELLOW + "inventario" + ChatColor.GRAY + "(item_id, cantidad, activo_en_etapa)");
        player.sendMessage(ChatColor.YELLOW + "tipos_enemigo" + ChatColor.GRAY + "(id, nombre, debilidad, descripcion)");
        player.sendMessage(ChatColor.YELLOW + "enemigos" + ChatColor.GRAY + "(id, tipo_id, hp, hp_max, estado, etapa_aparicion)");
        player.sendMessage(ChatColor.DARK_GRAY + "JOIN tipico: enemigos.tipo_id -> tipos_enemigo.id, inventario/almacen.item_id -> tipos_item.id");
    }

    public void stopSessionsForWorld(String worldName) {
        Map<UUID, BattlePlayerSession> snapshot = new HashMap<>(playerSessions);
        for (Map.Entry<UUID, BattlePlayerSession> entry : snapshot.entrySet()) {
            BattlePlayerSession session = entry.getValue();
            if (!session.worldName.equalsIgnoreCase(worldName)) {
                continue;
            }

            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                endPreparationSession(player, true);
            } else {
                session.database.close();
                playerSessions.remove(entry.getKey());
            }
        }
    }

    public void shutdown() {
        for (BattlePlayerSession session : playerSessions.values()) {
            session.database.close();
        }
        playerSessions.clear();
    }

    public void logStatus(String worldName) {
        SQLBattleWorld battleWorld = configManager.getSQLBattle(worldName);
        if (battleWorld == null) {
            logger.warning("SQL Battle not found: " + worldName);
            return;
        }

        Difficulty difficulty = getWorldDifficulty(worldName);
        logger.info("SQL Battle '" + worldName + "' ready=" + battleWorld.isConfigured()
            + ", waveActive=" + isWaveActive(worldName)
            + ", difficulty=" + (difficulty != null ? difficulty : "unknown"));
    }

    private boolean beginPreparationSession(Player player, SQLBattleWorld battleWorld) {
        endPreparationSession(player, false);

        BattleSQLDatabase database = new BattleSQLDatabase(plugin.getLogger());
        if (!database.initialize()) {
            return false;
        }

        try {
            database.loadWave(DEFAULT_WAVE_NUMBER);
            int forcedStage = getForcedStage(player);
            if (forcedStage > 0) {
                database.setCurrentStage(forcedStage);
            }
        } catch (Exception e) {
            database.close();
            logger.warning("Could not initialize SQL Battle wave for player '" + player.getName() + "': " + e.getMessage());
            return false;
        }

        BattlePlayerSession session = new BattlePlayerSession(battleWorld, database);
        playerSessions.put(player.getUniqueId(), session);

        setWaveActive(battleWorld.getWorldName(), false);
        player.teleport(battleWorld.getPreparationLocation());
        player.setBedSpawnLocation(battleWorld.getCheckpointLocation(), true);

        createPreparationSidebar(player);
        showPreparationIntro(player);
        return true;
    }

    private void endPreparationSession(Player player, boolean restoreMainScoreboard) {
        BattlePlayerSession session = playerSessions.remove(player.getUniqueId());
        if (session != null) {
            removeTrackedEntities(session.enemyEntityIds);
            removeTrackedEntities(session.summonedEntityIds);
            session.database.close();
        }

        if (restoreMainScoreboard) {
            ScoreboardManager manager = Bukkit.getScoreboardManager();
            if (manager != null) {
                player.setScoreboard(manager.getMainScoreboard());
            }
        }
    }

    private BattlePlayerSession getActiveSession(Player player) {
        return playerSessions.get(player.getUniqueId());
    }

    private boolean isPlayerInPreparationZone(Player player) {
        BattlePlayerSession session = getActiveSession(player);
        if (session == null) {
            return false;
        }

        SQLBattleWorld battleWorld = configManager.getSQLBattle(session.worldName);
        if (battleWorld == null) {
            return false;
        }

        Location current = player.getLocation();

        // Prefer wand-defined region over radius-based check
        if (battleWorld.hasEntryZone()) {
            return isInsideRegion(battleWorld.getEntryZonePos1(), battleWorld.getEntryZonePos2(), current);
        }

        if (!battleWorld.hasPreparationLocation()) {
            return false;
        }

        Location prep = battleWorld.getPreparationLocation();
        if (prep.getWorld() == null || current.getWorld() == null) {
            return false;
        }
        if (!prep.getWorld().equals(current.getWorld())) {
            return false;
        }

        return prep.distance(current) <= PREPARATION_RADIUS;
    }

    private boolean isInsideRegion(Location pos1, Location pos2, Location check) {
        if (pos1 == null || pos2 == null || check == null) return false;
        if (pos1.getWorld() == null || check.getWorld() == null) return false;
        if (!pos1.getWorld().equals(check.getWorld())) return false;
        double minX = Math.min(pos1.getX(), pos2.getX());
        double maxX = Math.max(pos1.getX(), pos2.getX());
        double minY = Math.min(pos1.getY(), pos2.getY()) - ENTRY_ZONE_VERTICAL_TOLERANCE;
        double maxY = Math.max(pos1.getY(), pos2.getY()) + ENTRY_ZONE_VERTICAL_TOLERANCE;
        double minZ = Math.min(pos1.getZ(), pos2.getZ());
        double maxZ = Math.max(pos1.getZ(), pos2.getZ());
        double x = check.getX(), y = check.getY(), z = check.getZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    private void processBattleQuery(Player player, BattlePlayerSession session, String query) {
        try {
            int currentPoints = session.database.getPlayerActionPoints();
            if (!hasAnyAffordableQuery(currentPoints)) {
                beginWavePhase(player, session, "Se agotaron tus puntos de acción disponibles.");
                return;
            }

            BattleValidationResult validation = session.validator.validate(query);
            if (!validation.isAllowed()) {
                player.sendMessage(ChatColor.RED + validation.getReason());
                player.sendMessage(ChatColor.GRAY + "No se descontaron puntos de acción.");
                updatePreparationSidebar(player);
                return;
            }

            int cost = validation.getActionPointCost();
            if (cost > currentPoints) {
                if (!hasAnyAffordableQuery(currentPoints)) {
                    beginWavePhase(player, session, "No quedan consultas posibles con tus puntos actuales.");
                    return;
                }

                player.sendMessage(ChatColor.RED + "No tienes suficientes puntos de acción para esa consulta.");
                player.sendMessage(ChatColor.GRAY + "Costo: " + cost + " AP | Disponibles: " + currentPoints + " AP");
                updatePreparationSidebar(player);
                return;
            }

            BattleExecutionResult result = session.executor.execute(session.database.getConnection(), query);
            if (!result.isSuccess()) {
                player.sendMessage(ChatColor.RED + "Consulta rechazada o fallida: " + result.getErrorMessage());
                player.sendMessage(ChatColor.GRAY + "No se descontaron puntos de acción.");
                updatePreparationSidebar(player);
                return;
            }

            int remainingPoints = Math.max(0, currentPoints - cost);
            session.database.setPlayerActionPoints(remainingPoints);

            player.sendMessage(ChatColor.GREEN + "Consulta ejecutada correctamente." );
            player.sendMessage(ChatColor.WHITE + "Tipo: " + ChatColor.GRAY + result.getQueryType()
                    + ChatColor.WHITE + " | Costo: " + ChatColor.GRAY + cost + " AP"
                    + ChatColor.WHITE + " | Restantes: " + ChatColor.GRAY + remainingPoints + " AP");
            player.sendMessage(ChatColor.WHITE + "Filas afectadas/devueltas: " + ChatColor.GRAY + result.getRowsAffected());
            if (!result.getTablesAccessed().isEmpty()) {
                player.sendMessage(ChatColor.WHITE + "Tablas: " + ChatColor.GRAY + String.join(", ", result.getTablesAccessed()));
            }
            if (result.isUsedJoin()) {
                player.sendMessage(ChatColor.AQUA + "Bonus por JOIN: +" + result.getJoinBonusPercent() + "%");
            }

            showQueryResultPreview(player, result);
            deliverQueryResultBook(player, query, result);

            updatePreparationSidebar(player);

            if (!hasAnyAffordableQuery(remainingPoints)) {
                beginWavePhase(player, session, "Se terminó la fase prewave: comienza la oleada.");
            }
        } catch (Exception e) {
            logger.warning("Error while executing SQL Battle query for '" + player.getName() + "': " + e.getMessage());
            player.sendMessage(ChatColor.RED + "Error interno al procesar la consulta.");
            player.sendMessage(ChatColor.GRAY + "No se descontaron puntos de acción.");
            updatePreparationSidebar(player);
        }
    }

    private void showPreparationIntro(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== SQL Battle: Preparación ===");
        player.sendMessage(ChatColor.GREEN + "Escribe consultas SQL en el chat dentro de la zona prewave.");
        player.sendMessage(ChatColor.GRAY + "Comandos: help, costos, estado, salir");
        player.sendMessage(ChatColor.GRAY + "Las consultas erróneas no descuentan puntos.");
        player.sendMessage(ChatColor.GRAY + "Cuando ya no tengas AP suficientes, la oleada comenzará automáticamente.");
    }

    private void showWaveIntro(Player player, BattlePlayerSession session) {
        player.sendMessage(ChatColor.RED + "=== SQL Battle: Oleada activa ===");
        player.sendMessage(ChatColor.YELLOW + "La fase de preparación terminó. Defiéndete de la oleada actual.");
        player.sendMessage(ChatColor.GRAY + "Oleada: " + session.lastKnownWave + " | Etapa: " + session.lastKnownStage);
        player.sendMessage(ChatColor.GRAY + "Enemigos desplegados: " + getTrackedLiveCount(session.enemyEntityIds)
            + " | Invocaciones: " + getTrackedLiveCount(session.summonedEntityIds));
    }

    private void showPreparationHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Ayuda SQL Battle ===");
        player.sendMessage(ChatColor.WHITE + "Chat SQL activo solo dentro del prewave.");
        player.sendMessage(ChatColor.WHITE + "SELECT cuesta 1 AP");
        player.sendMessage(ChatColor.WHITE + "INSERT cuesta 2 AP");
        player.sendMessage(ChatColor.WHITE + "UPDATE cuesta 2 AP");
        player.sendMessage(ChatColor.WHITE + "DELETE cuesta 3 AP");
        player.sendMessage(ChatColor.GRAY + "Comandos rápidos: costos, estado, salir");
    }

    private void showPreparationCosts(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Costos SQL Battle ===");
        player.sendMessage(ChatColor.WHITE + "SELECT: " + ChatColor.GREEN + "1 AP");
        player.sendMessage(ChatColor.WHITE + "INSERT: " + ChatColor.YELLOW + "2 AP");
        player.sendMessage(ChatColor.WHITE + "UPDATE: " + ChatColor.YELLOW + "2 AP");
        player.sendMessage(ChatColor.WHITE + "DELETE: " + ChatColor.RED + "3 AP");
    }

    private void showPreparationStatus(Player player, BattlePlayerSession session) {
        try {
            int points = session.database.getPlayerActionPoints();
            int wave = session.database.getCurrentWaveNumber();
            int stage = getDisplayedStage(player, session);
            player.sendMessage(ChatColor.GOLD + "=== Estado SQL Battle ===");
            player.sendMessage(ChatColor.WHITE + "Fase: " + ChatColor.AQUA + session.phase.getDisplayName());
            player.sendMessage(ChatColor.WHITE + "Sesión: " + ChatColor.GRAY + session.getSessionAgeSeconds() + "s");
            player.sendMessage(ChatColor.WHITE + "Oleada: " + ChatColor.AQUA + wave);
            player.sendMessage(ChatColor.WHITE + "Etapa: " + ChatColor.AQUA + stage);
            player.sendMessage(ChatColor.WHITE + "Puntos de acción: " + ChatColor.GREEN + points);
            player.sendMessage(ChatColor.WHITE + "Modelo extendido: " + (session.hasExpandedZoneModel() ? ChatColor.GREEN + "listo" : ChatColor.YELLOW + "parcial"));
            if (!session.lastPreparationEndReason.isEmpty()) {
                player.sendMessage(ChatColor.WHITE + "Última transición: " + ChatColor.GRAY + session.lastPreparationEndReason);
            }
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "No se pudo consultar el estado actual.");
        }
    }

    private void createPreparationSidebar(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }

        Scoreboard scoreboard = manager.getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("sqlbattle", "dummy",
                net.kyori.adventure.text.Component.text("SQL Battle", net.kyori.adventure.text.format.NamedTextColor.GOLD));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        updatePreparationSidebarContent(player, scoreboard, objective);
        player.setScoreboard(scoreboard);

        BattlePlayerSession session = getActiveSession(player);
        if (session != null) {
            session.scoreboard = scoreboard;
        }
    }

    private void updatePreparationSidebar(Player player) {
        BattlePlayerSession session = getActiveSession(player);
        if (session == null || session.scoreboard == null) {
            return;
        }

        Objective objective = session.scoreboard.getObjective("sqlbattle");
        if (objective == null) {
            return;
        }

        updatePreparationSidebarContent(player, session.scoreboard, objective);
    }

    private void updatePreparationSidebarContent(Player player, Scoreboard scoreboard, Objective objective) {
        for (String entry : scoreboard.getEntries()) {
            scoreboard.resetScores(entry);
        }

        BattlePlayerSession session = getActiveSession(player);
        if (session == null) {
            return;
        }

        int points = 0;
        int wave = DEFAULT_WAVE_NUMBER;
        int stage = 0;
        try {
            points = session.database.getPlayerActionPoints();
            wave = session.database.getCurrentWaveNumber();
            stage = getDisplayedStage(player, session);
            session.lastKnownWave = wave;
            session.lastKnownStage = stage;
        } catch (Exception e) {
            logger.warning("Could not update SQL Battle sidebar for '" + player.getName() + "': " + e.getMessage());
        }

        int score = 12;
        objective.getScore(ChatColor.GOLD + "Fase: " + ChatColor.WHITE + session.phase.getSidebarLabel()).setScore(score--);
        objective.getScore(ChatColor.YELLOW + "Oleada: " + ChatColor.WHITE + wave).setScore(score--);
        objective.getScore(ChatColor.YELLOW + "Etapa: " + ChatColor.WHITE + stage).setScore(score--);
        objective.getScore(ChatColor.GREEN + "AP: " + ChatColor.WHITE + points).setScore(score--);
        objective.getScore(ChatColor.BLACK.toString()).setScore(score--);
        objective.getScore(ChatColor.AQUA + "SELECT: 1 AP").setScore(score--);
        objective.getScore(ChatColor.GOLD + "INSERT: 2 AP").setScore(score--);
        objective.getScore(ChatColor.BLUE + "UPDATE: 2 AP").setScore(score--);
        objective.getScore(ChatColor.RED + "DELETE: 3 AP").setScore(score--);
        objective.getScore(ChatColor.DARK_BLUE.toString()).setScore(score--);
        objective.getScore(ChatColor.GRAY + "help | costos").setScore(score--);
        objective.getScore(ChatColor.DARK_GRAY + "estado | salir").setScore(score--);
    }

    private void showQueryResultPreview(Player player, BattleExecutionResult result) {
        List<Map<String, Object>> rows = result.getRows();
        if (rows.isEmpty()) {
            return;
        }

        int previewCount = Math.min(MAX_PREVIEW_ROWS, rows.size());
        player.sendMessage(ChatColor.GOLD + "=== Resultado SQL (preview " + previewCount + "/" + rows.size() + ") ===");

        for (int i = 0; i < previewCount; i++) {
            Map<String, Object> row = rows.get(i);
            StringBuilder line = new StringBuilder();
            boolean first = true;

            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (!first) {
                    line.append(ChatColor.DARK_GRAY).append(" | ");
                }
                line.append(ChatColor.AQUA).append(entry.getKey())
                    .append(ChatColor.WHITE).append("=")
                    .append(ChatColor.GRAY).append(String.valueOf(entry.getValue()));
                first = false;
            }

            player.sendMessage(ChatColor.WHITE + "#" + (i + 1) + " " + line);
        }

        if (rows.size() > previewCount) {
            player.sendMessage(ChatColor.GRAY + "... y " + (rows.size() - previewCount) + " filas mas.");
        }
    }

    private void deliverQueryResultBook(Player player, String query, BattleExecutionResult result) {
        List<Map<String, Object>> rows = result.getRows();
        if (rows.isEmpty()) {
            return;
        }

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta == null) {
            return;
        }

        meta.setTitle("Resultado SQL Battle");
        meta.setAuthor("SQL Battle");

        List<String> pages = new ArrayList<>();
        pages.add(buildSummaryPage(query, result));

        int maxRows = Math.min(MAX_BOOK_ROWS, rows.size());
        for (int i = 0; i < maxRows; i++) {
            Map<String, Object> row = rows.get(i);
            StringBuilder page = new StringBuilder();
            page.append("Fila #").append(i + 1).append("\n\n");

            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String value = String.valueOf(entry.getValue());
                if (value.length() > 90) {
                    value = value.substring(0, 90) + "...";
                }
                page.append(entry.getKey()).append(": ").append(value).append("\n");
            }

            pages.add(page.toString());
        }

        if (rows.size() > maxRows) {
            pages.add("Resultados truncados\n\nMostrando " + maxRows + " de " + rows.size() + " filas.");
        }

        meta.setPages(pages);
        book.setItemMeta(meta);
        player.getInventory().addItem(book);
        player.sendMessage(ChatColor.GRAY + "Se agrego un libro con el resultado completo a tu inventario.");
    }

    private String buildSummaryPage(String query, BattleExecutionResult result) {
        String compactQuery = query.replace("\n", " ").trim();
        if (compactQuery.length() > 220) {
            compactQuery = compactQuery.substring(0, 220) + "...";
        }

        return "SQL Battle Result\n\n"
            + "Tipo: " + result.getQueryType() + "\n"
            + "Filas: " + result.getRowsAffected() + "\n"
            + "JOIN: " + (result.isUsedJoin() ? ("Si (+" + result.getJoinBonusPercent() + "%)") : "No") + "\n\n"
            + "Query:\n" + compactQuery;
    }

    private int getDisplayedStage(Player player, BattlePlayerSession session) throws Exception {
        int forcedStage = getForcedStage(player);
        if (forcedStage > 0) {
            return forcedStage;
        }
        return session.database.getCurrentStage();
    }

    private boolean hasAnyAffordableQuery(int actionPoints) {
        return actionPoints >= MIN_ACTION_POINT_COST;
    }

    private void beginWavePhase(Player player, BattlePlayerSession session, String reason) {
        if (session.phase != BattleSessionPhase.PREPARATION) {
            return;
        }

        session.phase = BattleSessionPhase.WAVE_ACTIVE;
        session.lastPreparationEndReason = reason;
        session.lastKnownStage = FIRST_WAVE_STAGE;
        try {
            session.lastKnownWave = session.database.getCurrentWaveNumber();
        } catch (Exception e) {
            session.lastKnownWave = DEFAULT_WAVE_NUMBER;
        }

        try {
            session.database.setCurrentStage(FIRST_WAVE_STAGE);
        } catch (Exception e) {
            logger.warning("Could not set SQL Battle stage for player '" + player.getName() + "': " + e.getMessage());
        }

        if (session.waveStartLocation != null) {
            player.teleport(session.waveStartLocation);
        }
        if (session.checkpointLocation != null) {
            player.setBedSpawnLocation(session.checkpointLocation, true);
        }

        setWaveActive(session.worldName, true);
        spawnWaveEntities(player, session);
        giveInventoryItemsToPlayer(player, session, FIRST_WAVE_STAGE);
        updatePreparationSidebar(player);

        player.sendMessage(ChatColor.AQUA + reason);
        showWaveIntro(player, session);
    }

    private void spawnWaveEntities(Player player, BattlePlayerSession session) {
        session.enemyEntityIds.clear();
        session.summonedEntityIds.clear();

        spawnStageEnemies(player, session, session.lastKnownStage);
        spawnPreparedSummons(player, session, session.lastKnownStage);
    }

    private void spawnStageEnemies(Player player, BattlePlayerSession session, int stage) {
        try {
            List<BattleSQLDatabase.BattleEnemyRow> enemies = session.database.getEnemiesForStage(stage);
            for (BattleSQLDatabase.BattleEnemyRow enemyRow : enemies) {
                Location spawnLocation = getRandomRegionLocation(session.enemySpawnPos1, session.enemySpawnPos2);
                if (spawnLocation == null) {
                    continue;
                }

                LivingEntity spawned = spawnEnemyEntity(player, session, enemyRow, spawnLocation);
                if (spawned != null) {
                    session.enemyEntityIds.add(spawned.getUniqueId());
                }
            }
        } catch (Exception e) {
            logger.warning("Could not spawn SQL Battle enemies for player '" + player.getName() + "': " + e.getMessage());
        }
    }

    private void spawnPreparedSummons(Player player, BattlePlayerSession session, int stage) {
        if (session.summonZonePos1 == null || session.summonZonePos2 == null) {
            return;
        }

        try {
            int golemCount = Math.min(MAX_SUMMONED_GOLEMS, session.database.getPreparedSummonQuantityForStage(GOLEM_SUMMON_ITEM_ID, stage));
            for (int index = 0; index < golemCount; index++) {
                Location summonLocation = getRandomRegionLocation(session.summonZonePos1, session.summonZonePos2);
                if (summonLocation == null) {
                    continue;
                }

                IronGolem golem = (IronGolem) summonLocation.getWorld().spawnEntity(summonLocation, EntityType.IRON_GOLEM);
                golem.setPlayerCreated(true);
                golem.setCustomName("Golem SQL de " + player.getName());
                golem.setCustomNameVisible(true);
                golem.setRemoveWhenFarAway(false);
                tagBattleEntity(golem, session, player.getUniqueId(), "summon");
                applyConfiguredHealth(golem, 50.0D);
                session.summonedEntityIds.add(golem.getUniqueId());
            }
        } catch (Exception e) {
            logger.warning("Could not spawn SQL Battle summons for player '" + player.getName() + "': " + e.getMessage());
        }
    }

    private LivingEntity spawnEnemyEntity(Player player, BattlePlayerSession session,
            BattleSQLDatabase.BattleEnemyRow enemyRow, Location spawnLocation) {
        EntityType entityType = mapEnemyType(enemyRow.getTipoId());
        Entity spawnedEntity = spawnLocation.getWorld().spawnEntity(spawnLocation, entityType);
        if (!(spawnedEntity instanceof LivingEntity livingEntity)) {
            spawnedEntity.remove();
            return null;
        }

        livingEntity.setCustomName(getEnemyDisplayName(enemyRow.getTipoId()) + " [SQL]");
        livingEntity.setCustomNameVisible(true);
        livingEntity.setRemoveWhenFarAway(false);
        tagBattleEntity(livingEntity, session, player.getUniqueId(), "enemy");
        applyConfiguredHealth(livingEntity, enemyRow.getHp());
        configureEnemyBehavior(livingEntity, player);
        return livingEntity;
    }

    private void configureEnemyBehavior(LivingEntity entity, Player player) {
        if (entity instanceof Mob mob) {
            mob.setTarget(player);
        }

        if (entity instanceof Creeper creeper) {
            creeper.setExplosionRadius(2);
        } else if (entity instanceof Phantom phantom) {
            phantom.setSize(8);
        } else if (entity instanceof Ravager ravager) {
            ravager.setPatrolLeader(false);
        } else if (entity instanceof Zombie zombie) {
            zombie.setShouldBurnInDay(false);
        } else if (entity instanceof Spider spider) {
            spider.setCanPickupItems(false);
        } else if (entity instanceof Enderman) {
            // Enderman has no direct pickup toggle here; keep default behaviour for now.
        } else if (entity instanceof Witch witch) {
            witch.setCanPickupItems(false);
        }
    }

    private void applyConfiguredHealth(LivingEntity entity, double health) {
        AttributeInstance attribute = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attribute != null) {
            attribute.setBaseValue(Math.max(1.0D, health));
        }
        entity.setHealth(Math.max(1.0D, Math.min(health, entity.getMaxHealth())));
    }

    private EntityType mapEnemyType(int tipoId) {
        return switch (tipoId) {
            case 1 -> EntityType.ZOMBIE;
            case 2 -> EntityType.SKELETON;
            case 3 -> EntityType.SPIDER;
            case 4 -> EntityType.CREEPER;
            case 5 -> EntityType.ENDERMAN;
            case 6 -> EntityType.RAVAGER;
            case 7 -> EntityType.WITCH;
            case 8 -> EntityType.PHANTOM;
            default -> EntityType.ZOMBIE;
        };
    }

    private String getEnemyDisplayName(int tipoId) {
        return switch (tipoId) {
            case 1 -> "Zombi";
            case 2 -> "Esqueleto";
            case 3 -> "Araña";
            case 4 -> "Creeper";
            case 5 -> "Enderman";
            case 6 -> "Golem de Hierro";
            case 7 -> "Bruja";
            case 8 -> "Dragón";
            default -> "Enemigo";
        };
    }

    private void giveInventoryItemsToPlayer(Player player, BattlePlayerSession session, int stage) {
        try {
            List<BattleSQLDatabase.InventoryItemRow> items = session.database.getInventoryItemsForExactStage(stage);
            if (items.isEmpty()) {
                return;
            }
            for (BattleSQLDatabase.InventoryItemRow row : items) {
                Material mat = mapItemIdToMaterial(row.getItemId());
                if (mat == null) {
                    continue;
                }
                if (mat == Material.POTION) {
                    for (int i = 0; i < row.getCantidad(); i++) {
                        ItemStack potion = new ItemStack(Material.POTION);
                        org.bukkit.inventory.meta.PotionMeta meta = (org.bukkit.inventory.meta.PotionMeta) potion.getItemMeta();
                        if (meta != null) {
                            meta.setBasePotionData(new org.bukkit.potion.PotionData(org.bukkit.potion.PotionType.INSTANT_HEAL, false, false));
                            meta.setDisplayName(ChatColor.RED + "Pocion de Vida");
                            potion.setItemMeta(meta);
                        }
                        player.getInventory().addItem(potion);
                    }
                } else {
                    player.getInventory().addItem(new ItemStack(mat, row.getCantidad()));
                }
                player.sendMessage(ChatColor.GREEN + "+" + row.getCantidad() + "x " + row.getNombre() + " "
                    + ChatColor.GRAY + "(etapa " + stage + ")");
            }
        } catch (Exception e) {
            logger.warning("Could not give SQL Battle items to player '" + player.getName() + "': " + e.getMessage());
        }
    }

    private Material mapItemIdToMaterial(int itemId) {
        return switch (itemId) {
            case 1  -> Material.DIAMOND_SWORD;
            case 2  -> Material.IRON_SWORD;
            case 3  -> Material.WOODEN_AXE;
            case 4  -> Material.BOW;
            case 5  -> Material.IRON_CHESTPLATE;
            case 6  -> Material.DIAMOND_CHESTPLATE;
            case 7  -> Material.FIRE_CHARGE;
            case 8  -> Material.SNOWBALL;
            case 9  -> Material.POTION;
            default -> null; // 10 = golem (spawned as entity), unknown items ignored
        };
    }

    public void handleBattleEntityDamage(LivingEntity entity, double finalDamage) {
        String role = entity.getPersistentDataContainer().get(sqlBattleRoleKey, PersistentDataType.STRING);
        if (!"enemy".equalsIgnoreCase(role)) {
            return;
        }

        String current = entity.getCustomName();
        if (current == null) {
            return;
        }

        // Strip any existing HP tag appended after " [SQL]"
        String marker = " [SQL]";
        int markerIdx = current.indexOf(marker);
        String base = markerIdx >= 0 ? current.substring(0, markerIdx + marker.length()) : current;

        double newHp = Math.max(0.0, entity.getHealth() - finalDamage);
        double maxHp = entity.getMaxHealth();
        entity.setCustomName(base + " " + ChatColor.RED + (int) Math.ceil(newHp) + "/" + (int) maxHp + "\u2764");
    }

    private void tagBattleEntity(LivingEntity entity, BattlePlayerSession session, UUID ownerId, String role) {
        entity.getPersistentDataContainer().set(sqlBattleOwnerKey, PersistentDataType.STRING, ownerId.toString());
        entity.getPersistentDataContainer().set(sqlBattleSessionKey, PersistentDataType.STRING, session.sessionId.toString());
        entity.getPersistentDataContainer().set(sqlBattleRoleKey, PersistentDataType.STRING, role);
    }

    private Location getRandomRegionLocation(Location pos1, Location pos2) {
        if (pos1 == null || pos2 == null || pos1.getWorld() == null || pos2.getWorld() == null) {
            return null;
        }

        World world = pos1.getWorld();
        double minX = Math.min(pos1.getX(), pos2.getX());
        double maxX = Math.max(pos1.getX(), pos2.getX());
        double minZ = Math.min(pos1.getZ(), pos2.getZ());
        double maxZ = Math.max(pos1.getZ(), pos2.getZ());
        int randomX = (int) Math.round(minX + Math.random() * (maxX - minX));
        int randomZ = (int) Math.round(minZ + Math.random() * (maxZ - minZ));
        int safeY = world.getHighestBlockYAt(randomX, randomZ) + 1;
        return new Location(world, randomX + 0.5D, safeY, randomZ + 0.5D);
    }

    private void removeTrackedEntities(Set<UUID> entityIds) {
        for (UUID entityId : entityIds) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
        entityIds.clear();
    }

    private int getTrackedLiveCount(Set<UUID> entityIds) {
        int count = 0;
        for (UUID entityId : entityIds) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity instanceof LivingEntity livingEntity && livingEntity.isValid() && !livingEntity.isDead()) {
                count++;
            }
        }
        return count;
    }

    public void handleBattleEntityDeath(LivingEntity entity) {
        String ownerRaw = entity.getPersistentDataContainer().get(sqlBattleOwnerKey, PersistentDataType.STRING);
        String sessionRaw = entity.getPersistentDataContainer().get(sqlBattleSessionKey, PersistentDataType.STRING);
        String role = entity.getPersistentDataContainer().get(sqlBattleRoleKey, PersistentDataType.STRING);
        if (ownerRaw == null || sessionRaw == null || role == null) {
            return;
        }

        UUID ownerId;
        UUID sessionId;
        try {
            ownerId = UUID.fromString(ownerRaw);
            sessionId = UUID.fromString(sessionRaw);
        } catch (IllegalArgumentException ex) {
            return;
        }

        BattlePlayerSession session = playerSessions.get(ownerId);
        if (session == null || !session.sessionId.equals(sessionId)) {
            return;
        }

        if ("summon".equalsIgnoreCase(role)) {
            session.summonedEntityIds.remove(entity.getUniqueId());
            return;
        }

        if (!"enemy".equalsIgnoreCase(role) || session.phase != BattleSessionPhase.WAVE_ACTIVE) {
            return;
        }

        session.enemyEntityIds.remove(entity.getUniqueId());

        if (getTrackedLiveCount(session.enemyEntityIds) > 0) {
            return;
        }

        Player player = Bukkit.getPlayer(ownerId);
        if (player == null) {
            return;
        }

        advanceStageOrCompleteWave(player, session);
    }

    private void advanceStageOrCompleteWave(Player player, BattlePlayerSession session) {
        int nextStage = findNextStageWithEnemies(session, session.lastKnownStage + 1);
        if (nextStage > 0) {
            session.lastKnownStage = nextStage;
            try {
                session.database.setCurrentStage(nextStage);
            } catch (Exception e) {
                logger.warning("Could not persist SQL Battle stage advancement for player '" + player.getName() + "': " + e.getMessage());
            }

            player.sendMessage(ChatColor.GOLD + "=== SQL Battle: Etapa " + nextStage + " ===");
            player.sendMessage(ChatColor.YELLOW + "Nuevos enemigos e invocaciones han sido desplegados.");
            spawnStageEnemies(player, session, nextStage);
            spawnPreparedSummons(player, session, nextStage);
            giveInventoryItemsToPlayer(player, session, nextStage);
            updatePreparationSidebar(player);
            return;
        }

        finishWaveForPlayer(player, session);
    }

    private int findNextStageWithEnemies(BattlePlayerSession session, int startStage) {
        for (int stage = Math.max(FIRST_WAVE_STAGE, startStage); stage <= 3; stage++) {
            try {
                if (!session.database.getEnemiesForStage(stage).isEmpty()) {
                    return stage;
                }
            } catch (Exception e) {
                return -1;
            }
        }
        return -1;
    }

    private void finishWaveForPlayer(Player player, BattlePlayerSession session) {
        session.phase = BattleSessionPhase.BETWEEN_WAVES;
        session.lastPreparationEndReason = "Oleada completada.";
        setWaveActive(session.worldName, false);

        removeTrackedEntities(session.enemyEntityIds);
        removeTrackedEntities(session.summonedEntityIds);

        if (session.checkpointLocation != null) {
            player.teleport(session.checkpointLocation);
            player.setBedSpawnLocation(session.checkpointLocation, true);
        }

        updatePreparationSidebar(player);
        player.sendMessage(ChatColor.GREEN + "¡Oleada completada!");
        player.sendMessage(ChatColor.GRAY + "El mundo volvió a modo descanso. Próximo paso: preparar la siguiente oleada.");
    }

    private enum BattleSessionPhase {
        PREPARATION("Preparacion"),
        WAVE_ACTIVE("Oleada activa"),
        BETWEEN_WAVES("Entre oleadas"),
        COMPLETED("Completada"),
        FAILED("Fallida");

        private final String displayName;

        BattleSessionPhase(String displayName) {
            this.displayName = displayName;
        }

        private String getDisplayName() {
            return displayName;
        }

        private String getSidebarLabel() {
            return switch (this) {
                case PREPARATION -> "Prep";
                case WAVE_ACTIVE -> "Oleada";
                case BETWEEN_WAVES -> "Pausa";
                case COMPLETED -> "Fin";
                case FAILED -> "Fallo";
            };
        }
    }

    private static class BattlePlayerSession {
        private final String worldName;
        private final BattleSQLDatabase database;
        private final BattleQueryExecutor executor;
        private final BattleQueryValidator validator;
        private final long createdAtMillis;
        private final UUID sessionId;
        private final Location worldEntryLocation;
        private final Location waveStartLocation;
        private final Location checkpointLocation;
        private final Location preparationLocation;
        private final Location summonZonePos1;
        private final Location summonZonePos2;
        private final Location enemySpawnPos1;
        private final Location enemySpawnPos2;
        private final Set<UUID> enemyEntityIds;
        private final Set<UUID> summonedEntityIds;
        private Scoreboard scoreboard;
        private BattleSessionPhase phase;
        private int lastKnownWave;
        private int lastKnownStage;
        private String lastPreparationEndReason;

        private BattlePlayerSession(SQLBattleWorld battleWorld, BattleSQLDatabase database) {
            this.worldName = battleWorld.getWorldName();
            this.database = database;
            this.executor = new BattleQueryExecutor();
            this.validator = new BattleQueryValidator();
            this.createdAtMillis = System.currentTimeMillis();
            this.sessionId = UUID.randomUUID();
            this.worldEntryLocation = cloneLocation(battleWorld.getWorldEntryLocation());
            this.waveStartLocation = cloneLocation(battleWorld.getWaveStartLocation());
            this.checkpointLocation = cloneLocation(battleWorld.getCheckpointLocation());
            this.preparationLocation = cloneLocation(battleWorld.getPreparationLocation());
            this.summonZonePos1 = cloneLocation(battleWorld.getSummonZonePos1());
            this.summonZonePos2 = cloneLocation(battleWorld.getSummonZonePos2());
            this.enemySpawnPos1 = cloneLocation(battleWorld.getEnemySpawnPos1());
            this.enemySpawnPos2 = cloneLocation(battleWorld.getEnemySpawnPos2());
            this.enemyEntityIds = new HashSet<>();
            this.summonedEntityIds = new HashSet<>();
            this.phase = BattleSessionPhase.PREPARATION;
            this.lastKnownWave = DEFAULT_WAVE_NUMBER;
            this.lastKnownStage = 0;
            this.lastPreparationEndReason = "";
        }

        private long getSessionAgeSeconds() {
            return Math.max(0L, (System.currentTimeMillis() - createdAtMillis) / 1000L);
        }

        private boolean hasExpandedZoneModel() {
            return worldEntryLocation != null
                && waveStartLocation != null
                && preparationLocation != null
                && summonZonePos1 != null
                && summonZonePos2 != null
                && enemySpawnPos1 != null
                && enemySpawnPos2 != null;
        }

        private static Location cloneLocation(Location location) {
            return location != null ? location.clone() : null;
        }
    }
}