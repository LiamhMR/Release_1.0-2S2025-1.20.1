package com.seminario.plugin.sql.context;

import com.seminario.plugin.sql.context.challenge.SQLChallengeRepository;

/**
 * Represents an isolated SQL execution context for a game mode.
 */
public interface SQLContext {

    /**
     * Stable context identifier (e.g. sqldungeon-default, sqlbattle-v1).
     */
    String getId();

    SQLConnectionProvider getConnectionProvider();

    SQLSchemaProvider getSchemaProvider();

    SQLQueryPolicy getQueryPolicy();

    /**
     * Optional challenge repository for this context.
     */
    SQLChallengeRepository getChallengeRepository();
}
