package com.paycycle.billing_engine.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;


/**
 * JwtService — JWT token ka poora lifecycle.
 *
 * ============================================================
 * JWT kya hota hai?
 * ============================================================
 * JSON Web Token — 3 parts hote hain dot se separate:
 *
 * eyJhbGciOiJIUzI1NiJ9         ← Header (algorithm)
 * .eyJ0ZW5hbnRJZCI6Imx4eC4uIn0 ← Payload (claims/data)
 * .xK9mN2pQrS8vT1wZ3yA4bC5dE   ← Signature (tamper-proof)
 *
 * Server secret key se sign karta hai.
 * Client token store karta hai (localStorage/cookie).
 * Har request mein Header mein bhejta hai:
 *   Authorization: Bearer eyJhbGci...
 *
 * Server sirf signature verify karta hai — DB call nahi!
 * Isliye STATELESS hai — fast aur scalable.
 * ============================================================
 *
 * Token mein ye claims store karte hain:
 *   - tenantId  : kaun sa tenant
 *   - email     : user ka email
 *   - role      : ADMIN ya USER
 *   - exp       : expiry time (24 hours)
 * ============================================================
 */
@Slf4j
@Service
public class JwtService {

    @Value("${paycycle.jwt.secret}")
    private String jwtSecret;

    @Value("${paycycle.jwt.expiration-ms}")
    private long jwtExpirationMs;

    /**
     * Token generate karo.
     * Login ke baad call hota hai.
     */
    public String generateToken(String email, String tenantId, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
            .subject(email)                    // "sub" claim — user identifier
            .claim("tenantId", tenantId)       // custom claim
            .claim("role", role)               // custom claim
            .issuedAt(now)                     // "iat" — kab issue hua
            .expiration(expiry)                // "exp" — kab expire hoga
            .signWith(getSigningKey())         // HMAC-SHA256 signature
            .compact();                        // final token string
    }

    /**
     * Token se email nikalo.
     */
    public String extractEmail(String token) {
        return extractClaims(token).getSubject();
    }

    /**
     * Token se tenantId nikalo.
     */
    public String extractTenantId(String token) {
        return extractClaims(token).get("tenantId", String.class);
    }

    /**
     * Token se role nikalo.
     */
    public String extractRole(String token) {
        return extractClaims(token).get("role", String.class);
    }

    /**
     * Token valid hai? (signature + expiry check)
     */
    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractClaims(token);
            boolean notExpired = claims.getExpiration().after(new Date());
            if (!notExpired) {
                log.warn("JWT token expired");
            }
            return notExpired;
        } catch (Exception e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Token se saare claims nikalo.
     * Agar token tampered hai ya expired hai toh exception aayega.
     */
    private Claims extractClaims(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())  // signature verify karo
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    /**
     * Secret string se cryptographic key banao.
     * application.yml mein: paycycle.jwt.secret
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
