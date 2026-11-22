package com.rmbreeding.api.config;

import com.rmbreeding.api.identity.AuthContext;
import com.rmbreeding.api.shared.TenantContext;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

@Component
public class HibernateTenantConfig
        implements MultiTenantConnectionProvider<String>, CurrentTenantIdentifierResolver<String>,
        HibernatePropertiesCustomizer {

    private static final String NO_TENANT = "00000000-0000-0000-0000-000000000000";
    private static final String SET_TENANT =
            "select set_config('app.tenant_id', ?, false), set_config('app.auth_user_id', ?, false)";

    private final DataSource dataSource;

    public HibernateTenantConfig(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        Connection connection = dataSource.getConnection();
        applyTenant(connection, tenantIdentifier);
        return connection;
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        try {
            applyTenant(connection, NO_TENANT);
        } finally {
            connection.close();
        }
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return MultiTenantConnectionProvider.class.equals(unwrapType)
                || DataSource.class.isAssignableFrom(unwrapType);
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        if (DataSource.class.isAssignableFrom(unwrapType)) {
            return unwrapType.cast(dataSource);
        }
        throw new UnsupportedOperationException("Unsupported unwrap type: " + unwrapType);
    }

    @Override
    public String resolveCurrentTenantIdentifier() {
        UUID tenantId = TenantContext.getTenantId();
        return tenantId != null ? tenantId.toString() : NO_TENANT;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put("hibernate.multiTenancy", "DATABASE");
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, this);
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }

    private void applyTenant(Connection connection, String tenantIdentifier) throws SQLException {
        String value = tenantIdentifier == null || tenantIdentifier.isBlank() ? NO_TENANT : tenantIdentifier;
        String userId = AuthContext.userId() == null ? "" : AuthContext.userId().toString();
        try (PreparedStatement statement = connection.prepareStatement(SET_TENANT)) {
            statement.setString(1, value);
            statement.setString(2, userId);
            statement.execute();
        }
    }
}
