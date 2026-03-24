package com.seminario.plugin.manager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

import com.seminario.plugin.config.ConfigManager;
import com.seminario.plugin.model.SQLBattleWorld;

/**
 * Manages SQL Battle world configuration and scenario setup.
 */
public class SQLBattleManager {

    private static final Logger logger = Logger.getLogger(SQLBattleManager.class.getName());
    private static final long BETWEEN_WAVES_TIME = 1000L; // daytime
    private static final long ACTIVE_WAVE_TIME = 12500L;  // dusk/night edge: no undead burning, still visible

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final Map<UUID, Integer> playerForcedStage;
    private final Map<String, Boolean> worldWaveActive;

    public SQLBattleManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.playerForcedStage = new HashMap<>();
        this.worldWaveActive = new HashMap<>();
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
        SQLBattleWorld battleWorld = configManager.getSQLBattle(worldName);
        if (battleWorld == null) {
            return false;
        }

        battleWorld.setStartLocation(location);
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

        setWaveActive(worldName, true); // antes de iniciar: NORMAL
        player.teleport(battleWorld.getStartLocation());
        player.setBedSpawnLocation(battleWorld.getCheckpointLocation(), true);
        return true;
    }

    /**
     * Starts SQL Battle test flow for all players currently in the configured world.
     */
    public int startForWorld(World world) {
        SQLBattleWorld battleWorld = configManager.getSQLBattle(world.getName());
        if (battleWorld == null || !battleWorld.isConfigured()) {
            return -1;
        }

        setWaveActive(world.getName(), true); // antes de iniciar: NORMAL
        int count = 0;
        for (Player player : world.getPlayers()) {
            player.teleport(battleWorld.getStartLocation());
            player.setBedSpawnLocation(battleWorld.getCheckpointLocation(), true);
            count++;
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
}