package com.rmbreeding.api.files;

import com.rmbreeding.api.config.StorageProperties;
import com.rmbreeding.api.shared.BusinessRuleException;
import com.rmbreeding.api.shared.ErrorCode;
import com.rmbreeding.api.shared.NotFoundException;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Stateless HMAC-signed share tokens: tenantId|linkId|expiresAt.
 */
@Component
public class ShareTokenService {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public ShareTokenService(StorageProperties properties) {
        this.key = new SecretKeySpec(properties.getShareSecret().getBytes(StandardCharsets.UTF_8),
                ALGORITHM);
    }

    public String issue(UUID tenantId, UUID linkId, Instant expiresAt) {
        String payload = tenantId + "|" + linkId + "|" + expiresAt.getEpochSecond();
        String encoded = base64(payload.getBytes(StandardCharsets.UTF_8));
        return encoded + "." + base64(sign(encoded));
    }

    public ShareTokenPayload verify(String token) {
        if (token == null || token.isBlank()) {
            throw new NotFoundException("Share link not found");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            throw new NotFoundException("Share link not found");
        }
        byte[] expected = sign(parts[0]);
        byte[] provided;
        try {
            provided = Base64.getUrlDecoder().decode(parts[1]);
        } catch (IllegalArgumentException ex) {
            throw new NotFoundException("Share link not found");
        }
        if (!MessageDigest.isEqual(expected, provided)) {
            throw new NotFoundException("Share link not found");
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String[] fields = raw.split("\\|");
            if (fields.length != 3) {
                throw new NotFoundException("Share link not found");
            }
            Instant expiresAt = Instant.ofEpochSecond(Long.parseLong(fields[2]));
            if (expiresAt.isBefore(Instant.now())) {
                throw new BusinessRuleException(ErrorCode.EXPIRED, "Share link has expired");
            }
            return new ShareTokenPayload(UUID.fromString(fields[0]), UUID.fromString(fields[1]),
                    expiresAt);
        } catch (IllegalArgumentException ex) {
            throw new NotFoundException("Share link not found");
        }
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign share token", ex);
        }
    }

    private static String base64(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    public record ShareTokenPayload(UUID tenantId, UUID linkId, Instant expiresAt) {
    }
}
