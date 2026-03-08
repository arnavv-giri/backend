package com.thriftbazaar.backend.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

/**
 * Spring-managed JWT utility.
 *
 * The secret and expiry are read from environment variables via
 * application.properties — never hardcoded in source.
 *
 * Properties (set via environment variables):
 *   JWT_SECRET        — HMAC-SHA256 signing secret (must be ≥ 32 chars / 256 bits)
 *   JWT_EXPIRATION_MS — token lifetime in milliseconds (default: 3 600 000 = 1 hour)
 *
 * Startup validation
 * ──────────────────
 * If JWT_SECRET is missing or shorter than 32 characters the application
 * throws an IllegalStateException during bean initialisation and refuses
 * to start. This prevents silent use of a weak or absent secret in any
 * environment (dev, staging, production).
 */
@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    /** Minimum secret length in bytes to satisfy HMAC-SHA256 requirements. */
    private static final int MIN_SECRET_LENGTH = 32;

    private final Key  signingKey;
    private final long expirationMs;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms:3600000}") long expirationMs
    ) {
        // ── Fail-fast validation ─────────────────────────────────────────
        // An empty or short JWT secret would silently allow anyone to forge
        // tokens. We throw at bean construction time so the problem is caught
        // immediately on startup rather than at the first login request.
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET environment variable is not set. " +
                    "Generate one with: openssl rand -hex 32");
        }
        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "JWT_SECRET is too short (" + secret.length() + " chars). " +
                    "It must be at least " + MIN_SECRET_LENGTH + " characters (256 bits). " +
                    "Generate one with: openssl rand -hex 32");
        }

        this.signingKey   = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;

        log.info("JwtUtil initialised — token lifetime: {} ms ({} min)",
                expirationMs, expirationMs / 60_000);
    }

    // ── Token generation ──────────────────────────────────────────────────

    public String generateToken(String email, String role) {
        return Jwts.builder()
                .setSubject(email)
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    // ── Token validation ──────────────────────────────────────────────────

    /**
     * Parses and validates the JWT.
     *
     * @param token raw Bearer token (without the "Bearer " prefix)
     * @return Claims — caller can extract subject (email) and custom claims (role)
     * @throws JwtException if the token is invalid, expired, or tampered with
     */
    public Claims validateToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
