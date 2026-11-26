package com.github.svenfran.budgetapp.budgetappbackend.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Ein ausgestellter Refresh Token einer {@link AuthSession} (DIV-65). Gespeichert wird nur der HMAC-Hash
 * ({@code tokenHash}), nie der Klartext. Aktuell ist der Token, solange {@code rotatedAt} leer ist; bei der Rotation
 * zeigt {@code replacedBy} auf den direkten Nachfolger. Rotierte Tokens bleiben bis zum Löschen der Session erhalten
 * (Replay-Erkennung).
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private AuthSession session;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "rotated_at")
    private LocalDateTime rotatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaced_by_id")
    private RefreshToken replacedBy;

    public RefreshToken() {
    }

    public boolean isRotated() {
        return rotatedAt != null;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public AuthSession getSession() {
        return session;
    }

    public void setSession(AuthSession session) {
        this.session = session;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(LocalDateTime issuedAt) {
        this.issuedAt = issuedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getRotatedAt() {
        return rotatedAt;
    }

    public void setRotatedAt(LocalDateTime rotatedAt) {
        this.rotatedAt = rotatedAt;
    }

    public RefreshToken getReplacedBy() {
        return replacedBy;
    }

    public void setReplacedBy(RefreshToken replacedBy) {
        this.replacedBy = replacedBy;
    }

    /** Ohne {@code tokenHash} (FA8: keine Token-Daten in Logs). */
    @Override
    public String toString() {
        return "RefreshToken{" +
                "id=" + id +
                ", issuedAt=" + issuedAt +
                ", expiresAt=" + expiresAt +
                ", rotatedAt=" + rotatedAt +
                ", sessionId=" + (session == null ? null : session.getId()) +
                ", replacedById=" + (replacedBy == null ? null : replacedBy.getId()) +
                '}';
    }
}
