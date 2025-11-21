package com.seminario.plugin.sql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.plugin.java.JavaPlugin;

import com.seminario.plugin.database.SQLDatabase;

/**
 * Engine for validating SQL queries and providing educational feedback
 */
public class SQLValidationEngine {
    
    private static final Logger logger = Logger.getLogger(SQLValidationEngine.class.getName());
    private final SQLDatabase database;
    private final JavaPlugin plugin;
    
    public SQLValidationEngine(SQLDatabase database, JavaPlugin plugin) {
        this.database = database;
        this.plugin = plugin;
    }
    
    /**
     * Result of SQL query validation
     */
    public static class ValidationResult {
        private final boolean correct;
        private final String feedback;
        private final List<String> actualResults;
        private final List<String> expectedResults;
        private final String error;
        
        public ValidationResult(boolean correct, String feedback, List<String> actualResults, List<String> expectedResults, String error) {
            this.correct = correct;
            this.feedback = feedback;
            this.actualResults = actualResults != null ? actualResults : new ArrayList<>();
            this.expectedResults = expectedResults != null ? expectedResults : new ArrayList<>();
            this.error = error;
        }
        
        public boolean isCorrect() { return correct; }
        public String getFeedback() { return feedback; }
        public List<String> getActualResults() { return actualResults; }
        public List<String> getExpectedResults() { return expectedResults; }
        public String getError() { return error; }
        public boolean hasError() { return error != null && !error.isEmpty(); }
    }
    
    /**
     * Validate a SQL query against the expected result
     * @param userQuery The user's SQL query
     * @param expectedQuery The expected correct query (for comparison)
     * @return ValidationResult with feedback
     */
    public ValidationResult validateQuery(String userQuery, String expectedQuery) {
        if (!database.isConnected()) {
            return new ValidationResult(false, "Error: Base de datos no disponible", null, null, "Database disconnected");
        }
        
        // Clean and normalize queries
        String cleanUserQuery = cleanQuery(userQuery);
        String cleanExpectedQuery = cleanQuery(expectedQuery);
        
        try {
            // Execute both queries and compare results
            List<String> userResults = executeQuery(cleanUserQuery);
            List<String> expectedResults = executeQuery(cleanExpectedQuery);
            
            // Compare results
            boolean resultsMatch = compareResults(userResults, expectedResults);
            
            if (resultsMatch) {
                return new ValidationResult(true, 
                    "¡Correcto! Tu consulta devuelve el resultado esperado.", 
                    userResults, expectedResults, null);
            } else {
                String feedback = generateFeedback(cleanUserQuery, userResults, expectedResults);
                return new ValidationResult(false, feedback, userResults, expectedResults, null);
            }
            
        } catch (SQLException e) {
            // Analyze the SQL error to provide educational feedback
            String errorFeedback = analyzeSQLError(e, cleanUserQuery);
            return new ValidationResult(false, errorFeedback, null, null, e.getMessage());
        }
    }
    
    /**
     * Execute a SQL query and return results as strings
     * @param query The SQL query to execute
     * @return List of result strings
     * @throws SQLException if query execution fails
     */
    private List<String> executeQuery(String query) throws SQLException {
        List<String> results = new ArrayList<>();
        
        try (Statement stmt = database.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            
            int columnCount = rs.getMetaData().getColumnCount();
            
            while (rs.next()) {
                StringBuilder row = new StringBuilder();
                for (int i = 1; i <= columnCount; i++) {
                    if (i > 1) row.append(" | ");
                    String value = rs.getString(i);
                    row.append(value != null ? value : "NULL");
                }
                results.add(row.toString());
            }
        }
        
        return results;
    }
    
    /**
     * Compare two result sets
     * @param results1 First result set
     * @param results2 Second result set
     * @return true if results match
     */
    private boolean compareResults(List<String> results1, List<String> results2) {
        if (results1.size() != results2.size()) {
            return false;
        }
        
        // Sort both lists for comparison (order shouldn't matter for most cases)
        List<String> sorted1 = new ArrayList<>(results1);
        List<String> sorted2 = new ArrayList<>(results2);
        sorted1.sort(String::compareTo);
        sorted2.sort(String::compareTo);
        
        return sorted1.equals(sorted2);
    }
    
