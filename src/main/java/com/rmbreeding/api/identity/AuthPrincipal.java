package com.rmbreeding.api.identity;

import java.util.List;
import java.util.UUID;

public record AuthPrincipal(UUID userId, UUID tenantId, List<String> roles, boolean platformAdmin,
        UUID supportSessionId) {

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
