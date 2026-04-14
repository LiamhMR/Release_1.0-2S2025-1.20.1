package com.seminario.plugin.sql.battle;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.seminario.plugin.sql.battle.wave.AlmacenGrant;
import com.seminario.plugin.sql.battle.wave.BattleWaveBank;
import com.seminario.plugin.sql.battle.wave.BattleWaveDefinition;
import com.seminario.plugin.sql.battle.wave.EnemySpawn;

/**
 * H2 in-memory database for SQL BATTLE game mode.
 *
 * Schema: 6 tables designed to force INNER JOIN usage.
 *
 * Tables:
 *   jugador       – player state (hp, mana, puntos_accion, oleada_actual, etapa_actual)
 *   tipos_item    – item catalog: name, category, mana cost, activation stage (read-only for players)
 *   almacen       – pre-wave stockpile, FK → tipos_item
 *   inventario    – items committed for current wave, FK → tipos_item
 *   tipos_enemigo – enemy type definitions: name, weakness, description (read-only for players)
 *   enemigos      – wave enemies: tipo_id FK → tipos_enemigo, hp, estado, etapa_aparicion
 *
 * Key JOINs (forced by design):
 *   almacen   INNER JOIN tipos_item    ON almacen.item_id   = tipos_item.id
 *   inventario INNER JOIN tipos_item   ON inventario.item_id = tipos_item.id
 *   enemigos  INNER JOIN tipos_enemigo ON enemigos.tipo_id   = tipos_enemigo.id
 */
public class BattleSQLDatabase {

    private static final String DB_URL_PREFIX = "jdbc:h2:mem:sqlbattle_arena_";
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "";

    private Connection connection;
    private final Logger logger;
    private final String dbUrl;

