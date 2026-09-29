package com.flashsale.user.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String region;

    private String email;

    private String phone;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private UserRole role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Self-registration always creates a buyer that must verify its identifier. */
    public static User pendingBuyer(String region, String email, String phone, String passwordHash) {
        User user = new User();
        user.region = region;
        user.email = email;
        user.phone = phone;
        user.passwordHash = passwordHash;
        user.status = UserStatus.PENDING;
        user.role = UserRole.USER;
        user.createdAt = Instant.now();
        user.updatedAt = user.createdAt;
        return user;
    }

    /** Pre-verified account with a given role (seeding / future admin tooling). */
    public static User activeWithRole(String region, String email, String passwordHash, UserRole role) {
        User user = new User();
        user.region = region;
        user.email = email;
        user.passwordHash = passwordHash;
        user.status = UserStatus.ACTIVE;
        user.role = role;
        user.createdAt = Instant.now();
        user.updatedAt = user.createdAt;
        return user;
    }

    public void activate() {
        if (status == UserStatus.PENDING) {
            status = UserStatus.ACTIVE;
        }
    }

    public boolean isPending() {
        return status == UserStatus.PENDING;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
