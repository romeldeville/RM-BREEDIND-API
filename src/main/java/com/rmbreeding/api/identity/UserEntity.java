package com.rmbreeding.api.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
class UserEntity {

    @Id
    UUID id;

    @Column(columnDefinition = "citext")
    String email;

    String phone;

    String passwordHash;

    String pinHash;

    Instant emailVerifiedAt;

    Instant phoneVerifiedAt;

    String status;

    String locale;

    boolean mfaEnabled;

    String platformRole;

    String fullName;

    int failedLoginAttempts;

    Instant lockedUntil;

    Instant createdAt;

    Instant updatedAt;

    Instant archivedAt;

    boolean isPlatformAdmin() {
        return "PLATFORM_ADMIN".equals(platformRole);
    }

    boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }
}
