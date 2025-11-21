package com.seminario.plugin.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Manages the H2 in-memory database for SQL validation in dungeons
 * Contains fictional Minecraft-themed data for educational purposes
 */
public class SQLDatabase {
    
    private static final String DB_URL = "jdbc:h2:mem:sqlgame;DB_CLOSE_DELAY=-1";
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "";
    
    private Connection connection;
    private final Logger logger;
    
    public SQLDatabase(JavaPlugin plugin) {
        this.logger = plugin.getLogger();
    }
    
    /**
     * Initialize the database connection and create tables with sample data
     * @return true if initialization was successful
     */
    public boolean initialize() {
        try {
            // Load H2 driver
            Class.forName("org.h2.Driver");
            
            // Create connection
            connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            
            logger.info("Conectado a la base de datos H2 en memoria para SQL Dungeon");
            
            // Create tables and populate with sample data
            createTables();
            populateData();
            
            // Verify data was inserted correctly
            if (verifyData()) {
                logger.info("Base de datos SQL Dungeon inicializada correctamente");
                return true;
            } else {
                logger.severe("Error: Los datos no se insertaron correctamente");
                return false;
            }
            
        } catch (ClassNotFoundException e) {
            logger.severe("Driver H2 no encontrado: " + e.getMessage());
            return false;
        } catch (SQLException e) {
            logger.severe("Error al inicializar la base de datos SQL: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Create all tables for the SQL game following database design best practices
     * Using _pk suffix for primary keys and _fk suffix for foreign keys
     */
    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            
            // Table: Jugadores (Players) - Main entity table
            stmt.execute("""
                CREATE TABLE Jugadores (
                    jugador_pk INT PRIMARY KEY,
                    nombre VARCHAR(50) NOT NULL UNIQUE,
                    nivel INT DEFAULT 1 CHECK (nivel > 0),
                    diamantes INT DEFAULT 0 CHECK (diamantes >= 0),
                    esmeraldas INT DEFAULT 0 CHECK (esmeraldas >= 0),
                    oro INT DEFAULT 0 CHECK (oro >= 0),
                    ubicacion_x INT DEFAULT 0,
                    ubicacion_z INT DEFAULT 0,
                    mundo VARCHAR(30) DEFAULT 'overworld',
                    fecha_registro DATE DEFAULT CURRENT_DATE,
                    ultimo_login TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
            
            // Table: Inventarios (Inventories) - Player items
            stmt.execute("""
                CREATE TABLE Inventarios (
                    inventario_pk INT PRIMARY KEY AUTO_INCREMENT,
                    jugador_fk INT NOT NULL,
                    item VARCHAR(50) NOT NULL,
                    cantidad INT DEFAULT 1 CHECK (cantidad > 0),
                    rareza VARCHAR(20) DEFAULT 'común' CHECK (rareza IN ('común', 'raro', 'épico', 'legendario')),
                    encantado BOOLEAN DEFAULT FALSE,
                    fecha_obtenido TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (jugador_fk) REFERENCES Jugadores(jugador_pk) ON DELETE CASCADE
                )
            """);
            
            // Note: Índices deshabilitados temporalmente por compatibilidad con H2
            // stmt.execute("CREATE INDEX idx_jugador_item ON Inventarios (jugador_fk, item)");
            
            // Table: Construcciones (Buildings) - Player constructions  
            stmt.execute("""
                CREATE TABLE Construcciones (
                    construccion_pk INT PRIMARY KEY AUTO_INCREMENT,
                    jugador_fk INT NOT NULL,
                    nombre VARCHAR(100) NOT NULL,
                    tipo VARCHAR(30) NOT NULL CHECK (tipo IN ('casa', 'castillo', 'granja', 'mina', 'torre', 'puente', 'otro')),
                    tamaño INT DEFAULT 1 CHECK (tamaño > 0),
                    bloques_usados INT DEFAULT 0 CHECK (bloques_usados >= 0),
                    fecha_creacion DATE DEFAULT CURRENT_DATE,
                    mundo VARCHAR(30) DEFAULT 'overworld',
                    coordenada_x INT DEFAULT 0,
                    coordenada_y INT DEFAULT 64,
                    coordenada_z INT DEFAULT 0,
                    activa BOOLEAN DEFAULT TRUE,
                    FOREIGN KEY (jugador_fk) REFERENCES Jugadores(jugador_pk) ON DELETE CASCADE
                )
            """);
            
            // Note: Índices deshabilitados temporalmente por compatibilidad con H2
            // stmt.execute("CREATE INDEX idx_jugador_construccion ON Construcciones (jugador_fk, tipo)");
            // stmt.execute("CREATE INDEX idx_ubicacion ON Construcciones (mundo, coordenada_x, coordenada_z)");
            
            // Table: Logros (Achievements) - Player achievements
            stmt.execute("""
                CREATE TABLE Logros (
                    logro_pk INT PRIMARY KEY AUTO_INCREMENT,
                    jugador_fk INT NOT NULL,
                    nombre_logro VARCHAR(100) NOT NULL,
                    descripcion TEXT,
                    puntos INT DEFAULT 10 CHECK (puntos > 0),
                    fecha_obtenido TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    categoria VARCHAR(30) DEFAULT 'general' CHECK (categoria IN ('general', 'construcción', 'combate', 'exploración', 'comercio')),
                    FOREIGN KEY (jugador_fk) REFERENCES Jugadores(jugador_pk) ON DELETE CASCADE
                )
            """);
            
            // Create unique constraint and indexes for Logros
            stmt.execute("CREATE UNIQUE INDEX uk_jugador_logro ON Logros (jugador_fk, nombre_logro)");
            // Note: Índices deshabilitados temporalmente por compatibilidad con H2
            // stmt.execute("CREATE INDEX idx_categoria ON Logros (categoria)");
            // stmt.execute("CREATE INDEX idx_fecha ON Logros (fecha_obtenido)");
            
            // Table: Comercio (Trading) - Trade transactions
            stmt.execute("""
                CREATE TABLE Comercio (
                    comercio_pk INT PRIMARY KEY AUTO_INCREMENT,
                    vendedor_fk INT NOT NULL,
                    comprador_fk INT NOT NULL,
                    item VARCHAR(50) NOT NULL,
                    cantidad INT DEFAULT 1 CHECK (cantidad > 0),
                    precio_diamantes INT DEFAULT 0 CHECK (precio_diamantes >= 0),
                    precio_esmeraldas INT DEFAULT 0 CHECK (precio_esmeraldas >= 0),
                    fecha_intercambio TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    estado VARCHAR(20) DEFAULT 'completado' CHECK (estado IN ('completado', 'cancelado', 'pendiente')),
                    FOREIGN KEY (vendedor_fk) REFERENCES Jugadores(jugador_pk) ON DELETE CASCADE,
                    FOREIGN KEY (comprador_fk) REFERENCES Jugadores(jugador_pk) ON DELETE CASCADE,
                    CHECK (vendedor_fk != comprador_fk),
                    CHECK (precio_diamantes > 0 OR precio_esmeraldas > 0)
                )
            """);
            
            // Note: Índices deshabilitados temporalmente por compatibilidad con H2
            // stmt.execute("CREATE INDEX idx_vendedor ON Comercio (vendedor_fk)");
            // stmt.execute("CREATE INDEX idx_comprador ON Comercio (comprador_fk)");
            // stmt.execute("CREATE INDEX idx_fecha ON Comercio (fecha_intercambio)");
            // stmt.execute("CREATE INDEX idx_item ON Comercio (item)");
            
            logger.info("Tablas creadas exitosamente con las mejores prácticas de diseño de BD");
        }
    }
    
    /**
     * Populate tables with sample Minecraft-themed data using new column naming conventions
     */
    private void populateData() throws SQLException {
        logger.info("Insertando datos de ejemplo en la base de datos reestructurada...");
        
        // Insert sample players using new primary key naming
        try (PreparedStatement stmt = connection.prepareStatement(
            "INSERT INTO Jugadores (jugador_pk, nombre, nivel, diamantes, esmeraldas, oro, ubicacion_x, ubicacion_z, mundo) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            
            Object[][] players = {
                {1, "Steve", 25, 156, 23, 450, 100, 200, "overworld"},
                {2, "Alex", 18, 89, 67, 234, -50, 150, "overworld"},
                {3, "Notch", 50, 999, 500, 2000, 0, 0, "overworld"},
                {4, "Herobrine", 45, 666, 13, 1337, -100, -100, "nether"},
                {5, "CraftMaster", 32, 234, 145, 890, 250, -75, "overworld"},
                {6, "MineBuilder", 28, 178, 98, 567, 75, 300, "overworld"},
                {7, "RedstoneGuru", 35, 345, 234, 1200, -200, 400, "overworld"},
                {8, "EnderExplorer", 40, 445, 67, 890, 500, -300, "end"}
            };
            
            for (Object[] player : players) {
                for (int i = 0; i < player.length; i++) {
                    stmt.setObject(i + 1, player[i]);
                }
                stmt.addBatch();
            }
            int[] results = stmt.executeBatch();
            logger.info("Insertados " + results.length + " jugadores");
        }
        
        // Insert sample inventories using foreign key naming
        try (PreparedStatement stmt = connection.prepareStatement(
            "INSERT INTO Inventarios (jugador_fk, item, cantidad, rareza, encantado) VALUES (?, ?, ?, ?, ?)")) {
            
            Object[][] items = {
                {1, "espada_diamante", 1, "épico", true},
                {1, "pico_hierro", 2, "común", false},
                {1, "armadura_cuero", 1, "común", false},
                {2, "arco", 1, "raro", true},
                {2, "flechas", 64, "común", false},
                {2, "poción_curación", 5, "raro", false},
                {3, "bloque_diamante", 64, "legendario", false},
                {3, "elytra", 1, "legendario", true},
                {4, "espada_netherite", 1, "legendario", true},
                {4, "tridente", 1, "épico", true},
                {5, "bloques_construcción", 128, "común", false},
                {6, "herramientas_construcción", 10, "raro", false},
                {7, "redstone", 64, "común", false},
                {7, "repetidores", 32, "común", false},
                {8, "perlas_ender", 16, "raro", false},
                {1, "escudo", 1, "raro", true},
                {3, "beacon", 2, "legendario", false},
                {4, "totem_inmortalidad", 3, "épico", false}
            };
            
            for (Object[] item : items) {
                for (int i = 0; i < item.length; i++) {
                    stmt.setObject(i + 1, item[i]);
                }
                stmt.addBatch();
            }
            int[] results = stmt.executeBatch();
            logger.info("Insertados " + results.length + " items de inventario");
        }
        
        // Insert sample buildings using improved naming
        try (PreparedStatement stmt = connection.prepareStatement(
            "INSERT INTO Construcciones (jugador_fk, nombre, tipo, tamaño, bloques_usados, mundo, coordenada_x, coordenada_y, coordenada_z) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            
            Object[][] buildings = {
                {1, "Casa de Steve", "casa", 100, 1500, "overworld", 100, 64, 200},
                {2, "Torre de Alex", "torre", 200, 3000, "overworld", -50, 64, 150},
                {3, "Palacio de Notch", "castillo", 1000, 50000, "overworld", 0, 64, 0},
                {4, "Fortaleza Oscura", "castillo", 800, 25000, "nether", -100, 64, -100},
                {5, "Villa Craftmaster", "casa", 300, 8000, "overworld", 250, 64, -75},
                {5, "Granero", "granja", 150, 2000, "overworld", 275, 64, -50},
                {6, "Catedral", "torre", 500, 15000, "overworld", 75, 64, 300},
                {7, "Laboratorio Redstone", "mina", 250, 5000, "overworld", -200, 64, 400},
                {8, "Castillo del End", "castillo", 600, 20000, "end", 500, 64, -300},
                {2, "Puente Colgante", "puente", 80, 1200, "overworld", -25, 70, 175}
            };
            
            for (Object[] building : buildings) {
                for (int i = 0; i < building.length; i++) {
                    stmt.setObject(i + 1, building[i]);
                }
                stmt.addBatch();
            }
            int[] results = stmt.executeBatch();
            logger.info("Insertadas " + results.length + " construcciones");
        }
        
        // Insert sample achievements using better categorization
        try (PreparedStatement stmt = connection.prepareStatement(
            "INSERT INTO Logros (jugador_fk, nombre_logro, descripcion, puntos, categoria) VALUES (?, ?, ?, ?, ?)")) {
            
            Object[][] achievements = {
                {1, "Primer Diamante", "Minó su primer diamante", 50, "exploración"},
                {1, "Constructor Novato", "Construyó su primera casa", 25, "construcción"},
                {2, "Arquero Experto", "Mató 100 mobs con arco", 75, "combate"},
                {3, "Maestro Constructor", "Construyó un palacio", 200, "construcción"},
                {4, "Señor del Nether", "Dominó el Nether", 150, "exploración"},
                {5, "Comerciante", "Realizó 50 intercambios", 100, "comercio"},
                {7, "Ingeniero Redstone", "Creó 10 máquinas de redstone", 125, "construcción"},
                {8, "Explorador del End", "Visitó el End", 175, "exploración"},
                {3, "Benefactor", "Donó recursos a otros jugadores", 300, "general"},
                {6, "Arquitecto", "Construyó una catedral", 250, "construcción"}
            };
            
            for (Object[] achievement : achievements) {
                for (int i = 0; i < achievement.length; i++) {
                    stmt.setObject(i + 1, achievement[i]);
                }
                stmt.addBatch();
            }
            int[] results = stmt.executeBatch();
            logger.info("Insertados " + results.length + " logros");
        }
        
        // Insert sample trades using better business logic
        try (PreparedStatement stmt = connection.prepareStatement(
            "INSERT INTO Comercio (vendedor_fk, comprador_fk, item, cantidad, precio_diamantes, precio_esmeraldas, estado) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            
            Object[][] trades = {
                {1, 2, "espada_hierro", 1, 5, 0, "completado"},
                {2, 1, "arco", 1, 8, 0, "completado"},
                {3, 5, "bloques_diamante", 10, 0, 50, "completado"},
                {5, 6, "bloques_construcción", 64, 0, 10, "completado"},
                {7, 1, "pistón", 4, 2, 0, "completado"},
                {6, 8, "perlas_ender", 5, 15, 0, "completado"},
                {3, 4, "beacon", 1, 100, 0, "completado"},
                {4, 8, "totem_inmortalidad", 1, 0, 75, "completado"},
                {1, 7, "redstone", 32, 1, 5, "completado"}
            };
            
            for (Object[] trade : trades) {
                for (int i = 0; i < trade.length; i++) {
                    stmt.setObject(i + 1, trade[i]);
                }
                stmt.addBatch();
            }
            int[] results = stmt.executeBatch();
            logger.info("Insertados " + results.length + " intercambios comerciales");
        }
        
        logger.info("Todos los datos de ejemplo insertados exitosamente con la nueva estructura");
    }
    
    /**
     * Get the database connection
     * @return Database connection
     */
    public Connection getConnection() {
        return connection;
    }
    
    /**
     * Close the database connection
     */
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                logger.info("Conexión a base de datos SQL cerrada");
            } catch (SQLException e) {
                logger.warning("Error al cerrar la base de datos: " + e.getMessage());
            }
        }
    }
    
    /**
     * Check if database is connected and ready
     * @return true if connection is valid
     */
    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }
    
    /**
     * Verify that data was inserted correctly
     * @return true if data verification passes
     */
    public boolean verifyData() {
        if (!isConnected()) {
            logger.warning("No se puede verificar datos: base de datos desconectada");
            return false;
        }
        
        try (Statement stmt = connection.createStatement()) {
            // Check players table
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM Jugadores");
            if (rs.next()) {
                int playerCount = rs.getInt(1);
                logger.info("Jugadores en base de datos: " + playerCount);
                if (playerCount == 0) {
                    logger.warning("¡No hay jugadores en la base de datos!");
                    return false;
                }
            }
            
            // Check if we have players in overworld
            rs = stmt.executeQuery("SELECT COUNT(*) FROM Jugadores WHERE mundo = 'overworld'");
            if (rs.next()) {
                int overworldPlayers = rs.getInt(1);
                logger.info("Jugadores en overworld: " + overworldPlayers);
            }
            
            // Verify foreign key relationships work correctly
            rs = stmt.executeQuery("""
                SELECT COUNT(*) FROM Inventarios i 
                INNER JOIN Jugadores j ON i.jugador_fk = j.jugador_pk
            """);
            if (rs.next()) {
                int inventoryItems = rs.getInt(1);
                logger.info("Items de inventario con relaciones válidas: " + inventoryItems);
            }
            
            // Verify constructions have valid foreign keys
            rs = stmt.executeQuery("""
                SELECT COUNT(*) FROM Construcciones c 
                INNER JOIN Jugadores j ON c.jugador_fk = j.jugador_pk
            """);
            if (rs.next()) {
                int constructions = rs.getInt(1);
                logger.info("Construcciones con relaciones válidas: " + constructions);
            }
            
            // Test a sample query
            rs = stmt.executeQuery("SELECT nombre FROM Jugadores WHERE mundo = 'overworld' LIMIT 3");
            logger.info("Jugadores de ejemplo en overworld:");
            while (rs.next()) {
                logger.info("- " + rs.getString("nombre"));
            }
            
            logger.info("Verificación de integridad de base de datos completada exitosamente");
            return true;
            
        } catch (SQLException e) {
            logger.severe("Error al verificar datos de la base de datos: " + e.getMessage());
            return false;
        }
    }
}