    public BattleSQLDatabase(Logger logger, String arenaKey) {
        this.logger = logger;
        this.dbUrl = DB_URL_PREFIX + sanitizeArenaKey(arenaKey) + ";DB_CLOSE_DELAY=-1";
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Creates the schema, seeds reference data, and loads wave 1.
     * @return true if initialization succeeded
     */
    public boolean initialize() {
        try {
            Class.forName("org.h2.Driver");
            connection = DriverManager.getConnection(dbUrl, DB_USER, DB_PASSWORD);
            logger.info("[SQLBattle] Conectado a la base de datos H2 en memoria");

            createSharedSchema();
            seedReferenceData();
            createPrivateSchema();
            seedInitialGameState();

            logger.info("[SQLBattle] Base de datos inicializada correctamente");
            return true;

        } catch (ClassNotFoundException e) {
            logger.severe("[SQLBattle] Driver H2 no encontrado: " + e.getMessage());
            return false;
        } catch (SQLException e) {
            logger.severe("[SQLBattle] Error al inicializar la base de datos: " + e.getMessage());
            return false;
        }
    }

    /**
     * Resets mutable game state for a fresh game.
     * Reference tables (tipos_item, tipos_enemigo) and the schema are preserved.
     */
    public void resetForNewGame() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DELETE FROM inventario");
            stmt.execute("DELETE FROM almacen");
            stmt.execute("DELETE FROM jugador");
        }
        seedInitialGameState();
        logger.info("[SQLBattle] Estado de juego reiniciado");
    }

    /**
     * Transitions to the given sequential wave number using the BattleWaveBank.
     * Clears enemies and inventory, applies almacen grants, and spawns new enemies.
     * Wave numbers wrap cyclically when they exceed the total defined waves.
     */
    public void loadWave(int waveNumber) throws SQLException {
        BattleWaveDefinition def = BattleWaveBank.getByWaveNumber(waveNumber);
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DELETE FROM enemigos");
            stmt.execute("DELETE FROM inventario");
            stmt.execute("UPDATE jugador SET oleada_actual = " + waveNumber
                    + ", etapa_actual = 0, puntos_accion = 5 WHERE id = 1");
        }
        applyAlmacenGrants(def.getAlmacenGrants());
        spawnEnemies(def.getEnemies());
        logger.info("[SQLBattle] Oleada " + waveNumber + " preparada: " + def.getName());
    }

    /**
     * Transitions to an explicit wave definition (e.g. a randomly chosen variant).
     */
    public void loadWaveDefinition(BattleWaveDefinition def) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DELETE FROM enemigos");
            stmt.execute("DELETE FROM inventario");
            stmt.execute("UPDATE jugador SET oleada_actual = " + def.getWaveId()
                    + ", etapa_actual = 0, puntos_accion = 5 WHERE id = 1");
        }
        applyAlmacenGrants(def.getAlmacenGrants());
        spawnEnemies(def.getEnemies());
        logger.info("[SQLBattle] Oleada cargada: " + def.getName());
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
                logger.info("[SQLBattle] Conexion cerrada");
            } catch (SQLException e) {
                logger.warning("[SQLBattle] Error al cerrar la conexion: " + e.getMessage());
            }
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    public int getPlayerActionPoints() throws SQLException {
        return getPlayerIntField("puntos_accion", 5);
    }

    public void setPlayerActionPoints(int points) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE jugador SET puntos_accion = ? WHERE id = 1")) {
            ps.setInt(1, Math.max(0, points));
            ps.executeUpdate();
        }
    }

    public int getCurrentWaveNumber() throws SQLException {
        return getPlayerIntField("oleada_actual", 1);
    }

    public int getCurrentStage() throws SQLException {
        return getPlayerIntField("etapa_actual", 0);
    }

    public void setCurrentStage(int stage) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE jugador SET etapa_actual = ? WHERE id = 1")) {
            ps.setInt(1, Math.max(0, Math.min(stage, 3)));
            ps.executeUpdate();
        }
    }

    public List<BattleEnemyRow> getEnemiesForStage(int stage) throws SQLException {
        List<BattleEnemyRow> enemies = new ArrayList<>();
        String sql = "SELECT id, tipo_id, hp, etapa_aparicion FROM enemigos WHERE estado = 'vivo' AND etapa_aparicion = ? ORDER BY id ASC";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, stage);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    enemies.add(new BattleEnemyRow(
                        rs.getInt("id"),
                        rs.getInt("tipo_id"),
                        rs.getInt("hp"),
                        rs.getInt("etapa_aparicion")
                    ));
                }
            }
        }
        return enemies;
    }

    public int getPreparedSummonQuantity(int itemId, int maxActiveStage) throws SQLException {
        String sql = "SELECT COALESCE(SUM(i.cantidad), 0) AS total "
            + "FROM inventario i INNER JOIN tipos_item t ON i.item_id = t.id "
            + "WHERE i.item_id = ? AND t.categoria = 'invocacion' AND i.activo_en_etapa <= ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, itemId);
            ps.setInt(2, maxActiveStage);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Math.max(0, rs.getInt("total"));
                }
            }
        }
        return 0;
    }

    public int getPreparedSummonQuantityForStage(int itemId, int exactStage) throws SQLException {
        String sql = "SELECT COALESCE(SUM(i.cantidad), 0) AS total "
            + "FROM inventario i INNER JOIN tipos_item t ON i.item_id = t.id "
            + "WHERE i.item_id = ? AND t.categoria = 'invocacion' AND i.activo_en_etapa = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, itemId);
            ps.setInt(2, exactStage);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Math.max(0, rs.getInt("total"));
                }
            }
        }
        return 0;
    }

    /**
     * Captures current quantities in inventario by item_id.
     * Intended to be used before/after a modifying query in the same transaction.
     */
    public Map<Integer, Integer> snapshotInventarioQuantities() throws SQLException {
        Map<Integer, Integer> snapshot = new HashMap<>();
        String sql = "SELECT item_id, cantidad FROM inventario";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                snapshot.put(rs.getInt("item_id"), Math.max(0, rs.getInt("cantidad")));
            }
        }
        return snapshot;
    }

    /**
     * Applies deduction to almacen based on positive quantity deltas in inventario.
     * If almacen does not have enough stock for any increased item, throws SQLException.
     */
    public void consumeAlmacenForInventarioIncrease(Map<Integer, Integer> beforeSnapshot) throws SQLException {
        Map<Integer, Integer> afterSnapshot = snapshotInventarioQuantities();

        String updateSql = "UPDATE almacen SET cantidad = cantidad - ? WHERE item_id = ? AND cantidad >= ?";
        try (PreparedStatement ps = connection.prepareStatement(updateSql)) {
            for (Map.Entry<Integer, Integer> after : afterSnapshot.entrySet()) {
                int itemId = after.getKey();
                int before = beforeSnapshot.getOrDefault(itemId, 0);
                int delta = after.getValue() - before;
                if (delta <= 0) {
                    continue;
                }

                ps.setInt(1, delta);
                ps.setInt(2, itemId);
                ps.setInt(3, delta);
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    throw new SQLException("Stock insuficiente en almacen para item_id=" + itemId + " (faltan " + delta + ")");
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Schema
    // -------------------------------------------------------------------------

    private void createSharedSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {

            // Reference: item catalog (read-only for players)
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS tipos_item (" +
                "  id               INT PRIMARY KEY," +
                "  nombre           VARCHAR(50)  NOT NULL," +
                "  categoria        VARCHAR(20)  NOT NULL" +
                "    CHECK (categoria IN ('arma','hechizo','invocacion','armadura','consumible'))," +
                "  costo_mana       INT DEFAULT 0 CHECK (costo_mana >= 0)," +
                "  etapa_activacion INT DEFAULT 1 CHECK (etapa_activacion BETWEEN 1 AND 3)" +
                ")"
            );

            // Reference: enemy type catalog (read-only for players)
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS tipos_enemigo (" +
                "  id          INT PRIMARY KEY," +
                "  nombre      VARCHAR(50)  NOT NULL," +
                "  debilidad   VARCHAR(30)," +
                "  descripcion VARCHAR(200)" +
                ")"
            );

            // Shared wave state: enemies in the current wave (arena-wide)
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS enemigos (" +
                "  id              INT PRIMARY KEY," +
                "  tipo_id         INT NOT NULL," +
                "  hp              INT NOT NULL CHECK (hp > 0)," +
                "  hp_max          INT NOT NULL CHECK (hp_max > 0)," +
                "  estado          VARCHAR(20) DEFAULT 'vivo'" +
                "    CHECK (estado IN ('vivo','derrotado','aturdido'))," +
                "  etapa_aparicion INT DEFAULT 1 CHECK (etapa_aparicion BETWEEN 1 AND 3)," +
                "  FOREIGN KEY (tipo_id) REFERENCES tipos_enemigo(id)" +
                ")"
            );
        }
    }

    private void createPrivateSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Per-player connection-local state
            stmt.execute(
                "CREATE LOCAL TEMPORARY TABLE IF NOT EXISTS jugador (" +
                "  id            INT PRIMARY KEY," +
                "  nombre        VARCHAR(50) NOT NULL," +
                "  hp            INT NOT NULL CHECK (hp >= 0)," +
                "  mana          INT NOT NULL CHECK (mana >= 0)," +
                "  puntos_accion INT NOT NULL CHECK (puntos_accion >= 0)," +
                "  oleada_actual INT DEFAULT 1 CHECK (oleada_actual > 0)," +
                "  etapa_actual  INT DEFAULT 0 CHECK (etapa_actual BETWEEN 0 AND 3)" +
                ")"
            );

            stmt.execute(
                "CREATE LOCAL TEMPORARY TABLE IF NOT EXISTS almacen (" +
                "  item_id  INT PRIMARY KEY," +
                "  cantidad INT NOT NULL CHECK (cantidad >= 0)" +
                ")"
            );

            stmt.execute(
                "CREATE LOCAL TEMPORARY TABLE IF NOT EXISTS inventario (" +
                "  item_id         INT PRIMARY KEY," +
                "  cantidad        INT NOT NULL CHECK (cantidad >= 0)," +
                "  activo_en_etapa INT DEFAULT 1 CHECK (activo_en_etapa BETWEEN 1 AND 3)" +
                ")"
            );
        }
    }

    // -------------------------------------------------------------------------
    // Reference data (static catalog, not reset between games)
    // -------------------------------------------------------------------------

    private void seedReferenceData() throws SQLException {
        try (Statement stmt = connection.createStatement()) {

            // 13 item types across all categories and stages
            stmt.execute("MERGE INTO tipos_item (id, nombre, categoria, costo_mana, etapa_activacion) KEY(id) VALUES (1,  'Espada de Diamante',  'arma',        0,  1)");
            stmt.execute("MERGE INTO tipos_item (id, nombre, categoria, costo_mana, etapa_activacion) KEY(id) VALUES (2,  'Espada de Hierro',    'arma',        0,  1)");
            stmt.execute("MERGE INTO tipos_item (id, nombre, categoria, costo_mana, etapa_activacion) KEY(id) VALUES (3,  'Hacha de Madera',     'arma',        0,  1)");
            stmt.execute("MERGE INTO tipos_item (id, nombre, categoria, costo_mana, etapa_activacion) KEY(id) VALUES (4,  'Arco Elfico',         'arma',        0,  2)");
            stmt.execute("MERGE INTO tipos_item (id, nombre, categoria, costo_mana, etapa_activacion) KEY(id) VALUES (5,  'Armadura de Hierro',  'armadura',    0,  1)");
            stmt.execute("MERGE INTO tipos_item (id, nombre, categoria, costo_mana, etapa_activacion) KEY(id) VALUES (6,  'Armadura de Diamante','armadura',    0,  2)");
            stmt.execute("MERGE INTO tipos_item (id, nombre, categoria, costo_mana, etapa_activacion) KEY(id) VALUES (7,  'Hechizo de Fuego',    'hechizo',    30,  2)");
            stmt.execute("MERGE INTO tipos_item (id, nombre, categoria, costo_mana, etapa_activacion) KEY(id) VALUES (8,  'Hechizo de Hielo',    'hechizo',    25,  1)");
            stmt.execute("MERGE INTO tipos_item (id, nombre, categoria, costo_mana, etapa_activacion) KEY(id) VALUES (9,  'Pocion de Vida',      'consumible',  0,  1)");
            stmt.execute("MERGE INTO tipos_item (id, nombre, categoria, costo_mana, etapa_activacion) KEY(id) VALUES (10, 'Invocacion de Golem', 'invocacion', 40,  3)");
            stmt.execute("MERGE INTO tipos_item (id, nombre, categoria, costo_mana, etapa_activacion) KEY(id) VALUES (11, 'Flechas',             'consumible',  0,  1)");
            stmt.execute("MERGE INTO tipos_item (id, nombre, categoria, costo_mana, etapa_activacion) KEY(id) VALUES (12, 'Escudo',              'armadura',    0,  1)");
            stmt.execute("MERGE INTO tipos_item (id, nombre, categoria, costo_mana, etapa_activacion) KEY(id) VALUES (13, 'Filete Cocido',       'consumible',  0,  1)");

            // 8 enemy types with distinct weaknesses
            stmt.execute("MERGE INTO tipos_enemigo (id, nombre, debilidad, descripcion) KEY(id) VALUES (1, 'Zombi',          'luz',       'Muerto viviente lento pero resistente')");
            stmt.execute("MERGE INTO tipos_enemigo (id, nombre, debilidad, descripcion) KEY(id) VALUES (2, 'Esqueleto',      'espada',    'Arquero preciso, fragil en cuerpo a cuerpo')");
            stmt.execute("MERGE INTO tipos_enemigo (id, nombre, debilidad, descripcion) KEY(id) VALUES (3, 'Arana',          'fuego',     'Trepa paredes, veneno en cada ataque')");
            stmt.execute("MERGE INTO tipos_enemigo (id, nombre, debilidad, descripcion) KEY(id) VALUES (4, 'Creeper',        'hielo',     'Explosivo, se congela con hielo')");
            stmt.execute("MERGE INTO tipos_enemigo (id, nombre, debilidad, descripcion) KEY(id) VALUES (5, 'Enderman',       'agua',      'Teletransporte, evasivo y veloz')");
            stmt.execute("MERGE INTO tipos_enemigo (id, nombre, debilidad, descripcion) KEY(id) VALUES (6, 'Golem de Hierro','hacha',     'Inmune a flechas, vulnerable al hacha de madera')");
            stmt.execute("MERGE INTO tipos_enemigo (id, nombre, debilidad, descripcion) KEY(id) VALUES (7, 'Bruja',          'fuego',     'Lanza pociones, debil al fuego directo')");
            stmt.execute("MERGE INTO tipos_enemigo (id, nombre, debilidad, descripcion) KEY(id) VALUES (8, 'Dragon',         'hielo',     'Jefe final. Solo aparece en oleadas avanzadas')");
        }
    }

    // -------------------------------------------------------------------------
    // Game state seed
    // -------------------------------------------------------------------------

    private void seedInitialGameState() throws SQLException {
        try (Statement stmt = connection.createStatement()) {

            // Player starting values
            stmt.execute(
                "INSERT INTO jugador (id, nombre, hp, mana, puntos_accion, oleada_actual, etapa_actual)" +
                " VALUES (1, 'Aventurero', 100, 100, 5, 1, 0)"
            );

            // Starting stockpile: core combat items for early survival
            stmt.execute(
                "INSERT INTO almacen (item_id, cantidad) VALUES" +
                " (1, 1)," +   // Espada de Diamante x1
                " (2, 2)," +   // Espada de Hierro   x2
                " (3, 2)," +   // Hacha de Madera    x2
                " (4, 1)," +   // Arco Elfico        x1
                " (5, 2)," +   // Armadura de Hierro x2
                " (8, 3)," +   // Hechizo de Hielo   x3
                " (9, 5)," +   // Pocion de Vida     x5
                " (11, 32)," + // Flechas            x32
                " (12, 1)," +  // Escudo             x1
                " (13, 8)," +  // Filete Cocido      x8
                " (10, 1)"     // Invocacion de Golem x1
            );

        }
    }

    private static String sanitizeArenaKey(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "default";
        }
        String normalized = raw.toLowerCase().replaceAll("[^a-z0-9_]", "_");
        return normalized.isEmpty() ? "default" : normalized;
    }

    // -------------------------------------------------------------------------
    // Wave loading helpers
    // -------------------------------------------------------------------------

    /**
     * Inserts enemies from a wave definition, assigning sequential IDs starting at 1.
     */
    private void spawnEnemies(List<EnemySpawn> enemies) throws SQLException {
        String sql = "INSERT INTO enemigos (id, tipo_id, hp, hp_max, estado, etapa_aparicion) VALUES (?, ?, ?, ?, 'vivo', ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < enemies.size(); i++) {
                EnemySpawn e = enemies.get(i);
                ps.setInt(1, i + 1);
                ps.setInt(2, e.getTipoId());
                ps.setInt(3, e.getHp());
                ps.setInt(4, e.getHp());      // hp_max = hp inicial
                ps.setInt(5, e.getEtapaAparicion());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /**
     * Applies almacen grants for a wave:
     *   - If the item exists in almacen, its cantidad is incremented.
     *   - If the item does not exist yet, it is inserted.
     */
    private void applyAlmacenGrants(List<AlmacenGrant> grants) throws SQLException {
        if (grants == null || grants.isEmpty()) return;
        String updateSql = "UPDATE almacen SET cantidad = cantidad + ? WHERE item_id = ?";
        String insertSql = "INSERT INTO almacen (item_id, cantidad) VALUES (?, ?)";
        try (PreparedStatement upd = connection.prepareStatement(updateSql);
             PreparedStatement ins = connection.prepareStatement(insertSql)) {
            for (AlmacenGrant grant : grants) {
                upd.setInt(1, grant.getAddCantidad());
                upd.setInt(2, grant.getItemId());
                int rows = upd.executeUpdate();
                if (rows == 0) {
                    ins.setInt(1, grant.getItemId());
                    ins.setInt(2, grant.getAddCantidad());
                    ins.executeUpdate();
                }
            }
        }
    }

    private int getPlayerIntField(String columnName, int defaultValue) throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT " + columnName + " FROM jugador WHERE id = 1")) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return defaultValue;
    }

    public static class BattleEnemyRow {
        private final int enemyId;
        private final int tipoId;
        private final int hp;
        private final int stage;

        public BattleEnemyRow(int enemyId, int tipoId, int hp, int stage) {
            this.enemyId = enemyId;
            this.tipoId = tipoId;
            this.hp = hp;
            this.stage = stage;
        }

        public int getEnemyId() {
            return enemyId;
        }

        public int getTipoId() {
            return tipoId;
        }

        public int getHp() {
            return hp;
        }

        public int getStage() {
            return stage;
        }
    }

    public static class InventoryItemRow {
        private final int itemId;
        private final int cantidad;
        private final String nombre;
        private final String categoria;

        public InventoryItemRow(int itemId, int cantidad, String nombre, String categoria) {
            this.itemId = itemId;
            this.cantidad = cantidad;
            this.nombre = nombre;
            this.categoria = categoria;
        }

        public int getItemId() { return itemId; }
        public int getCantidad() { return cantidad; }
        public String getNombre() { return nombre; }
        public String getCategoria() { return categoria; }
    }

    /**
     * Returns items in inventario that activate at exactly the given stage,
     * excluding invocacion items (those are handled as spawned entities).
     */
    public List<InventoryItemRow> getInventoryItemsForExactStage(int stage) throws SQLException {
        List<InventoryItemRow> items = new ArrayList<>();
        String sql = "SELECT i.item_id, i.cantidad, t.nombre, t.categoria "
            + "FROM inventario i INNER JOIN tipos_item t ON i.item_id = t.id "
            + "WHERE i.activo_en_etapa = ? AND t.categoria != 'invocacion'";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, stage);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(new InventoryItemRow(
                        rs.getInt("item_id"),
                        rs.getInt("cantidad"),
                        rs.getString("nombre"),
                        rs.getString("categoria")
                    ));
                }
            }
        }
        return items;
    }
}
