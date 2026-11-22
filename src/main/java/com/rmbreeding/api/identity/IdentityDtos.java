package com.rmbreeding.api.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class IdentityDtos {

    private IdentityDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 12, max = 128) String password,
            @NotBlank @Size(max = 160) String fullName,
            @Size(max = 32) String phone,
            @Pattern(regexp = "fr|en") String locale,
            boolean consent) {
    }

    public record VerifyEmailRequest(@NotBlank String token) {
    }

    public record OtpRequest(
            @NotBlank String destination,
            @NotBlank @Pattern(regexp = "EMAIL|SMS") String channel) {
    }

    public record OtpVerifyRequest(
            @NotBlank String destination,
            @NotBlank String code,
            String deviceId) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record MfaVerifyRequest(@NotBlank String challengeId, @NotBlank String code) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record PasswordForgotRequest(@NotBlank @Email String email) {
    }

    public record PasswordResetRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 12, max = 128) String password) {
    }

    public record TokenPair(String accessToken, String refreshToken, long expiresIn, String tokenType) {
    }

    public record TenantResponse(UUID id, String name, String slug, String status, String planCode,
            Instant trialEndsAt, Instant deleteRequestedAt, Instant deleteAfter) {
    }

    public record CreateTenantRequest(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Pattern(regexp = "^[a-z0-9-]+$") @Size(max = 80) String slug,
            @NotBlank @Size(min = 2, max = 2) String country,
            @NotBlank @Size(min = 3, max = 3) String currency) {
    }

    public record FarmResponse(UUID id, UUID tenantId, String name, String code, String country,
            String currency, String timezone) {
    }

    public record CreateFarmRequest(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(max = 40) String code,
            String country,
            String currency,
            String timezone) {
    }

    public record FarmPatchRequest(String name, String country, String currency, String timezone) {
    }

    public record MemberResponse(UUID id, UUID userId, String email, String fullName, String role,
            List<UUID> farmScope, String status) {
    }

    public record NotificationRecipient(UUID userId, String email, String fullName) {
    }

    public record InviteMemberRequest(
            @NotBlank @Email String email,
            @NotBlank @Pattern(regexp = "OWNER|MANAGER|WORKER|VET|ACCOUNTANT") String role,
            List<UUID> farmScope) {
    }

    public record AcceptInvitationRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 12, max = 128) String password,
            @NotBlank @Size(max = 160) String fullName) {
    }

    public record UpdateMemberRequest(
            @Pattern(regexp = "OWNER|MANAGER|WORKER|VET|ACCOUNTANT") String role,
            List<UUID> farmScope) {
    }

    public record MembershipInfo(UUID tenantId, String tenantName, String role, List<UUID> farmScope) {
    }

    public record MeResponse(UUID id, String email, String phone, String fullName, String locale, boolean platformAdmin,
            List<MembershipInfo> memberships) {
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 160) String fullName,
            @Size(max = 32) String phone,
            @Pattern(regexp = "fr|en") String locale) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 12, max = 128) String newPassword) {
    }

    public record QuotaResponse(String quotaKey, int used, Integer limit) {
    }

    public record ConsentResponse(String type, String version, Instant acceptedAt, String ip) {
    }

    public record AnonymizeRequest(
            @NotBlank @Size(min = 1, max = 128) String password,
            @NotBlank @Size(max = 40) String confirmation) {
    }

    public record SupportSessionRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public record MfaSetupResponse(String secret, String otpauthUri) {
    }

    public record MfaEnableRequest(@NotBlank String code) {
    }

    public record MfaChallengeResponse(String challengeId) {
    }

    public record InvitationResponse(UUID id, String email, String role, String status, Instant expiresAt) {
    }

    public record CreatedResource(UUID id) {
    }

    public record MessageResponse(String message) {
    }

    public record RegisterResponse(UUID userId, UUID tenantId) {
    }

    public record IdRequest(@NotNull UUID id) {
    }
}
