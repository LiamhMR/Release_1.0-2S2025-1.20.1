package com.seminario.plugin.sql.battle;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates player SQL queries before execution in the SQL BATTLE preparation phase.
 *
 * Rules enforced (defence-in-depth):
 *
 *  1. No multi-statement injection (extra semicolons mid-query).
 *  2. No DDL / system operations: DROP, CREATE, ALTER, TRUNCATE, RENAME,
 *     GRANT, REVOKE, COMMIT, ROLLBACK, SAVEPOINT, EXEC, EXECUTE, CALL, SCRIPT.
 *  3. Only SELECT, INSERT, UPDATE, DELETE are accepted.
 *  4. Writes (INSERT/UPDATE/DELETE) are restricted to the 'inventario' table.
 *     All other tables (jugador, almacen, tipos_item, tipos_enemigo, enemigos) are read-only.
 *  5. UPDATE without a WHERE clause is blocked.
 *  6. DELETE without a WHERE clause is blocked.
 *  7. INSERT INTO ... SELECT without LIMIT is blocked (prevents bulk copy exploits).
 *
 * This validator does NOT execute the query; it only inspects the SQL text.
 */
public class BattleQueryValidator {

    // Tables the player may only SELECT from
    private static final Set<String> READ_ONLY_TABLES = new HashSet<>(Arrays.asList(
            "jugador", "almacen", "tipos_item", "tipos_enemigo", "enemigos"
    ));

    // Table the player is allowed to write to
    private static final Set<String> WRITABLE_TABLES = new HashSet<>(Arrays.asList(
            "inventario"
    ));

    // DDL / DCL / TCL keywords that are never allowed
    private static final Set<String> FORBIDDEN_VERBS = new HashSet<>(Arrays.asList(
            "DROP", "CREATE", "ALTER", "TRUNCATE", "RENAME",
            "GRANT", "REVOKE",
            "COMMIT", "ROLLBACK", "SAVEPOINT",
            "EXEC", "EXECUTE", "CALL", "SCRIPT"
    ));

    // UPDATE <table> SET ... (no WHERE anywhere after SET)
    private static final Pattern UPDATE_WITHOUT_WHERE = Pattern.compile(
            "^\\s*UPDATE\\s+\\S+\\s+SET\\b(?!.*\\bWHERE\\b).+",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    // DELETE FROM <table>  — nothing after the table name (no WHERE)
    private static final Pattern DELETE_WITHOUT_WHERE = Pattern.compile(
            "^\\s*DELETE\\s+FROM\\s+\\S+\\s*$",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Validates the raw player query and returns a {@link BattleValidationResult}.
     *
     * @param rawQuery the SQL string exactly as the player typed it
     * @return allow (with cost) or deny (with player-facing reason)
     */
    public BattleValidationResult validate(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return BattleValidationResult.deny("La consulta está vacía.", "UNKNOWN");
        }

        // Strip string literals to avoid false positives in pattern checks
        String sanitised = removeStringLiterals(rawQuery.trim());

        // 1. Multi-statement check — more than one semicolon, or semicolon not at the very end
        long semis = sanitised.chars().filter(c -> c == ';').count();
        if (semis > 1 || (semis == 1 && !sanitised.trim().endsWith(";"))) {
            return BattleValidationResult.deny(
                    "No se permiten múltiples sentencias en una consulta.", "UNKNOWN");
        }

        // Work on a clean copy without trailing semicolon
        String query = rawQuery.trim();
        if (query.endsWith(";")) {
            query = query.substring(0, query.length() - 1).trim();
        }
        String upper = query.toUpperCase();

        // 2. Forbidden first verb (DDL / DCL / TCL)
        String firstToken = upper.split("\\s+")[0];
        if (FORBIDDEN_VERBS.contains(firstToken)) {
            return BattleValidationResult.deny(
                    "Operación '" + firstToken + "' no está permitida en la fase de batalla.", firstToken);
        }

        // 3. Only SELECT / INSERT / UPDATE / DELETE are legal
        if (!firstToken.equals("SELECT") && !firstToken.equals("INSERT")
                && !firstToken.equals("UPDATE") && !firstToken.equals("DELETE")) {
            return BattleValidationResult.deny(
                    "Solo se permiten SELECT, INSERT, UPDATE y DELETE.", firstToken);
        }

        String queryType = firstToken;

        // 4. Write-access control: only 'inventario' is writable
        if (!queryType.equals("SELECT")) {
            String target = extractTargetTable(query, queryType);
            if (target == null) {
                return BattleValidationResult.deny(
                        "No se pudo determinar la tabla destino de la operación.", queryType);
            }
            String targetLower = target.toLowerCase();
            if (READ_ONLY_TABLES.contains(targetLower)) {
                return BattleValidationResult.deny(
                        "La tabla '" + target + "' es de solo lectura. "
                                + "Solo puedes modificar 'inventario'.", queryType);
            }
            if (!WRITABLE_TABLES.contains(targetLower)) {
                return BattleValidationResult.deny(
                        "No tienes permiso para escribir en la tabla '" + target + "'.", queryType);
            }
        }

        // 5. UPDATE without WHERE
        if (queryType.equals("UPDATE") && UPDATE_WITHOUT_WHERE.matcher(query).matches()) {
            return BattleValidationResult.deny(
                    "UPDATE sin WHERE no está permitido. Especifica la fila a modificar "
                            + "(ej: WHERE item_id = 1).", "UPDATE");
        }

        // 6. DELETE without WHERE
        if (queryType.equals("DELETE") && DELETE_WITHOUT_WHERE.matcher(query).matches()) {
            return BattleValidationResult.deny(
                    "DELETE sin WHERE no está permitido. Especifica la fila a eliminar "
                            + "(ej: WHERE item_id = 1).", "DELETE");
        }

        // 7. INSERT ... SELECT without LIMIT (bulk copy exploit)
        if (queryType.equals("INSERT") && upper.contains("SELECT") && !upper.contains("LIMIT")) {
            return BattleValidationResult.deny(
                    "INSERT ... SELECT sin LIMIT no está permitido. "
                            + "Añade un LIMIT para evitar inserciones masivas.", "INSERT");
        }

        // All checks passed — compute cost
        int cost = ActionPointCost.fromQuery(query).getCost();
        return BattleValidationResult.allow(queryType, cost);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Extracts the target table name from INSERT / UPDATE / DELETE statements.
     * Returns null if the syntax does not match expected patterns.
     */
    private String extractTargetTable(String query, String queryType) {
        try {
            String upper = query.trim().toUpperCase();
            return switch (queryType) {
                case "UPDATE" -> {
                    // UPDATE <table> SET ...
                    String[] parts = query.trim().split("\\s+");
                    yield parts.length >= 2 ? parts[1] : null;
                }
                case "DELETE" -> {
                    // DELETE FROM <table> ...
                    if (upper.startsWith("DELETE FROM")) {
                        String rest = query.trim().substring("DELETE FROM".length()).trim();
                        yield rest.split("[\\s;]+")[0];
                    }
                    yield null;
                }
                case "INSERT" -> {
                    // INSERT INTO <table> ...
                    if (upper.startsWith("INSERT INTO")) {
                        String rest = query.trim().substring("INSERT INTO".length()).trim();
                        yield rest.split("[\\s(;]+")[0];
                    }
                    yield null;
                }
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Replaces single-quoted string literals with empty placeholders to avoid false positives
     * when scanning for keywords inside values.
     */
    private String removeStringLiterals(String query) {
        return query.replaceAll("'[^']*'", "''");
    }
}
