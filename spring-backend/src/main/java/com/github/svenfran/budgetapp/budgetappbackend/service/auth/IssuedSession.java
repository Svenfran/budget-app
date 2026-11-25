package com.github.svenfran.budgetapp.budgetappbackend.service.auth;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Ergebnis einer Session-Erstellung oder Rotation (DIV-65): neues Token-Paar im Klartext (nur für die Response),
 * Profildaten für die Response und die Ablaufzeiten (Refresh/Session in UTC). {@link #toString()} enthält weder
 * Tokens noch E-Mail.
 */
public record IssuedSession(Long sessionId, Long userId, String userName, String userEmail, String accessToken,
                            Instant accessTokenExpiresAt, String refreshToken, LocalDateTime refreshTokenExpiresAt,
                            LocalDateTime sessionExpiresAt) {

    @Override
    public String toString() {
        return "IssuedSession{sessionId=" + sessionId + ", userId=" + userId
                + ", accessTokenExpiresAt=" + accessTokenExpiresAt + ", refreshTokenExpiresAt=" + refreshTokenExpiresAt
                + ", sessionExpiresAt=" + sessionExpiresAt + '}';
    }
}
