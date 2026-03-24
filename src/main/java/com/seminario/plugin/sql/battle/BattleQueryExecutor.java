package com.seminario.plugin.sql.battle;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Executes validated player SQL queries against the SQL BATTLE database.
 *
 * Responsibilities:
 *   1. Validates the query via {@link BattleQueryValidator}.
 *   2. Detects which of the 6 battle tables are referenced.
 *   3. Detects JOIN usage and awards a bonus multiplier.
 *   4. Executes the query via JDBC and returns a structured {@link BattleExecutionResult}.
 *
 * JOIN bonus:
 *   If the player writes an INNER JOIN (or any JOIN variant), joinBonusPercent = 25.
 *   The game layer is responsible for translating this into actual damage/healing bonuses.
 *
 * Thread safety: this class is stateless and safe to reuse across player sessions.
 * Each call requires its own active {@link Connection}.
 */
public class BattleQueryExecutor {

    /** Bonus awarded for using any JOIN clause (percentage points). */
    public static final int JOIN_BONUS_PERCENT = 25;

    /** All table names in the battle schema. */
    private static final Set<String> KNOWN_TABLES = new HashSet<>(Arrays.asList(
            "jugador", "tipos_item", "almacen", "inventario", "tipos_enemigo", "enemigos"
    ));

    /**
     * Matches any JOIN variant: INNER JOIN, LEFT JOIN, RIGHT JOIN, CROSS JOIN, or bare JOIN.
     * Requires a word boundary after JOIN to avoid matching partial identifiers.
     */
    private static final Pattern JOIN_PATTERN = Pattern.compile(
            "\\b(?:INNER\\s+JOIN|LEFT\\s+(?:OUTER\\s+)?JOIN|RIGHT\\s+(?:OUTER\\s+)?JOIN|CROSS\\s+JOIN|JOIN)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private final BattleQueryValidator validator;

    public BattleQueryExecutor() {
        this.validator = new BattleQueryValidator();
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Validates and executes a player query.
     *
     * @param connection active connection from {@link BattleSQLDatabase#getConnection()}
     * @param rawQuery   the SQL string typed by the player
     * @return a structured result including rows, tables accessed, JOIN bonus, and cost
     */
    public BattleExecutionResult execute(Connection connection, String rawQuery) {
        // Step 1: validate
        BattleValidationResult validation = validator.validate(rawQuery);
        if (!validation.isAllowed()) {
            return BattleExecutionResult.error(
                    validation.getQueryType(), 0, validation.getReason());
        }

        // Normalise query for execution
        String query = rawQuery.trim();
        if (query.endsWith(";")) {
            query = query.substring(0, query.length() - 1).trim();
        }

        String queryType      = validation.getQueryType();
        int    actionPoints   = validation.getActionPointCost();
        boolean usedJoin      = detectJoin(query);
        List<String> tables   = detectTables(query);
        int joinBonus         = usedJoin ? JOIN_BONUS_PERCENT : 0;

        // Step 2: execute
        try {
            if ("SELECT".equals(queryType)) {
                return executeSelect(connection, query, queryType, actionPoints, usedJoin, joinBonus, tables);
            } else {
                return executeModify(connection, query, queryType, actionPoints, usedJoin, joinBonus, tables);
            }
        } catch (SQLException e) {
            return BattleExecutionResult.error(queryType, actionPoints,
                    "Error SQL: " + sanitiseSQLError(e.getMessage()));
        }
    }

    // -----------------------------------------------------------------------
    // Detection utilities (also exposed for testing / game layer hinting)
    // -----------------------------------------------------------------------

    /**
     * Returns true if the query contains any JOIN clause.
     * Used to calculate the JOIN bonus reward.
     */
    public boolean detectJoin(String query) {
        return JOIN_PATTERN.matcher(query).find();
    }

    /**
     * Returns the names of the known battle tables referenced in the query.
     * Matching is word-boundary based to avoid false positives on column names.
     */
    public List<String> detectTables(String query) {
        String upper = query.toUpperCase();
        List<String> found = new ArrayList<>();
        for (String table : KNOWN_TABLES) {
            Pattern p = Pattern.compile("\\b" + Pattern.quote(table.toUpperCase()) + "\\b");
            if (p.matcher(upper).find()) {
                found.add(table);
            }
        }
        return found;
    }

    // -----------------------------------------------------------------------
    // Private execution helpers
    // -----------------------------------------------------------------------

    private BattleExecutionResult executeSelect(
            Connection conn, String query, String queryType,
            int cost, boolean usedJoin, int joinBonus, List<String> tables) throws SQLException {

        try (Statement stmt = conn.createStatement(
                ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
             ResultSet rs = stmt.executeQuery(query)) {

            List<Map<String, Object>> rows = extractRows(rs);
            return new BattleExecutionResult.Builder()
                    .success(true)
                    .queryType(queryType)
                    .rowsAffected(rows.size())
                    .rows(rows)
                    .tablesAccessed(tables)
                    .usedJoin(usedJoin)
                    .joinBonusPercent(joinBonus)
                    .actionPointCost(cost)
                    .build();
        }
    }

    private BattleExecutionResult executeModify(
            Connection conn, String query, String queryType,
            int cost, boolean usedJoin, int joinBonus, List<String> tables) throws SQLException {

        try (Statement stmt = conn.createStatement()) {
            int affected = stmt.executeUpdate(query);
            return new BattleExecutionResult.Builder()
                    .success(true)
                    .queryType(queryType)
                    .rowsAffected(affected)
                    .tablesAccessed(tables)
                    .usedJoin(usedJoin)
                    .joinBonusPercent(joinBonus)
                    .actionPointCost(cost)
                    .build();
        }
    }

    /**
     * Converts a {@link ResultSet} into a list of ordered maps (preserving column order).
     * Each map key is the column name; value is the raw Java object from JDBC.
     */
    private List<Map<String, Object>> extractRows(ResultSet rs) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        ResultSetMetaData meta = rs.getMetaData();
        int cols = meta.getColumnCount();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= cols; i++) {
                row.put(meta.getColumnLabel(i), rs.getObject(i));
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * Strips internal H2 noise from error messages before showing to the player.
     * Keeps the message readable but removes stack references.
     */
    private String sanitiseSQLError(String message) {
        if (message == null) return "Error desconocido";
        // H2 often appends "[42xxx-nnn]" error codes — keep just the human part
        int bracket = message.indexOf(" [");
        return bracket > 0 ? message.substring(0, bracket) : message;
    }
}
