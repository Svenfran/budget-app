package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.config.AuthProperties;
import com.github.svenfran.budgetapp.budgetappbackend.constants.SessionRevokeReason;
import com.github.svenfran.budgetapp.budgetappbackend.constants.UserEnum;
import com.github.svenfran.budgetapp.budgetappbackend.entity.AuthSession;
import com.github.svenfran.budgetapp.budgetappbackend.entity.RefreshToken;
import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
import com.github.svenfran.budgetapp.budgetappbackend.helper.RefreshTokenHasher;
import com.github.svenfran.budgetapp.budgetappbackend.repository.AuthSessionRepository;
import com.github.svenfran.budgetapp.budgetappbackend.repository.RefreshTokenRepository;
import com.github.svenfran.budgetapp.budgetappbackend.repository.UserRepository;
import com.github.svenfran.budgetapp.budgetappbackend.service.auth.IssuedSession;
import com.github.svenfran.budgetapp.budgetappbackend.service.auth.RefreshResult;
import com.github.svenfran.budgetapp.budgetappbackend.service.auth.RefreshResult.Reason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Refresh-Token-Sessions (DIV-65): Anlegen, Rotation mit Replay-Erkennung, Widerruf und Aufräumen.
 *
 * <p><b>Sperren:</b> Feste Reihenfolge Nutzerzeile → Session-Zeilen. {@link #createSession}, {@link #revokeAllSessions}
 * und {@link #deleteAllForUser} sperren zuerst den Nutzer (ein Aufrufer, der ihn schon gesperrt hat, sperrt ihn
 * erneut ohne Wirkung); {@link #refresh} und die Einzel-Widerrufe sperren nur die Session. So gibt es keinen Deadlock,
 * und ein Refresh, der während eines Widerrufs wartet, sieht danach {@code revoked_at}.
 *
 * <p><b>Refresh</b> (READ COMMITTED, Postgres-Default): erst nur die Ids zum Token-Hash lesen (keine Entity im
 * Persistence Context), dann die Session sperren, dann Token und Nachfolger frisch laden – so sieht ein paralleler
 * Request nach Erhalt der Sperre den Stand nach dem Commit des ersten. Prüfreihenfolge siehe {@link #refresh}.
 *
 * <p>Logs enthalten nur Ereignis, Session-/Nutzer-Id und Grund – nie Token, Hash oder E-Mail.
 */
@Service
public class AuthSessionService {

    private static final Logger LOG = LoggerFactory.getLogger(AuthSessionService.class);

    /** Beendete Sessions bleiben so lange stehen (Nachvollziehbarkeit), bevor das Aufräumen sie löscht. */
    static final Duration CLEANUP_RETENTION = Duration.ofDays(7);

    private final AuthSessionRepository sessionRepository;
    private final RefreshTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final RefreshTokenHasher hasher;
    private final AuthProperties properties;
    private final JwtService jwtService;
    private final Clock clock;

    public AuthSessionService(AuthSessionRepository sessionRepository, RefreshTokenRepository tokenRepository,
                              UserRepository userRepository, RefreshTokenHasher hasher, AuthProperties properties,
                              JwtService jwtService, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.hasher = hasher;
        this.properties = properties;
        this.jwtService = jwtService;
        this.clock = clock;
    }

    /**
     * Legt unter Nutzersperre eine neue Session mit erstem Refresh Token und Access Token an. Nicht widerrufene Sessions desselben
     * Nutzers auf derselben DeviceId werden widerrufen ({@code RELOGIN}) – höchstens eine aktive Session je Gerät.
     */
    @Transactional
    public IssuedSession createSession(User user, String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId must not be blank");
        }
        var lockedUser = lockUser(user.getId());
        var now = now();
        for (var previous : sessionRepository.lockNotRevokedByUserIdAndDeviceId(lockedUser.getId(), deviceId)) {
            previous.revoke(now, SessionRevokeReason.RELOGIN);
            LOG.info("event=session_revoked sessionId={} userId={} reason={}", previous.getId(), lockedUser.getId(),
                    SessionRevokeReason.RELOGIN);
        }

        var session = new AuthSession();
        session.setUser(lockedUser);
        session.setDeviceId(deviceId);
        session.setCreatedAt(now);
        session.setAbsoluteExpiresAt(now.plus(properties.getSessionMaxLifetime()));
        sessionRepository.save(session);

        var plain = hasher.newToken();
        var token = newToken(session, plain, now);
        tokenRepository.save(token);
        LOG.info("event=session_created sessionId={} userId={}", session.getId(), lockedUser.getId());
        return issued(session, lockedUser, plain, token);
    }

    /**
     * Rotiert einen Refresh Token. Prüfreihenfolge nach der Session-Sperre:
     * <ol>
     *   <li>Session widerrufen oder absolute Grenze erreicht → abgelehnt.</li>
     *   <li>DeviceId passt nicht → abgelehnt, <b>ohne</b> Widerruf (die DeviceId ist kein Geheimnis).</li>
     *   <li>Nutzer gelöscht → abgelehnt.</li>
     *   <li>Token bereits rotiert (unabhängig vom eigenen Ablauf): unmittelbarer Vorgänger des noch unbenutzten
     *       aktuellen Tokens und innerhalb des Kulanzfensters → abgelehnt ohne Widerruf; sonst Replay → Session
     *       widerrufen ({@code REUSE_DETECTED}).</li>
     *   <li>Aktueller Token nach Leerlauf abgelaufen → abgelehnt.</li>
     *   <li>Sonst rotieren: neuer Token gilt {@code refreshTokenValidity}, höchstens bis zur absoluten Grenze; dazu ein
     *       neuer Access Token derselben Session.</li>
     * </ol>
     * Wirft nicht, damit ein Widerruf committet wird (siehe {@link RefreshResult}).
     */
    @Transactional
    public RefreshResult refresh(String refreshToken, String deviceId) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return reject(Reason.UNKNOWN_TOKEN, null);
        }
        var ref = tokenRepository.findRefByTokenHash(hasher.hash(refreshToken));
        if (ref.isEmpty()) {
            return reject(Reason.UNKNOWN_TOKEN, null);
        }
        var session = sessionRepository.findByIdForUpdate(ref.get().getSessionId()).orElse(null);
        var presented = session == null ? null : tokenRepository.findById(ref.get().getTokenId()).orElse(null);
        if (presented == null) {
            // zwischen Lookup und Sperre gelöscht (Konto gelöscht, Aufräumen)
            return reject(Reason.UNKNOWN_TOKEN, null);
        }

        var now = now();
        if (session.getRevokedAt() != null) {
            return reject(Reason.SESSION_REVOKED, session);
        }
        if (!session.getAbsoluteExpiresAt().isAfter(now)) {
            return reject(Reason.SESSION_EXPIRED, session);
        }
        if (deviceId == null || !deviceId.equals(session.getDeviceId())) {
            return reject(Reason.DEVICE_MISMATCH, session);
        }
        if (UserEnum.USER_DELETED.getName().equals(session.getUser().getName())) {
            return reject(Reason.USER_DELETED, session);
        }
        if (presented.isRotated()) {
            var successor = presented.getReplacedBy();
            var withinGrace = successor != null && !successor.isRotated()
                    && presented.getRotatedAt().isAfter(now.minus(properties.getRefreshReuseGrace()));
            if (withinGrace) {
                LOG.info("event=refresh_token_reuse_within_grace sessionId={}", session.getId());
                return new RefreshResult.Rejected(Reason.REUSE_WITHIN_GRACE);
            }
            session.revoke(now, SessionRevokeReason.REUSE_DETECTED);
            LOG.warn("event=refresh_token_reuse_detected sessionId={} userId={}", session.getId(),
                    session.getUser().getId());
            return new RefreshResult.Rejected(Reason.REUSE_DETECTED);
        }
        if (!presented.getExpiresAt().isAfter(now)) {
            return reject(Reason.TOKEN_EXPIRED, session);
        }

        var plain = hasher.newToken();
        var next = newToken(session, plain, now);
        tokenRepository.save(next);
        presented.setRotatedAt(now);
        presented.setReplacedBy(next);
        session.setLastUsedAt(now);
        LOG.debug("event=refresh_token_rotated sessionId={}", session.getId());
        return new RefreshResult.Rotated(issued(session, session.getUser(), plain, next));
    }

    /**
     * Widerruft die Session des Refresh Tokens (auch eines rotierten), falls sie noch nicht widerrufen ist.
     * Liefert die Id der Session, wenn der Token bekannt ist.
     */
    @Transactional
    public Optional<Long> revokeByRefreshToken(String refreshToken, SessionRevokeReason reason) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return Optional.empty();
        }
        return tokenRepository.findRefByTokenHash(hasher.hash(refreshToken))
                .map(ref -> {
                    revokeBySessionId(ref.getSessionId(), reason);
                    return ref.getSessionId();
                });
    }

    /** Widerruft die Session unter Session-Sperre; {@code true}, wenn sie dadurch widerrufen wurde. */
    @Transactional
    public boolean revokeBySessionId(Long sessionId, SessionRevokeReason reason) {
        if (sessionId == null) {
            return false;
        }
        return sessionRepository.findByIdForUpdate(sessionId)
                .filter(session -> session.revoke(now(), reason))
                .map(session -> {
                    LOG.info("event=session_revoked sessionId={} userId={} reason={}", session.getId(),
                            session.getUser().getId(), reason);
                    return true;
                })
                .orElse(false);
    }

    /** Widerruft unter Nutzersperre alle nicht widerrufenen Sessions des Nutzers; liefert ihre Anzahl. */
    @Transactional
    public int revokeAllSessions(User user, SessionRevokeReason reason) {
        var lockedUser = lockUser(user.getId());
        var now = now();
        var sessions = sessionRepository.lockNotRevokedByUserId(lockedUser.getId());
        sessions.forEach(session -> session.revoke(now, reason));
        LOG.info("event=sessions_revoked_all userId={} reason={} count={}", lockedUser.getId(), reason, sessions.size());
        return sessions.size();
    }

    /** Löscht unter Nutzersperre alle Sessions und Refresh Tokens des Nutzers (Konto löschen). */
    @Transactional
    public int deleteAllForUser(User user) {
        var lockedUser = lockUser(user.getId());
        var deleted = sessionRepository.deleteByUserId(lockedUser.getId());
        LOG.info("event=sessions_deleted_all userId={} count={}", lockedUser.getId(), deleted);
        return deleted;
    }

    /**
     * Löscht Sessions, die seit mehr als {@link #CLEANUP_RETENTION} beendet sind (widerrufen, absolut abgelaufen oder
     * aktueller Token im Leerlauf abgelaufen), samt Refresh Tokens. Rotierte Tokens aktiver Sessions bleiben.
     */
    @Transactional
    public int deleteEndedSessions() {
        var deleted = sessionRepository.deleteEndedBefore(now().minus(CLEANUP_RETENTION));
        LOG.info("event=sessions_cleanup count={}", deleted);
        return deleted;
    }

    private User lockUser(Long userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException("User " + userId + " not found"));
    }

    private RefreshToken newToken(AuthSession session, String plain, LocalDateTime now) {
        var token = new RefreshToken();
        token.setSession(session);
        token.setTokenHash(hasher.hash(plain));
        token.setIssuedAt(now);
        var idleExpiry = now.plus(properties.getRefreshTokenValidity());
        token.setExpiresAt(idleExpiry.isBefore(session.getAbsoluteExpiresAt()) ? idleExpiry : session.getAbsoluteExpiresAt());
        return token;
    }

    private IssuedSession issued(AuthSession session, User user, String plainRefreshToken, RefreshToken token) {
        var accessToken = jwtService.generateToken(user, session.getDeviceId(), session.getId());
        return new IssuedSession(session.getId(), user.getId(), user.getName(), user.getEmail(), accessToken,
                jwtService.extractExpiration(accessToken).toInstant(), plainRefreshToken, token.getExpiresAt(),
                session.getAbsoluteExpiresAt());
    }

    private static RefreshResult reject(Reason reason, AuthSession session) {
        LOG.info("event=refresh_rejected reason={} sessionId={}", reason, session == null ? null : session.getId());
        return new RefreshResult.Rejected(reason);
    }

    private LocalDateTime now() {
        // UTC: keine Sprünge durch Sommer-/Winterzeit
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
