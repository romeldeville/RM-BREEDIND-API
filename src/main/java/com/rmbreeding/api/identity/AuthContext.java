package com.rmbreeding.api.identity;

import java.util.UUID;

public final class AuthContext {

    private static final ThreadLocal<AuthPrincipal> CURRENT = new ThreadLocal<>();

    private AuthContext() {
    }

    public static AuthPrincipal get() {
        return CURRENT.get();
    }

    public static UUID userId() {
        AuthPrincipal principal = CURRENT.get();
        return principal == null ? null : principal.userId();
    }

    public static UUID supportSessionId() {
        AuthPrincipal principal = CURRENT.get();
        return principal == null ? null : principal.supportSessionId();
    }

    public static void set(AuthPrincipal principal) {
        CURRENT.set(principal);
    }

    public static void clear() {
        CURRENT.remove();
    }
}
