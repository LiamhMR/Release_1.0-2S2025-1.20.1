package com.seminario.plugin.sql.battle;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
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

    private static final String DB_URL = "jdbc:h2:mem:sqlbattle;DB_CLOSE_DELAY=-1";
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "";

    private Connection connection;
    private final Logger logger;

    public BattleSQLDatabase(Logger logger) {
        this.logger = logger;
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
            connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            logger.info("[SQLBattle] Conectado a la base de datos H2 en memoria");

            createSchema();
            seedReferenceData();
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
            stmt.execute("DELETE FROM enemigos");
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

    // -------------------------------------------------------------------------
    // Schema
    // -------------------------------------------------------------------------

    private void createSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {

            // Reference: item catalog (read-only for players)
            stmt.execute(
                "CREATE TABLE tipos_item (" +
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
                "CREATE TABLE tipos_enemigo (" +
                "  id          INT PRIMARY KEY," +
                "  nombre      VARCHAR(50)  NOT NULL," +
                "  debilidad   VARCHAR(30)," +
                "  descripcion VARCHAR(200)" +
                ")"
            );

            // Game state: player (always 1 row, system-managed)
            stmt.execute(
                "CREATE TABLE jugador (" +
                "  id            INT PRIMARY KEY," +
                "  nombre        VARCHAR(50) NOT NULL," +
                "  hp            INT NOT NULL CHECK (hp >= 0)," +
                "  mana          INT NOT NULL CHECK (mana >= 0)," +
                "  puntos_accion INT NOT NULL CHECK (puntos_accion >= 0)," +
                "  oleada_actual INT DEFAULT 1 CHECK (oleada_actual > 0)," +
                "  etapa_actual  INT DEFAULT 0 CHECK (etapa_actual BETWEEN 0 AND 3)" +
                ")"
            );

            // Game state: pre-wave resource stockpile
            stmt.execute(
                "CREATE TABLE almacen (" +
                "  item_id  INT PRIMARY KEY," +
                "  cantidad INT NOT NULL CHECK (cantidad >= 0)," +
                "  FOREIGN KEY (item_id) REFERENCES tipos_item(id)" +
                ")"
            );

            // Game state: items committed for the current wave
            stmt.execute(
                "CREATE TABLE inventario (" +
                "  item_id         INT PRIMARY KEY," +
                "  cantidad        INT NOT NULL CHECK (cantidad >= 0)," +
                "  activo_en_etapa INT DEFAULT 1 CHECK (activo_en_etapa BETWEEN 1 AND 3)," +
                "  FOREIGN KEY (item_id) REFERENCES tipos_item(id)" +
                ")"
            );

            // Game state: enemies in the current wave
            stmt.execute(
                "CREATE TABLE enemigos (" +
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

    // -------------------------------------------------------------------------
    // Reference data (static catalog, not reset between games)
    // -------------------------------------------------------------------------

    private void seedReferenceData() throws SQLException {
        try (Statement stmt = connection.createStatement()) {

            // 10 item types across all categories and stages
            stmt.execute(
                "INSERT INTO tipos_item (id, nombre, categoria, costo_mana, etapa_activacion) VALUES" +
                " (1,  'Espada de Diamante',  'arma',        0,  1)," +
                " (2,  'Espada de Hierro',    'arma',        0,  1)," +
                " (3,  'Hacha de Madera',     'arma',        0,  1)," +
                " (4,  'Arco Elfico',         'arma',        0,  2)," +
                " (5,  'Armadura de Hierro',  'armadura',    0,  1)," +
                " (6,  'Armadura de Diamante','armadura',    0,  2)," +
                " (7,  'Hechizo de Fuego',    'hechizo',    30,  2)," +
                " (8,  'Hechizo de Hielo',    'hechizo',   25,  1)," +
                " (9,  'Pocion de Vida',      'consumible',  0,  1)," +
                " (10, 'Invocacion de Golem', 'invocacion', 40,  3)"
            );

            // 8 enemy types with distinct weaknesses
            stmt.execute(
                "INSERT INTO tipos_enemigo (id, nombre, debilidad, descripcion) VALUES" +
                " (1, 'Zombi',          'luz',       'Muerto viviente lento pero resistente')," +
                " (2, 'Esqueleto',      'espada',    'Arquero preciso, fragil en cuerpo a cuerpo')," +
                " (3, 'Arana',          'fuego',     'Trepa paredes, veneno en cada ataque')," +
                " (4, 'Creeper',        'hielo',     'Explosivo, se congela con hielo')," +
                " (5, 'Enderman',       'agua',      'Teletransporte, evasivo y veloz')," +
                " (6, 'Golem de Hierro','hacha',     'Inmune a flechas, vulnerable al hacha de madera')," +
                " (7, 'Bruja',          'fuego',     'Lanza pociones, debil al fuego directo')," +
                " (8, 'Dragon',         'hielo',     'Jefe final. Solo aparece en oleadas avanzadas')"
            );
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

            // Starting stockpile: 7 item types available to the player
            stmt.execute(
                "INSERT INTO almacen (item_id, cantidad) VALUES" +
                " (1, 1)," +   // Espada de Diamante x1
                " (2, 2)," +   // Espada de Hierro   x2
                " (3, 2)," +   // Hacha de Madera    x2
                " (5, 2)," +   // Armadura de Hierro x2
                " (8, 3)," +   // Hechizo de Hielo   x3
                " (9, 5)," +   // Pocion de Vida     x5
                " (10, 1)"     // Invocacion de Golem x1
            );

            // Enemies for wave 1 (loaded from the bank)
            spawnEnemies(BattleWaveBank.getByWaveNumber(1).getEnemies());
        }
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
}
