package com.seminario.plugin.sql.context;

import java.sql.Connection;
import java.sql.SQLException;

import com.seminario.plugin.database.SQLDatabase;

/**
 * Adapter to use the existing SQLDatabase in the new SQLContext architecture.
 */
public class ExistingSQLDatabaseConnectionProvider implements SQLConnectionProvider {

    private final SQLDatabase sqlDatabase;

    public ExistingSQLDatabaseConnectionProvider(SQLDatabase sqlDatabase) {
        this.sqlDatabase = sqlDatabase;
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (!isConnected()) {
            throw new SQLException("SQLDatabase is not connected");
        }
        return sqlDatabase.getConnection();
    }

    @Override
    public boolean isConnected() {
        return sqlDatabase != null && sqlDatabase.isConnected();
    }

    @Override
    public boolean shouldCloseConnectionAfterUse() {
        // Existing SQLDatabase uses a shared connection lifecycle.
        return false;
    }

    @Override
    public void close() {
        // Do not close here to avoid interfering with current SQLDungeon lifecycle.
    }
}
