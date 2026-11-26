package com.github.svenfran.budgetapp.budgetappbackend.entity;

import com.github.svenfran.budgetapp.budgetappbackend.constants.SessionRevokeReason;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Anmeldung eines Nutzers auf einem Gerät (DIV-65) – die „Token-Familie“ aller Refresh Tokens, die aus einem Login
 * hervorgehen. Aktiv, solange {@code revokedAt} leer und {@code absoluteExpiresAt} nicht erreicht ist; Rotationen
 * verlängern die absolute Grenze nicht. Zeiten in UTC.
 */
@Entity
@Table(name = "auth_session")
public class AuthSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "absolute_expires_at", nullable = false)
    private LocalDateTime absoluteExpiresAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revoke_reason")
    private SessionRevokeReason revokeReason;

    public AuthSession() {
    }

    /** Aktiv = nicht widerrufen und die absolute Höchstdauer ist zum Zeitpunkt {@code now} noch nicht erreicht. */
    public boolean isActiveAt(LocalDateTime now) {
        return revokedAt == null && absoluteExpiresAt.isAfter(now);
    }

    /** Widerruft die Session, falls sie es noch nicht ist; liefert {@code true}, wenn sich etwas geändert hat. */
    public boolean revoke(LocalDateTime now, SessionRevokeReason reason) {
        if (revokedAt != null) {
            return false;
        }
        revokedAt = now;
        revokeReason = reason;
        return true;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getAbsoluteExpiresAt() {
        return absoluteExpiresAt;
    }

    public void setAbsoluteExpiresAt(LocalDateTime absoluteExpiresAt) {
        this.absoluteExpiresAt = absoluteExpiresAt;
    }

    public LocalDateTime getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(LocalDateTime lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(LocalDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    public SessionRevokeReason getRevokeReason() {
        return revokeReason;
    }

    public void setRevokeReason(SessionRevokeReason revokeReason) {
        this.revokeReason = revokeReason;
    }

    @Override
    public String toString() {
        return "AuthSession{" +
                "id=" + id +
                ", createdAt=" + createdAt +
                ", absoluteExpiresAt=" + absoluteExpiresAt +
                ", lastUsedAt=" + lastUsedAt +
                ", revokedAt=" + revokedAt +
                ", revokeReason=" + revokeReason +
                ", userId=" + (user == null ? null : user.getId()) +
                '}';
    }
}
