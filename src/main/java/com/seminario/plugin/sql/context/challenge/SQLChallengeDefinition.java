package com.seminario.plugin.sql.context.challenge;

/**
 * Generic challenge model for context-specific challenge banks.
 */
public class SQLChallengeDefinition {

    private final String description;
    private final String expectedQuery;
    private final String hint1;
    private final String hint2;
    private final String hint3;
    private final String difficultyKey;

    public SQLChallengeDefinition(
        String description,
        String expectedQuery,
        String hint1,
        String hint2,
        String hint3,
        String difficultyKey
    ) {
        this.description = description;
        this.expectedQuery = expectedQuery;
        this.hint1 = hint1;
        this.hint2 = hint2;
        this.hint3 = hint3;
        this.difficultyKey = difficultyKey;
    }

    public String getDescription() {
        return description;
    }

    public String getExpectedQuery() {
        return expectedQuery;
    }

    public String getHint1() {
        return hint1;
    }

    public String getHint2() {
        return hint2;
    }

    public String getHint3() {
        return hint3;
    }

    public String getDifficultyKey() {
        return difficultyKey;
    }
}
