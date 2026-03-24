package com.seminario.plugin.sql.battle;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Structured result of executing a validated player query in the SQL BATTLE phase.
 *
 * Immutable; constructed via the nested {@link Builder}.
 *
 * Game interpretation guide:
 *   - SELECT results are in {@link #getRows()} as ordered Map<columnName, value>.
 *   - rowsAffected for INSERT/UPDATE/DELETE is the JDBC update count.
 *   - JOIN bonus: if the player used an INNER JOIN, joinBonusPercent > 0 (+25 by default).
 *     The game layer should apply this to damage, healing, or other effects.
 *   - tablesAccessed: which of the 6 battle tables appeared in the query (for audit/hints).
 */
public class BattleExecutionResult {

    private final boolean success;
    private final String queryType;
    private final int rowsAffected;
    private final List<Map<String, Object>> rows;
    private final List<String> tablesAccessed;
    private final boolean usedJoin;
    private final int joinBonusPercent;
    private final int actionPointCost;
    private final String errorMessage;

    private BattleExecutionResult(Builder b) {
        this.success          = b.success;
        this.queryType        = b.queryType;
        this.rowsAffected     = b.rowsAffected;
        this.rows             = b.rows            != null ? Collections.unmodifiableList(b.rows)           : Collections.emptyList();
        this.tablesAccessed   = b.tablesAccessed  != null ? Collections.unmodifiableList(b.tablesAccessed) : Collections.emptyList();
        this.usedJoin         = b.usedJoin;
        this.joinBonusPercent = b.joinBonusPercent;
        this.actionPointCost  = b.actionPointCost;
        this.errorMessage     = b.errorMessage;
    }

    // --- static factory for error cases ---

    public static BattleExecutionResult error(String queryType, int actionPointCost, String message) {
        return new Builder()
                .success(false)
                .queryType(queryType)
                .actionPointCost(actionPointCost)
                .errorMessage(message)
                .build();
    }

    // --- accessors ---

    /** True if the query executed without errors. */
    public boolean isSuccess()          { return success; }

    /** SELECT / INSERT / UPDATE / DELETE */
    public String getQueryType()        { return queryType; }

    /** Number of rows returned (SELECT) or modified (INSERT/UPDATE/DELETE). */
    public int getRowsAffected()        { return rowsAffected; }

    /** SELECT result rows; empty for non-SELECT queries. */
    public List<Map<String, Object>> getRows() { return rows; }

    /** Battle tables referenced in the query (subset of the 6 battle tables). */
    public List<String> getTablesAccessed() { return tablesAccessed; }

    /** True when the query contained at least one JOIN clause. */
    public boolean isUsedJoin()         { return usedJoin; }

    /**
     * Bonus percentage to apply to the primary game effect.
     * 25 means "+25%". 0 means no bonus (no JOIN was used).
     */
    public int getJoinBonusPercent()    { return joinBonusPercent; }

    /** Action points this query cost the player. */
    public int getActionPointCost()     { return actionPointCost; }

    /** Non-null only when isSuccess() == false. */
    public String getErrorMessage()     { return errorMessage; }
    public boolean hasError()           { return errorMessage != null && !errorMessage.isEmpty(); }

    // --- builder ---

    public static class Builder {
        private boolean success          = false;
        private String queryType         = "UNKNOWN";
        private int rowsAffected         = 0;
        private List<Map<String, Object>> rows;
        private List<String> tablesAccessed;
        private boolean usedJoin         = false;
        private int joinBonusPercent     = 0;
        private int actionPointCost      = 0;
        private String errorMessage;

        public Builder success(boolean v)                           { this.success = v;          return this; }
        public Builder queryType(String v)                          { this.queryType = v;        return this; }
        public Builder rowsAffected(int v)                          { this.rowsAffected = v;     return this; }
        public Builder rows(List<Map<String, Object>> v)            { this.rows = v;             return this; }
        public Builder tablesAccessed(List<String> v)               { this.tablesAccessed = v;   return this; }
        public Builder usedJoin(boolean v)                          { this.usedJoin = v;         return this; }
        public Builder joinBonusPercent(int v)                      { this.joinBonusPercent = v; return this; }
        public Builder actionPointCost(int v)                       { this.actionPointCost = v;  return this; }
        public Builder errorMessage(String v)                       { this.errorMessage = v;     return this; }

        public BattleExecutionResult build() { return new BattleExecutionResult(this); }
    }
}
