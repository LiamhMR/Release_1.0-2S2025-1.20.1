package com.seminario.plugin.sql.context.engine;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.logging.Logger;

import com.seminario.plugin.sql.SQLQueryResult;
import com.seminario.plugin.sql.context.SQLConnectionProvider;
import com.seminario.plugin.sql.context.SQLContext;

/**
 * Generic validation engine that can execute against any SQLContext.
 */
public class GenericSQLValidationEngine {

    private static final Logger logger = Logger.getLogger(GenericSQLValidationEngine.class.getName());

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

    public ValidationResult validateQuery(SQLContext context, String userQuery, String expectedQuery) {
        if (!isContextAvailable(context)) {
            return new ValidationResult(false, "Error: Contexto SQL no disponible", null, null, "Context disconnected");
        }

        String cleanUserQuery = cleanQuery(userQuery);
        String cleanExpectedQuery = cleanQuery(expectedQuery);

        String denied = checkPolicy(context, cleanUserQuery);
        if (denied != null) {
            return new ValidationResult(false, denied, null, null, "Policy denied");
        }

        try {
            List<String> userResults = executeQuery(context, cleanUserQuery);
            List<String> expectedResults = executeQuery(context, cleanExpectedQuery);

            boolean resultsMatch = compareResults(userResults, expectedResults);
            if (resultsMatch) {
                return new ValidationResult(true,
                    "Correcto: la consulta devuelve el resultado esperado.",
                    userResults,
                    expectedResults,
                    null);
            }

            String feedback = generateFeedback(cleanUserQuery, userResults, expectedResults);
            return new ValidationResult(false, feedback, userResults, expectedResults, null);

        } catch (SQLException e) {
            return new ValidationResult(false, analyzeSQLError(context, e), null, null, e.getMessage());
        }
    }

    public SQLQueryResult executeQueryForLaboratory(SQLContext context, String query) {
        if (!isContextAvailable(context)) {
            return SQLQueryResult.error("Error: Contexto SQL no disponible", query);
        }

        String cleanQuery = cleanQuery(query);
        String denied = checkPolicy(context, cleanQuery);
        if (denied != null) {
            return SQLQueryResult.error(denied, cleanQuery);
        }

        try {
            Connection conn = context.getConnectionProvider().getConnection();
            Statement stmt = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            ResultSet resultSet = stmt.executeQuery(cleanQuery);

            // Intentionally leave resources open so ResultSet can be consumed by caller.
            return SQLQueryResult.success(cleanQuery, resultSet);
        } catch (SQLException e) {
            return SQLQueryResult.error("Error SQL: " + e.getMessage(), cleanQuery);
        } catch (Exception e) {
            return SQLQueryResult.error("Error interno: " + e.getMessage(), cleanQuery);
        }
    }

    public SQLQueryResult validateQueryWithResults(SQLContext context, String userQuery, String expectedQuery) {
        if (!isContextAvailable(context)) {
            return SQLQueryResult.error("Error: Contexto SQL no disponible", userQuery);
        }

        String cleanUserQuery = cleanQuery(userQuery);
        String cleanExpectedQuery = cleanQuery(expectedQuery);

        String denied = checkPolicy(context, cleanUserQuery);
        if (denied != null) {
            return SQLQueryResult.error(denied, cleanUserQuery);
        }

        try {
            Connection conn = context.getConnectionProvider().getConnection();
            Statement bookStmt = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            ResultSet userResultSet = bookStmt.executeQuery(cleanUserQuery);

            List<String> userResults = extractRows(userResultSet);
            List<String> expectedResults = executeQuery(context, cleanExpectedQuery);
            boolean isCorrect = compareResults(userResults, expectedResults);

            if (isCorrect) {
                return SQLQueryResult.correct(
                    "Correcto: la consulta devuelve el resultado esperado.",
                    userResults,
                    expectedResults,
                    userResultSet,
                    cleanUserQuery,
                    !userResults.isEmpty()
                );
            }

            String feedback = generateFeedback(cleanUserQuery, userResults, expectedResults);
            return SQLQueryResult.incorrect(
                feedback,
                userResults,
                expectedResults,
                userResultSet,
                cleanUserQuery,
                !userResults.isEmpty()
            );

        } catch (SQLException e) {
            return SQLQueryResult.error(analyzeSQLError(context, e), cleanUserQuery);
        }
    }

    public String getSchemaInfo(SQLContext context) {
        if (context == null || context.getSchemaProvider() == null) {
            return "No hay informacion de esquema para este contexto.";
        }
        return context.getSchemaProvider().getSchemaInfo();
    }

