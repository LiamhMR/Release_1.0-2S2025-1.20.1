package com.seminario.plugin.sql.context;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Provides SQL connections for a specific context.
 */
public interface SQLConnectionProvider {

    /**
     * Returns a connection for query execution.
     */
    Connection getConnection() throws SQLException;

    /**
     * Indicates if the underlying provider is currently usable.
     */
    boolean isConnected();

    /**
     * Whether the caller should close the connection after use.
     *
     * Providers backed by a shared singleton connection can return false.
     */
    default boolean shouldCloseConnectionAfterUse() {
        return true;
    }

    /**
     * Releases provider resources.
     */
    void close();
}
