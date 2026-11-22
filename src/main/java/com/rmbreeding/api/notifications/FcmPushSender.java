package com.rmbreeding.api.notifications;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/**
 * Minimal FCM HTTP v1 client. It exchanges a service-account JWT for a Google
 * OAuth access token and posts the message to the Firebase endpoint, which
 * avoids pulling the Firebase Admin SDK into the application.
 */
public class FcmPushSender implements PushSender {

    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    private static final String GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer";

    private final NotificationProperties.Push properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String clientEmail;
    private final PrivateKey privateKey;

    private String accessToken;
    private Instant accessTokenExpiresAt = Instant.EPOCH;

    public FcmPushSender(NotificationProperties properties, ObjectMapper objectMapper) {
        this(properties.getPush(), objectMapper);
    }

    public FcmPushSender(NotificationProperties.Push properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
        JsonNode account = readServiceAccount(properties.getServiceAccountJson(), objectMapper);
        this.clientEmail = account.path("client_email").asText();
        this.privateKey = parsePrivateKey(account.path("private_key").asText());
    }

    @Override
    public void send(String token, String title, String body, Map<String, String> data) {
        String bearer = accessToken();
        ObjectNode message = objectMapper.createObjectNode();
        ObjectNode target = message.putObject("message");
        target.put("token", token);
        ObjectNode notification = target.putObject("notification");
        notification.put("title", title);
        notification.put("body", body == null ? "" : body);
        if (data != null && !data.isEmpty()) {
            ObjectNode dataNode = target.putObject("data");
            data.forEach(dataNode::put);
        }
        ObjectNode android = target.putObject("android");
        android.put("priority", "high");
        ObjectNode webpush = target.putObject("webpush");
        webpush.putObject("notification").put("icon", "/favicon.ico");

        String endpoint = "https://fcm.googleapis.com/v1/projects/" + properties.getProjectId()
                + "/messages:send";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(properties.getRequestTimeout())
                    .header("Authorization", "Bearer " + bearer)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(message)))
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new PushDeliveryException("FCM responded with HTTP " + response.statusCode()
                        + ": " + truncate(response.body()));
            }
        } catch (PushDeliveryException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new PushDeliveryException("FCM request interrupted", ex);
        } catch (Exception ex) {
            throw new PushDeliveryException("FCM request failed: " + ex.getMessage(), ex);
        }
    }

    private synchronized String accessToken() {
        Instant now = Instant.now();
        if (accessToken != null && now.isBefore(accessTokenExpiresAt.minusSeconds(60))) {
            return accessToken;
        }
        String assertion = signedAssertion(now);
        String form = "grant_type=" + URLEncoder.encode(GRANT_TYPE, StandardCharsets.UTF_8)
                + "&assertion=" + URLEncoder.encode(assertion, StandardCharsets.UTF_8);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getTokenUri()))
                    .timeout(properties.getRequestTimeout())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new PushDeliveryException("OAuth token request failed with HTTP "
                        + response.statusCode() + ": " + truncate(response.body()));
            }
            JsonNode payload = objectMapper.readTree(response.body());
            accessToken = payload.path("access_token").asText();
            long expiresIn = payload.path("expires_in").asLong(3600);
            accessTokenExpiresAt = now.plusSeconds(expiresIn);
            return accessToken;
        } catch (PushDeliveryException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new PushDeliveryException("OAuth token request interrupted", ex);
        } catch (Exception ex) {
            throw new PushDeliveryException("OAuth token request failed: " + ex.getMessage(), ex);
        }
    }

    private String signedAssertion(Instant now) {
        try {
            long issuedAt = now.getEpochSecond();
            String header = base64Url(objectMapper.writeValueAsBytes(Map.of("alg", "RS256", "typ", "JWT")));
            String claims = base64Url(objectMapper.writeValueAsBytes(Map.of(
                    "iss", clientEmail,
                    "scope", SCOPE,
                    "aud", properties.getTokenUri(),
                    "iat", issuedAt,
                    "exp", issuedAt + 3600)));
            String signingInput = header + "." + claims;
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + base64Url(signature.sign());
        } catch (Exception ex) {
            throw new PushDeliveryException("Unable to sign service account assertion: " + ex.getMessage(), ex);
        }
    }

    private static JsonNode readServiceAccount(String json, ObjectMapper objectMapper) {
        if (json == null || json.isBlank()) {
            throw new PushDeliveryException("FCM service account JSON is missing");
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new PushDeliveryException("FCM service account JSON is invalid", ex);
        }
    }

    private static PrivateKey parsePrivateKey(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new PushDeliveryException("FCM service account private key is missing");
        }
        String normalized = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        try {
            byte[] keyBytes = Base64.getDecoder().decode(normalized);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (Exception ex) {
            throw new PushDeliveryException("FCM service account private key is invalid", ex);
        }
    }

    private static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 300 ? value : value.substring(0, 300);
    }
}