    /**
     * Generate educational feedback based on query differences
     * @param userQuery User's query
     * @param userResults User's results
     * @param expectedResults Expected results
     * @return Feedback message
     */
    private String generateFeedback(String userQuery, List<String> userResults, List<String> expectedResults) {
        StringBuilder feedback = new StringBuilder("Consulta incorrecta. ");
        
        // Analyze common mistakes
        String upperQuery = userQuery.toUpperCase();
        
        if (userResults.size() > expectedResults.size()) {
            feedback.append("Tu consulta devuelve demasiados resultados. ");
            if (!upperQuery.contains("WHERE")) {
                feedback.append("¿Necesitas agregar una condición WHERE? ");
            }
        } else if (userResults.size() < expectedResults.size()) {
            feedback.append("Tu consulta devuelve muy pocos resultados. ");
            if (upperQuery.contains("WHERE")) {
                feedback.append("Revisa tus condiciones WHERE. ");
            }
        } else {
            feedback.append("El número de resultados es correcto, pero los valores no coinciden. ");
        }
        
        // Check for common SQL keywords
        if (!upperQuery.contains("SELECT")) {
            feedback.append("Recuerda usar SELECT para elegir columnas. ");
        }
        if (!upperQuery.contains("FROM")) {
            feedback.append("Necesitas especificar la tabla con FROM. ");
        }
        
        // Add results comparison
        feedback.append(String.format("\nTu consulta devolvió %d resultados, se esperaban %d.", 
                                     userResults.size(), expectedResults.size()));
        
        return feedback.toString();
    }
    
    /**
     * Analyze SQL exceptions to provide educational feedback
     * @param e The SQL exception
     * @param query The user's query
     * @return Educational error message
     */
    private String analyzeSQLError(SQLException e, String query) {
        String errorMsg = e.getMessage().toLowerCase();
        
        if (errorMsg.contains("syntax error") || errorMsg.contains("unexpected token")) {
            return "Error de sintaxis SQL. Revisa que tu consulta esté bien escrita. " +
                   "Recuerda: SELECT columnas FROM tabla WHERE condición;";
        }
        
        if (errorMsg.contains("table") && errorMsg.contains("not found")) {
            return "Tabla no encontrada. Las tablas disponibles son: Jugadores, Inventarios, Construcciones, Logros, Comercio.";
        }
        
        if (errorMsg.contains("column") && errorMsg.contains("not found")) {
            return "Columna no encontrada. Verifica que el nombre de la columna sea correcto y exista en la tabla.";
        }
        
        if (errorMsg.contains("ambiguous")) {
            return "Columna ambigua. Cuando uses JOINs, especifica la tabla: tabla.columna";
        }
        
        if (errorMsg.contains("group by")) {
            return "Error con GROUP BY. Recuerda que todas las columnas en SELECT deben estar en GROUP BY o ser funciones agregadas.";
        }
        
        return "Error en la consulta SQL: " + e.getMessage() + 
               "\nRevisa la sintaxis y asegúrate de que las tablas y columnas existan.";
    }
    
    /**
     * Clean and normalize a SQL query
     * @param query The raw query
     * @return Cleaned query
     */
    private String cleanQuery(String query) {
        if (query == null) return "";
        
        return query.trim()
                   .replaceAll("\\s+", " ")  // Multiple spaces to single space
                   .replaceAll(";\\s*$", ""); // Remove trailing semicolon
    }
    
