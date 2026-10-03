package com.github.svenfran.budgetapp.budgetappbackend.service;

import io.github.cdimascio.dotenv.Dotenv;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.KeyGenerator;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    private static final String SECRET_KEY = "secret_key";

    /** Prefix of the HTTP Authorization header carrying a bearer token. */
    public static final String BEARER_PREFIX = "Bearer ";

    /** Token validity: 21 days in milliseconds. */
    private static final long TOKEN_VALIDITY_MS = 1000L * 60 * 60 * 24 * 21;

    private final Logger logger = LoggerFactory.getLogger(JwtService.class);

    private final Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
    private final String secretKey = System.getenv(SECRET_KEY) != null
            ? System.getenv(SECRET_KEY)
            : dotenv.get(SECRET_KEY);


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
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .setExpiration(new Date(System.currentTimeMillis() + TOKEN_VALIDITY_MS))
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        // Here username is equal to email
        String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
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
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Key generateKey(int n) throws NoSuchAlgorithmException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(n);
        return keyGenerator.generateKey();
    }

    private String convertSecretKeyToString(Key secretKey) {
        byte[] rawData = secretKey.getEncoded();
        return Base64.getEncoder().encodeToString(rawData);
    }

    private String getSecretKey() {
        String secretKey = "";
        try {
            secretKey = convertSecretKeyToString(generateKey(256));
        } catch (NoSuchAlgorithmException e) {
            logger.error("Failed to generate secret key", e);
        }
        return secretKey;
    }

    private Key getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
