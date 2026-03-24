package com.seminario.plugin.sql.context;

import java.util.Objects;

import com.seminario.plugin.sql.context.challenge.SQLChallengeRepository;

/**
 * Default immutable SQLContext implementation.
 */
public class DefaultSQLContext implements SQLContext {

    private final String id;
    private final SQLConnectionProvider connectionProvider;
    private final SQLSchemaProvider schemaProvider;
    private final SQLQueryPolicy queryPolicy;
    private final SQLChallengeRepository challengeRepository;

    public DefaultSQLContext(
        String id,
        SQLConnectionProvider connectionProvider,
        SQLSchemaProvider schemaProvider,
        SQLQueryPolicy queryPolicy,
        SQLChallengeRepository challengeRepository
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");
        this.schemaProvider = Objects.requireNonNull(schemaProvider, "schemaProvider");
        this.queryPolicy = Objects.requireNonNull(queryPolicy, "queryPolicy");
        this.challengeRepository = challengeRepository;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public SQLConnectionProvider getConnectionProvider() {
        return connectionProvider;
    }

    @Override
    public SQLSchemaProvider getSchemaProvider() {
        return schemaProvider;
    }

    @Override
    public SQLQueryPolicy getQueryPolicy() {
        return queryPolicy;
    }

    @Override
    public SQLChallengeRepository getChallengeRepository() {
        return challengeRepository;
    }
}
