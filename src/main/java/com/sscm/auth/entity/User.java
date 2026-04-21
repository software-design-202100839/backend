package com.sscm.auth.entity;

import com.sscm.common.crypto.EncryptedStringConverter;
import com.sscm.common.crypto.EncryptionUtil;
import jakarta.persistence.*;
import lombok.*;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "TEXT")
    private String email;

    @Column(name = "email_hash", unique = true, length = 64)
    private String emailHash;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String name;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "TEXT")
    private String phone;

    @Column(name = "phone_hash", unique = true, length = 64)
    private String phoneHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "is_activated", nullable = false)
    @Builder.Default
    private Boolean isActivated = false;

    @Column(name = "failed_login_count", nullable = false)
    @Builder.Default
    private Integer failedLoginCount = 0;

    @Column(name = "login_locked_until")
    private Instant loginLockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    @PreUpdate
    void computeHashes() {
        if (this.email != null) {
            this.emailHash = EncryptionUtil.sha256(this.email);
        }
        if (this.phone != null && this.phoneHash == null) {
            this.phoneHash = EncryptionUtil.sha256(this.phone);
        }
    }

    public boolean isLocked() {
        return loginLockedUntil != null && Instant.now().isBefore(loginLockedUntil);
    }

    public void recordLoginFailure() {
        this.failedLoginCount++;
        if (this.failedLoginCount >= 5) {
            this.loginLockedUntil = Instant.now().plus(Duration.ofMinutes(30));
        }
        this.updatedAt = LocalDateTime.now();
    }

    public void recordLoginSuccess() {
        this.failedLoginCount = 0;
        this.loginLockedUntil = null;
        this.updatedAt = LocalDateTime.now();
    }

    public void activate(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.isActivated = true;
        this.updatedAt = LocalDateTime.now();
    }

    public void resetPassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.updatedAt = LocalDateTime.now();
    }

    public void setPhoneHash(String phoneHash) {
        this.phoneHash = phoneHash;
    }
}
