package com.rmbreeding.api.config;

import com.rmbreeding.api.shared.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class TenantContextFilter extends OncePerRequestFilter {

    public static final String TENANT_HEADER = "X-Tenant-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(TENANT_HEADER);
        try {
            if (header != null && !header.isBlank()) {
                try {
                    UUID tenantId = UUID.fromString(header);
                    TenantContext.setTenantId(tenantId);
                    MDC.put("tenantId", tenantId.toString());
                } catch (IllegalArgumentException ignored) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid X-Tenant-Id header");
                    return;
                }
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.remove("tenantId");
        }
    }
}
