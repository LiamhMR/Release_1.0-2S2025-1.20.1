package com.seminario.plugin.manager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
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
    private static final int DEFAULT_WAVE_NUMBER = 1;
    private static final int MAX_PREVIEW_ROWS = 5;
    private static final int MAX_BOOK_ROWS = 30;

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final Map<UUID, Integer> playerForcedStage;
    private final Map<String, Boolean> worldWaveActive;
    private final Map<UUID, BattlePlayerSession> playerSessions;

    public SQLBattleManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
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
        return getActiveSession(player) != null && isPlayerInPreparationZone(player);
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

        BattlePlayerSession session = new BattlePlayerSession(battleWorld.getWorldName(), database);
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
        if (battleWorld == null || !battleWorld.hasPreparationLocation()) {
            return false;
        }

        Location prep = battleWorld.getPreparationLocation();
        Location current = player.getLocation();
        if (prep.getWorld() == null || current.getWorld() == null) {
            return false;
        }
        if (!prep.getWorld().equals(current.getWorld())) {
            return false;
        }

        return prep.distance(current) <= PREPARATION_RADIUS;
    }

    private void processBattleQuery(Player player, BattlePlayerSession session, String query) {
        try {
            BattleValidationResult validation = session.validator.validate(query);
            if (!validation.isAllowed()) {
                player.sendMessage(ChatColor.RED + validation.getReason());
                player.sendMessage(ChatColor.GRAY + "No se descontaron puntos de acción.");
                updatePreparationSidebar(player);
                return;
            }

            int currentPoints = session.database.getPlayerActionPoints();
            int cost = validation.getActionPointCost();
            if (cost > currentPoints) {
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
            player.sendMessage(ChatColor.WHITE + "Oleada: " + ChatColor.AQUA + wave);
            player.sendMessage(ChatColor.WHITE + "Etapa: " + ChatColor.AQUA + stage);
            player.sendMessage(ChatColor.WHITE + "Puntos de acción: " + ChatColor.GREEN + points);
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
        } catch (Exception e) {
            logger.warning("Could not update SQL Battle sidebar for '" + player.getName() + "': " + e.getMessage());
        }

        int score = 11;
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

    private static class BattlePlayerSession {
        private final String worldName;
        private final BattleSQLDatabase database;
        private final BattleQueryExecutor executor;
        private final BattleQueryValidator validator;
        private Scoreboard scoreboard;

        private BattlePlayerSession(String worldName, BattleSQLDatabase database) {
            this.worldName = worldName;
            this.database = database;
            this.executor = new BattleQueryExecutor();
            this.validator = new BattleQueryValidator();
        }
    }
}