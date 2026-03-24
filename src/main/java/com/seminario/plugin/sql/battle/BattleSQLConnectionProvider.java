package com.seminario.plugin.sql.battle;

import java.sql.Connection;
import java.sql.SQLException;

import com.seminario.plugin.sql.context.SQLConnectionProvider;

/**
 * Adapts {@link BattleSQLDatabase} into the {@link SQLConnectionProvider} interface,
 * allowing the battle database to be registered in {@link com.seminario.plugin.sql.context.SQLContextRegistry}
 * and consumed by {@link com.seminario.plugin.sql.context.engine.GenericSQLValidationEngine}.
 *
 * The underlying H2 connection is shared (singleton per game session), so
 * {@link #shouldCloseConnectionAfterUse()} returns false to prevent the engine
 * from closing it between queries.
 */
public class BattleSQLConnectionProvider implements SQLConnectionProvider {

    private final BattleSQLDatabase database;

    public BattleSQLConnectionProvider(BattleSQLDatabase database) {
        this.database = database;
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection conn = database.getConnection();
        if (conn == null || conn.isClosed()) {
            throw new SQLException("[SQLBattle] La conexion a la base de datos no esta disponible.");
        }
        return conn;
    }

    @Override
    public boolean isConnected() {
        return database.isConnected();
    }

    /**
     * The battle connection is a shared singleton — callers must NOT close it after use.
     */
    @Override
    public boolean shouldCloseConnectionAfterUse() {
        return false;
    }

    /**
     * Delegates to {@link BattleSQLDatabase#close()}.
     * Called when the context is unregistered or the plugin shuts down.
     */
    @Override
    public void close() {
        database.close();
    }
}
