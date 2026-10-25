package com.github.svenfran.budgetapp.budgetappbackend.config;

import com.github.svenfran.budgetapp.budgetappbackend.repository.TokenRepository;
import com.github.svenfran.budgetapp.budgetappbackend.service.JwtService;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    // not named "logger": that would hide GenericFilterBean.logger
    private final Logger LOG = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;

    private final UserDetailsService userDetailsService;

    private final TokenRepository tokenRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService,
                                   TokenRepository tokenRepository) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.tokenRepository = tokenRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith(JwtService.BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            authenticateIfValid(request, authHeader.substring(JwtService.BEARER_PREFIX.length()));
        } catch (JwtException | IllegalArgumentException e) {
            // expired, tampered or malformed JWT: continue unauthenticated
            // only the type is logged: the exception message may contain claims (e.g. the e-mail)
            LOG.debug("JWT rejected: {}", e.getClass().getSimpleName());
        }
        filterChain.doFilter(request, response);
    }

    private void authenticateIfValid(HttpServletRequest request, String jwt) {
        final String deviceIdFromRequest = request.getHeader("deviceId");
        final String userEmail = jwtService.extractUsername(jwt);
        final String deviceIdFromToken = jwtService.extractDeviceId(jwt);

        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails;
            try {
                userDetails = this.userDetailsService.loadUserByUsername(userEmail);
            } catch (UsernameNotFoundException e) {
                // e.g. token of a deleted (anonymized) user: continue unauthenticated
                LOG.debug("JWT rejected: subject not found");
                return;
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
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );
                authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );
                SecurityContextHolder.getContext().setAuthentication(authToken);
            } else {
                LOG.debug("JWT rejected: token not stored, expired, revoked, deviceId mismatch or invalid");
            }
        }
    }
}
