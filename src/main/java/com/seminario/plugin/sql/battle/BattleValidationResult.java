package com.seminario.plugin.sql.battle;

/**
 * Result of validating a player query in the SQL BATTLE phase.
 */
public class BattleValidationResult {

    private final boolean allowed;
    private final String reason;
    private final String queryType;     // SELECT | INSERT | UPDATE | DELETE | UNKNOWN
    private final int actionPointCost;

    private BattleValidationResult(boolean allowed, String reason, String queryType, int actionPointCost) {
        this.allowed = allowed;
        this.reason = reason;
        this.queryType = queryType;
        this.actionPointCost = actionPointCost;
    }

    /** Query passed all safety checks and may be executed. */
    public static BattleValidationResult allow(String queryType, int cost) {
        return new BattleValidationResult(true, null, queryType, cost);
    }

    /** Query was rejected. Reason is a player-facing message explaining why. */
    public static BattleValidationResult deny(String reason, String queryType) {
        return new BattleValidationResult(false, reason, queryType, 0);
    }

    public boolean isAllowed()       { return allowed; }
    public String getReason()        { return reason; }
    public String getQueryType()     { return queryType; }
    public int getActionPointCost()  { return actionPointCost; }
}
