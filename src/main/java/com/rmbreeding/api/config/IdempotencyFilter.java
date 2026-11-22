package com.rmbreeding.api.config;

import com.rmbreeding.api.shared.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 40)
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String HEADER = "Idempotency-Key";
    private static final Set<String> MUTATING = Set.of("POST", "PUT", "PATCH");

    private final IdempotencyService service;

    public IdempotencyFilter(IdempotencyService service) {
        this.service = service;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !MUTATING.contains(request.getMethod()) || request.getHeader(HEADER) == null
                || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader(HEADER);
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request, 8192);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        chain.doFilter(requestWrapper, responseWrapper);

        String requestHash = IdempotencyService.hash(requestWrapper.getContentAsByteArray());
        String responseBody = new String(responseWrapper.getContentAsByteArray(), StandardCharsets.UTF_8);
        String contentType = responseWrapper.getContentType();

        Optional<IdempotencyService.StoredResponse> stored = service.find(key, requestHash);
        if (stored.isEmpty()) {
            service.start(key, requestHash);
        } else if (!stored.get().requestHash().equals(requestHash)) {
            responseWrapper.resetBuffer();
            responseWrapper.setStatus(409);
            responseWrapper.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            responseWrapper.getWriter().write("{\"code\":\"" + ErrorCode.IDEMPOTENCY_KEY_REUSED
                    + "\",\"status\":409,\"title\":\"Idempotency key reused with a different payload\"}");
            responseWrapper.copyBodyToResponse();
            return;
        }

        if (responseWrapper.getStatus() < 500) {
            service.complete(key, responseWrapper.getStatus(), responseBody, contentType);
        }
        responseWrapper.copyBodyToResponse();
    }
}
