package com.seminario.plugin.sql.context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Static schema provider for predefined contexts.
 */
public class StaticSQLSchemaProvider implements SQLSchemaProvider {

    private final String schemaInfo;
    private final List<String> knownTables;

    public StaticSQLSchemaProvider(String schemaInfo, List<String> knownTables) {
        this.schemaInfo = schemaInfo != null ? schemaInfo : "";
        this.knownTables = knownTables != null ? new ArrayList<>(knownTables) : new ArrayList<>();
    }

    @Override
    public String getSchemaInfo() {
        return schemaInfo;
    }

    @Override
    public List<String> getKnownTables() {
        return Collections.unmodifiableList(knownTables);
    }
}
