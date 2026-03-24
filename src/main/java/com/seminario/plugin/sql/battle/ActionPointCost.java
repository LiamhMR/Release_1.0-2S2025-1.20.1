package com.seminario.plugin.sql.battle;

/**
 * Action point cost per SQL query type in the battle preparation phase.
 *
 * Players have a fixed budget of points per wave (default 5).
 * Instant (free) queries are not part of this enum — they are SELECT, which costs 1.
 *
 * Cost table:
 *   SELECT  = 1  (read-only, cheapest)
 *   INSERT  = 2  (moves items to inventario)
 *   UPDATE  = 2  (adjusts item quantities in inventario)
 *   DELETE  = 3  (removes items from inventario)
 */
public enum ActionPointCost {

    SELECT(1),
    INSERT(2),
    UPDATE(2),
    DELETE(3),
    UNKNOWN(0);

    private final int cost;

    ActionPointCost(int cost) {
        this.cost = cost;
    }

    public int getCost() {
        return cost;
    }

    /**
     * Derives the cost from the first keyword of the raw query.
     * Returns UNKNOWN for DDL or unrecognised statements.
     */
    public static ActionPointCost fromQuery(String query) {
        if (query == null || query.isBlank()) return UNKNOWN;
        String first = query.trim().toUpperCase().split("\\s+")[0];
        return switch (first) {
            case "SELECT" -> SELECT;
            case "INSERT" -> INSERT;
            case "UPDATE" -> UPDATE;
            case "DELETE" -> DELETE;
            default       -> UNKNOWN;
        };
    }
}
