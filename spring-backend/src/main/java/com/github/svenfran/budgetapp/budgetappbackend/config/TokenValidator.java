package com.github.svenfran.budgetapp.budgetappbackend.config;

import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
import com.github.svenfran.budgetapp.budgetappbackend.repository.AuthSessionRepository;
import com.github.svenfran.budgetapp.budgetappbackend.service.JwtService;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Validates an access token (JWT without the "Bearer " prefix) together with the deviceId sent by the client:
 * signature, expiry, user exists, and the session from the {@code sid} claim (DIV-65) exists, belongs to that user,
 * is neither revoked nor past its absolute lifetime, and was created for the same deviceId as the JWT claim and the
 * request header. JWTs without {@code sid} (issued before DIV-65) are rejected. The idle expiry of the refresh token
 * is deliberately not checked: an access token stays valid until its own expiry.
 * Shared by the REST filter ({@link JwtAuthenticationFilter}) and the STOMP CONNECT check.
 */
@Component
public class TokenValidator {

    private static final Logger LOG = LoggerFactory.getLogger(TokenValidator.class);

    private final JwtService jwtService;

    private final UserDetailsService userDetailsService;

    private final AuthSessionRepository authSessionRepository;

    private final Clock clock;

    public TokenValidator(JwtService jwtService, UserDetailsService userDetailsService,
                          AuthSessionRepository authSessionRepository, Clock clock) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.authSessionRepository = authSessionRepository;
        this.clock = clock;
    }

    /** Returns the user of a valid token, or empty if the token is invalid for any reason. */
    public Optional<UserDetails> validate(String jwt, String deviceIdFromRequest) {
        try {
            return validateOrThrow(jwt, deviceIdFromRequest);
        } catch (JwtException | IllegalArgumentException e) {
            // expired, tampered or malformed JWT
            // only the type is logged: the exception message may contain claims (e.g. the e-mail)
            LOG.debug("JWT rejected: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private Optional<UserDetails> validateOrThrow(String jwt, String deviceIdFromRequest) {
        final String userEmail = jwtService.extractUsername(jwt);
        final String deviceIdFromToken = jwtService.extractDeviceId(jwt);
        final Long sessionId = jwtService.extractSessionId(jwt);

        if (userEmail == null) {
            return Optional.empty();
        }
        if (sessionId == null) {
            LOG.debug("JWT rejected: no session id (issued before refresh-token sessions)");
            return Optional.empty();
        }
        UserDetails userDetails;
        try {
            userDetails = this.userDetailsService.loadUserByUsername(userEmail);
        } catch (UsernameNotFoundException e) {
            // e.g. token of a deleted (anonymized) user
            LOG.debug("JWT rejected: subject not found");
            return Optional.empty();
        }
        var now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        // PK lookup without lock; getUser().getId() reads the id from the proxy without loading the user
        var isSessionActive = authSessionRepository.findById(sessionId)
                .map(session ->
                        session.isActiveAt(now)
                        && userDetails instanceof User user
                        && user.getId().equals(session.getUser().getId())
                        // a missing deviceId never matches, not even null == null
                        && deviceIdFromRequest != null
                        && deviceIdFromRequest.equals(session.getDeviceId())
                        && deviceIdFromRequest.equals(deviceIdFromToken))
                .orElse(false);
        if (jwtService.isTokenValid(jwt, userDetails) && isSessionActive) {
            return Optional.of(userDetails);
        }
        LOG.debug("JWT rejected: session {} unknown, revoked, expired, of another user, deviceId mismatch or JWT invalid",
                sessionId);
        return Optional.empty();
    }
}