    public boolean testContext(SQLContext context) {
        try {
            List<String> results = executeQuery(context, "SELECT 1");
            return !results.isEmpty();
        } catch (SQLException e) {
            logger.warning("SQL context test failed for " + (context != null ? context.getId() : "<null>") + ": " + e.getMessage());
            return false;
        }
    }

    private boolean isContextAvailable(SQLContext context) {
        return context != null
            && context.getConnectionProvider() != null
            && context.getConnectionProvider().isConnected();
    }

    private String checkPolicy(SQLContext context, String query) {
        if (context == null || context.getQueryPolicy() == null) {
            return null;
        }
        if (context.getQueryPolicy().isAllowed(query)) {
            return null;
        }
        return context.getQueryPolicy().getDeniedMessage(query);
    }

    private List<String> executeQuery(SQLContext context, String query) throws SQLException {
        SQLConnectionProvider provider = context.getConnectionProvider();
        Connection conn = null;
        boolean closeConn = provider.shouldCloseConnectionAfterUse();

        try {
            conn = provider.getConnection();
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
                return extractRows(rs);
            }
        } finally {
            if (closeConn && conn != null) {
                try {
                    conn.close();
                } catch (SQLException ignored) {
                    // Ignore close errors on best-effort cleanup.
                }
            }
        }
    }

    private List<String> extractRows(ResultSet rs) throws SQLException {
        List<String> rows = new ArrayList<>();
        int columnCount = rs.getMetaData().getColumnCount();

        while (rs.next()) {
            StringJoiner row = new StringJoiner(" | ");
            for (int i = 1; i <= columnCount; i++) {
                String value = rs.getString(i);
                row.add(value != null ? value : "NULL");
            }
            rows.add(row.toString());
        }

        return rows;
    }

    private boolean compareResults(List<String> results1, List<String> results2) {
        if (results1.size() != results2.size()) {
            return false;
        }

        List<String> sorted1 = new ArrayList<>(results1);
        List<String> sorted2 = new ArrayList<>(results2);
        sorted1.sort(String::compareTo);
        sorted2.sort(String::compareTo);
        return sorted1.equals(sorted2);
    }

    private String generateFeedback(String userQuery, List<String> userResults, List<String> expectedResults) {
        StringBuilder feedback = new StringBuilder("Consulta incorrecta. ");
        String upperQuery = userQuery.toUpperCase();

        if (userResults.size() > expectedResults.size()) {
            feedback.append("Tu consulta devuelve demasiados resultados. ");
            if (!upperQuery.contains("WHERE")) {
                feedback.append("Necesitas una condicion WHERE? ");
            }
        } else if (userResults.size() < expectedResults.size()) {
            feedback.append("Tu consulta devuelve muy pocos resultados. ");
            if (upperQuery.contains("WHERE")) {
                feedback.append("Revisa tus condiciones WHERE. ");
            }
        } else {
            feedback.append("El numero de resultados es correcto, pero los valores no coinciden. ");
        }

        if (!upperQuery.contains("SELECT")) {
            feedback.append("Recuerda usar SELECT para elegir columnas. ");
        }
        if (!upperQuery.contains("FROM")) {
            feedback.append("Necesitas especificar la tabla con FROM. ");
        }

        feedback.append(String.format("\nTu consulta devolvio %d resultados, se esperaban %d.",
            userResults.size(),
            expectedResults.size()));

        return feedback.toString();
    }

    private String analyzeSQLError(SQLContext context, SQLException e) {
        String errorMsg = e.getMessage().toLowerCase();

        if (errorMsg.contains("syntax error") || errorMsg.contains("unexpected token")) {
            return "Error de sintaxis SQL. Revisa que tu consulta este bien escrita.";
        }

        if (errorMsg.contains("table") && errorMsg.contains("not found")) {
            List<String> tables = context.getSchemaProvider().getKnownTables();
            if (tables.isEmpty()) {
                return "Tabla no encontrada. Verifica las tablas disponibles para este contexto.";
            }
            return "Tabla no encontrada. Tablas disponibles: " + String.join(", ", tables) + ".";
        }

        if (errorMsg.contains("column") && errorMsg.contains("not found")) {
            return "Columna no encontrada. Verifica el nombre de la columna en la tabla correspondiente.";
        }

        if (errorMsg.contains("ambiguous")) {
            return "Columna ambigua. Cuando uses JOIN, especifica tabla.columna.";
        }

        if (errorMsg.contains("group by")) {
            return "Error con GROUP BY. Revisa columnas no agregadas y funciones de agregacion.";
        }

        return "Error en la consulta SQL: " + e.getMessage();
    }

    private String cleanQuery(String query) {
        if (query == null) {
            return "";
        }
        return query.trim().replaceAll("\\s+", " ").replaceAll(";\\s*$", "");
    }
}
