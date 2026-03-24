package com.seminario.plugin.sql.context;

import java.util.List;

/**
 * Provides schema metadata and educational schema information for a context.
 */
public interface SQLSchemaProvider {

    /**
     * Human-readable schema text for players/admins.
     */
    String getSchemaInfo();

    /**
     * Known tables for helpful error messages.
     */
    List<String> getKnownTables();
}
