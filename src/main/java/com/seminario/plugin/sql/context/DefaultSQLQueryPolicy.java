package com.seminario.plugin.sql.context;

/**
 * Basic implementation that optionally enforces read-only (SELECT) mode.
 */
public class DefaultSQLQueryPolicy implements SQLQueryPolicy {

    private final boolean readOnly;

    public DefaultSQLQueryPolicy(boolean readOnly) {
        this.readOnly = readOnly;
    }

    @Override
    public boolean isAllowed(String query) {
        if (!readOnly) {
            return true;
        }

        if (query == null) {
            return false;
        }

        String trimmed = query.trim().toUpperCase();
        return trimmed.startsWith("SELECT");
    }

    @Override
    public String getDeniedMessage(String query) {
        if (!readOnly) {
            return "Consulta no permitida";
        }
        return "Esta modalidad permite solo consultas SELECT.";
    }
}