    /**
     * Get schema information for educational purposes with updated database structure
     * @return Schema information as formatted string
     */
    public String getSchemaInfo() {
        StringBuilder schema = new StringBuilder();
        schema.append("=== ESQUEMA DE BASE DE DATOS (ACTUALIZADO) ===\n\n");
        
        schema.append("TABLA: Jugadores [Tabla principal]\n");
        schema.append("- jugador_pk (INT PRIMARY KEY): Clave primaria única\n");
        schema.append("- nombre (VARCHAR UNIQUE): Nombre del jugador\n");
        schema.append("- nivel (INT): Nivel del jugador\n");
        schema.append("- diamantes (INT): Cantidad de diamantes\n");
        schema.append("- esmeraldas (INT): Cantidad de esmeraldas\n");
        schema.append("- oro (INT): Cantidad de oro\n");
        schema.append("- ubicacion_x, ubicacion_z (INT): Coordenadas\n");
        schema.append("- mundo (VARCHAR): Nombre del mundo\n");
        schema.append("- fecha_registro, ultimo_login: Timestamps\n\n");
        
        schema.append("TABLA: Inventarios [Items de jugadores]\n");
        schema.append("- inventario_pk (INT PRIMARY KEY): Clave primaria\n");
        schema.append("- jugador_fk (INT FOREIGN KEY): Referencia a jugador_pk\n");
        schema.append("- item (VARCHAR): Nombre del item\n");
        schema.append("- cantidad (INT): Cantidad del item\n");
        schema.append("- rareza (VARCHAR): común/raro/épico/legendario\n");
        schema.append("- encantado (BOOLEAN): Si está encantado\n");
        schema.append("- fecha_obtenido (TIMESTAMP): Cuándo se obtuvo\n\n");
        
        schema.append("TABLA: Construcciones [Edificios de jugadores]\n");
        schema.append("- construccion_pk (INT PRIMARY KEY): Clave primaria\n");
        schema.append("- jugador_fk (INT FOREIGN KEY): Referencia a jugador_pk\n");
        schema.append("- nombre (VARCHAR): Nombre de la construcción\n");
        schema.append("- tipo (VARCHAR): casa/castillo/granja/mina/torre/puente\n");
        schema.append("- tamaño (INT): Tamaño en bloques\n");
        schema.append("- bloques_usados (INT): Bloques utilizados\n");
        schema.append("- mundo (VARCHAR): Mundo donde está ubicada\n");
        schema.append("- coordenada_x, coordenada_y, coordenada_z (INT): Posición\n");
        schema.append("- activa (BOOLEAN): Si está activa o no\n\n");
        
        schema.append("TABLA: Logros [Achievements de jugadores]\n");
        schema.append("- logro_pk (INT PRIMARY KEY): Clave primaria\n");
        schema.append("- jugador_fk (INT FOREIGN KEY): Referencia a jugador_pk\n");
        schema.append("- nombre_logro (VARCHAR): Nombre del logro\n");
        schema.append("- descripcion (TEXT): Descripción del logro\n");
        schema.append("- puntos (INT): Puntos otorgados\n");
        schema.append("- categoria (VARCHAR): general/construcción/combate/exploración/comercio\n");
        schema.append("- fecha_obtenido (TIMESTAMP): Cuándo se obtuvo\n\n");
        
        schema.append("TABLA: Comercio [Transacciones comerciales]\n");
        schema.append("- comercio_pk (INT PRIMARY KEY): Clave primaria\n");
        schema.append("- vendedor_fk (INT FOREIGN KEY): Referencia a jugador_pk\n");
        schema.append("- comprador_fk (INT FOREIGN KEY): Referencia a jugador_pk\n");
        schema.append("- item (VARCHAR): Item intercambiado\n");
        schema.append("- cantidad (INT): Cantidad intercambiada\n");
        schema.append("- precio_diamantes (INT): Precio en diamantes\n");
        schema.append("- precio_esmeraldas (INT): Precio en esmeraldas\n");
        schema.append("- fecha_intercambio (TIMESTAMP): Cuándo ocurrió\n");
        schema.append("- estado (VARCHAR): completado/cancelado/pendiente\n\n");
        
        schema.append("CONVENCIONES DE NOMENCLATURA:\n");
        schema.append("- _pk: Sufijo para claves primarias (Primary Key)\n");
        schema.append("- _fk: Sufijo para claves foráneas (Foreign Key)\n");
        schema.append("- Restricciones de integridad y validación aplicadas\n");
        schema.append("- Índices optimizados para consultas frecuentes\n");
        
        return schema.toString();
    }
    
