package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.config.AuthProperties;
import com.github.svenfran.budgetapp.budgetappbackend.helper.EmailUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.time.Clock;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Access Tokens (JWT, HS256). Claims: {@code sub} (E-Mail), {@code deviceId}, {@code sid} (Id der Auth-Session,
 * DIV-65), {@code iat}, {@code exp}. Die Gültigkeit kommt aus {@code app.auth.access-token-validity}.
 */
@Service
public class JwtService {

    /** Prefix of the HTTP Authorization header carrying a bearer token. */
    public static final String BEARER_PREFIX = "Bearer ";

    /** Claim mit der Session-Id (als String); JWTs ohne diesen Claim werden abgelehnt (DIV-65). */
    static final String SESSION_ID_CLAIM = "sid";

    static final String DEVICE_ID_CLAIM = "deviceId";

    // Env var in prod, .env (loaded into system properties by BudgetAppApplication.main) in dev,
    // fixed dummy value in application-test.properties for tests.
    private final String secretKey;

    private final Clock clock;

    private final AuthProperties authProperties;

    public JwtService(@Value("${secret_key}") String secretKey, Clock clock, AuthProperties authProperties) {
        this.secretKey = secretKey;
        this.clock = clock;
        this.authProperties = authProperties;
    }


    public String extractUsername(String token) {
        return extractClaims(token, Claims::getSubject);
    }

    public <T> T extractClaims(String token, Function<Claims, T> claimsResolver) {
        Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /** Access Token der Session {@code sessionId} für das Gerät {@code deviceId}. */
    public String generateToken(UserDetails userDetails, String deviceId, Long sessionId) {
        var extraClaims = new HashMap<String, Object>();
        extraClaims.put(DEVICE_ID_CLAIM, deviceId);
        extraClaims.put(SESSION_ID_CLAIM, String.valueOf(sessionId));
        return generateToken(extraClaims, userDetails);
    }

    private String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return Jwts
                .builder()
                .setHeaderParam("typ", "JWT")
                .setClaims(extraClaims)
                .setSubject(userDetails.getUsername())
                .setIssuedAt(Date.from(clock.instant()))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .setExpiration(new Date(clock.millis() + authProperties.getAccessTokenValidity().toMillis()))
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        // Here username is equal to email
        String username = extractUsername(token);
        return (EmailUtils.normalize(username).equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(Date.from(clock.instant()));
    }

    public Date extractExpiration(String token) {
        return extractClaims(token, Claims::getExpiration);
    }

    public String extractDeviceId(String token) {
        return extractClaim(token, claims -> claims.get(DEVICE_ID_CLAIM, String.class));
    }

    /** Session-Id eines gültigen JWT; {@code null}, wenn der Claim fehlt oder keine Zahl ist (Legacy-Token). */
    public Long extractSessionId(String token) {
        return extractClaim(token, JwtService::sessionId);
    }

    /**
     * Session-Id aus einem signaturgültigen JWT, auch wenn es bereits abgelaufen ist (Logout mit abgelaufenem Access
     * Token). Leer bei falscher Signatur, unlesbarem JWT oder fehlendem Claim.
     */
    public Optional<Long> extractSessionIdIgnoringExpiry(String token) {
        try {
            return Optional.ofNullable(extractSessionId(token));
        } catch (ExpiredJwtException e) {
            // Signatur wurde vor der Ablaufprüfung geprüft; die Claims sind vertrauenswürdig
            return Optional.ofNullable(sessionId(e.getClaims()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private static Long sessionId(Claims claims) {
        if (!(claims.get(SESSION_ID_CLAIM) instanceof String value)) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Claims extractAllClaims(String token) {
        return Jwts
                .parserBuilder()
                .setSigningKey(getSignInKey())
                // expiry check against the same clock as issuing and isTokenExpired
                .setClock(() -> Date.from(clock.instant()))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Key getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
