package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.helper.EmailUtils;
import io.jsonwebtoken.Claims;
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
import java.util.function.Function;

@Service
public class JwtService {

    /** Prefix of the HTTP Authorization header carrying a bearer token. */
    public static final String BEARER_PREFIX = "Bearer ";

    /** Token validity: 21 days in milliseconds. */
    private static final long TOKEN_VALIDITY_MS = 1000L * 60 * 60 * 24 * 21;

    // Env var in prod, .env (loaded into system properties by BudgetAppApplication.main) in dev,
    // fixed dummy value in application-test.properties for tests.
    private final String secretKey;

    private final Clock clock;

    public JwtService(@Value("${secret_key}") String secretKey, Clock clock) {
        this.secretKey = secretKey;
        this.clock = clock;
    }


    public String extractUsername(String token) {
        return extractClaims(token, Claims::getSubject);
    }

    public <T> T extractClaims(String token, Function<Claims, T> claimsResolver) {
        Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(UserDetails userDetails, String deviceId) {
        var extraClaims = new HashMap<String, Object>();
        extraClaims.put("deviceId", deviceId);
        return generateToken(extraClaims, userDetails);
    }

    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return Jwts
                .builder()
                .setHeaderParam("typ", "JWT")
                .setClaims(extraClaims)
                .setSubject(userDetails.getUsername())
                .setIssuedAt(Date.from(clock.instant()))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .setExpiration(new Date(clock.millis() + TOKEN_VALIDITY_MS))
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
        return extractClaim(token, claims -> claims.get("deviceId", String.class));
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
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
