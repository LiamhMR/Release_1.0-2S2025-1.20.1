package com.seminario.plugin.sql.context;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry for SQL contexts.
 */
public class SQLContextRegistry {

    private final Map<String, SQLContext> contexts = new ConcurrentHashMap<>();

    public void register(SQLContext context) {
        contexts.put(context.getId(), context);
    }

    public Optional<SQLContext> find(String contextId) {
        return Optional.ofNullable(contexts.get(contextId));
    }

    public SQLContext require(String contextId) {
        SQLContext context = contexts.get(contextId);
        if (context == null) {
            throw new IllegalArgumentException("SQL context not found: " + contextId);
        }
        return context;
    }

    public Map<String, SQLContext> getAll() {
        return Collections.unmodifiableMap(contexts);
    }

    public boolean unregister(String contextId) {
        SQLContext removed = contexts.remove(contextId);
        if (removed == null) {
            return false;
        }
        removed.getConnectionProvider().close();
        return true;
    }

    public void shutdown() {
        for (SQLContext context : contexts.values()) {
            context.getConnectionProvider().close();
        }
        contexts.clear();
    }
}
