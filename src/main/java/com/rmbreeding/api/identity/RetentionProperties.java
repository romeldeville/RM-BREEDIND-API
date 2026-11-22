package com.rmbreeding.api.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.retention")
public class RetentionProperties {

    /** Délai de grâce avant suppression définitive d'un tenant. */
    private Duration tenantDeletionGrace = Duration.ofDays(30);

    /** Conservation des jetons OTP / vérification / réinitialisation expirés. */
    private Duration tokenCleanupAfter = Duration.ofDays(7);

    public Duration getTenantDeletionGrace() {
        return tenantDeletionGrace;
    }

    public void setTenantDeletionGrace(Duration tenantDeletionGrace) {
        this.tenantDeletionGrace = tenantDeletionGrace;
    }

    public Duration getTokenCleanupAfter() {
        return tokenCleanupAfter;
    }

    public void setTokenCleanupAfter(Duration tokenCleanupAfter) {
        this.tokenCleanupAfter = tokenCleanupAfter;
    }
}
