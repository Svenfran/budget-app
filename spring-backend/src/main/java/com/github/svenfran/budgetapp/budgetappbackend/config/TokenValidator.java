package com.github.svenfran.budgetapp.budgetappbackend.config;

import com.github.svenfran.budgetapp.budgetappbackend.repository.TokenRepository;
import com.github.svenfran.budgetapp.budgetappbackend.service.JwtService;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Validates a JWT (without the "Bearer " prefix) together with the deviceId sent by the client: signature, expiry,
 * stored and not revoked, and the deviceId must match both the stored token and the JWT claim.
 * Shared by the REST filter ({@link JwtAuthenticationFilter}) and the STOMP CONNECT check.
 */
@Component
public class TokenValidator {

    private static final Logger LOG = LoggerFactory.getLogger(TokenValidator.class);

    private final JwtService jwtService;

    private final UserDetailsService userDetailsService;

    private final TokenRepository tokenRepository;

    public TokenValidator(JwtService jwtService, UserDetailsService userDetailsService,
                          TokenRepository tokenRepository) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.tokenRepository = tokenRepository;
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

        if (userEmail == null) {
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
        var isTokenValid = tokenRepository.findByToken(jwt)
                .map(token ->
                        !token.isExpired()
                        && !token.isRevoked()
                        // a missing deviceId never matches, not even null == null
                        && deviceIdFromRequest != null
                        && deviceIdFromRequest.equals(token.getDeviceId())
                        && deviceIdFromRequest.equals(deviceIdFromToken))
                .orElse(false);
        if (jwtService.isTokenValid(jwt, userDetails) && isTokenValid) {
            return Optional.of(userDetails);
        }
        LOG.debug("JWT rejected: token not stored, expired, revoked, deviceId mismatch or invalid");
        return Optional.empty();
    }
}
