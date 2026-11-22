package com.rmbreeding.api.shared;

import java.util.UUID;

public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static UUID getTenantId() {
        return CURRENT.get();
    }

    public static void setTenantId(UUID tenantId) {
        CURRENT.set(tenantId);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static UUID requireTenantId() {
        UUID tenantId = CURRENT.get();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        return tenantId;
    }
}