    /**
     * Test the database connection and basic functionality
     * @return true if database is working correctly
     */
    public boolean testDatabase() {
        try {
            List<String> results = executeQuery("SELECT COUNT(*) FROM Jugadores");
            return !results.isEmpty();
        } catch (SQLException e) {
            logger.warning("Database test failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Execute a query for laboratory mode (no validation, just execution)
     * @param query The SQL query to execute
     * @return SQLQueryResult with either results or error information
     */
    public SQLQueryResult executeQueryForLaboratory(String query) {
        if (!database.isConnected()) {
            return SQLQueryResult.error("Error: Base de datos no disponible", query);
        }
        
        String cleanQuery = cleanQuery(query);
        
        try {
            Connection conn = database.getConnection();
            Statement stmt = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            ResultSet resultSet = stmt.executeQuery(cleanQuery);
            
            // For laboratory mode, we just return success with the ResultSet
            // Note: Connection and Statement are NOT closed here because ResultSet needs them
            // They will be closed when the ResultSet is closed
            return SQLQueryResult.success(cleanQuery, resultSet);
            
        } catch (SQLException e) {
            return SQLQueryResult.error("Error SQL: " + e.getMessage(), cleanQuery);
        } catch (Exception e) {
            return SQLQueryResult.error("Error interno: " + e.getMessage(), cleanQuery);
        }
    }
    
    /**
     * Validate query and return enhanced result with ResultSet for book generation
     * @param userQuery The user's SQL query
     * @param expectedQuery The expected correct query
     * @return SQLQueryResult with validation results and raw ResultSet
     */
    public SQLQueryResult validateQueryWithResults(String userQuery, String expectedQuery) {
        if (!database.isConnected()) {
            return SQLQueryResult.error("Error: Base de datos no disponible", userQuery);
        }
        
        // Clean and normalize queries
        String cleanUserQuery = cleanQuery(userQuery);
        String cleanExpectedQuery = cleanQuery(expectedQuery);
        
        try {
            // Execute user query and get both ResultSet for book and results for validation
            List<String> userResults = new ArrayList<>();
            List<String> expectedResults = new ArrayList<>();
            boolean hasResults = false;
            
            // Create a fresh ResultSet for the book (this one will be returned)
            Statement bookStmt = database.getConnection().createStatement(
                ResultSet.TYPE_SCROLL_INSENSITIVE, 
                ResultSet.CONCUR_READ_ONLY);
            ResultSet userResultSet = bookStmt.executeQuery(cleanUserQuery);
            
            // Check if there are results and collect them for validation
            int columnCount = userResultSet.getMetaData().getColumnCount();
            
            while (userResultSet.next()) {
                hasResults = true;
                StringBuilder row = new StringBuilder();
                for (int i = 1; i <= columnCount; i++) {
                    if (i > 1) row.append(" | ");
                    String value = userResultSet.getString(i);
                    row.append(value != null ? value : "NULL");
                }
                userResults.add(row.toString());
            }
            
            // Reset ResultSet to beginning for book generation
            userResultSet.beforeFirst();
            
            // Execute expected query for comparison (separate connection)
            expectedResults = executeQuery(cleanExpectedQuery);
            
            // Compare results
            boolean resultsMatch = compareResults(userResults, expectedResults);
            
            if (resultsMatch) {
                return SQLQueryResult.correct(
                    "¡Correcto! Tu consulta devuelve el resultado esperado.", 
                    userResults, expectedResults, userResultSet, userQuery, hasResults);
            } else {
                String feedback = generateFeedback(cleanUserQuery, userResults, expectedResults);
                return SQLQueryResult.incorrect(feedback, userResults, expectedResults, 
                                              userResultSet, userQuery, hasResults);
            }
            
        } catch (SQLException e) {
            // Analyze the SQL error to provide educational feedback
            String errorFeedback = analyzeSQLError(e, cleanUserQuery);
            return SQLQueryResult.error(errorFeedback + " - " + e.getMessage(), userQuery);
        }
    }
}