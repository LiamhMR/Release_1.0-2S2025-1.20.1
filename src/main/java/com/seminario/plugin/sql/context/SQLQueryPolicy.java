package com.seminario.plugin.sql.context;

/**
 * Defines query restrictions for a SQL context.
 */
public interface SQLQueryPolicy {

    /**
     * Returns true when the query is allowed to execute.
     */
    boolean isAllowed(String query);

    /**
     * Returns an error message when query is not allowed.
     */
    String getDeniedMessage(String query);
}
