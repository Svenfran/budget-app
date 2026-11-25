package com.github.svenfran.budgetapp.budgetappbackend.service.auth;

import java.time.LocalDateTime;

/**
 * Ergebnis einer Session-Erstellung oder Rotation (DIV-65): der neue Refresh Token im Klartext (nur für die Response)
 * und die Ablaufzeiten in UTC. {@link #toString()} enthält den Token nicht.
 */
public record IssuedSession(Long sessionId, Long userId, String refreshToken, LocalDateTime refreshTokenExpiresAt,
                            LocalDateTime sessionExpiresAt) {

    @Override
    public String toString() {
        return "IssuedSession{sessionId=" + sessionId + ", userId=" + userId
                + ", refreshTokenExpiresAt=" + refreshTokenExpiresAt + ", sessionExpiresAt=" + sessionExpiresAt + '}';
    }
}
