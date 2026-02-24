package com.uos.lms.security;

import com.uos.lms.user.User;
import com.uos.lms.user.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.access-token-expiration-seconds:3600}")
    private long expirationSeconds;

    private SecretKey secretKey;

    @PostConstruct
    void init() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET is required");
        }
        if (jwtSecret.length() < 32) {
            log.warn("JWT_SECRET is shorter than 32 characters — consider using a stronger secret");
        }
        secretKey = Keys.hmacShaKeyFor(sha256(jwtSecret));
    }

    public String createAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(expirationSeconds);

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("role", user.getEffectiveRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            parser().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ignored) {
            return false;
        }
    }

    public Long extractUserId(String token) {
        Claims claims = parser().parseSignedClaims(token).getPayload();
        return Long.parseLong(claims.getSubject());
    }

    public UserRole extractUserRole(String token) {
        Claims claims = parser().parseSignedClaims(token).getPayload();
        String roleName = claims.get("role", String.class);
        if (roleName == null || roleName.isBlank()) {
            return UserRole.STUDENT;
        }
        if ("USER".equals(roleName)) {
            return UserRole.STUDENT;
        }

        try {
            return UserRole.valueOf(roleName);
        } catch (IllegalArgumentException exception) {
            return UserRole.STUDENT;
        }
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }

    private JwtParser parser() {
        return Jwts.parser().verifyWith(secretKey).build();
    }

    private byte[] sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm not available", exception);
        }
    }
}
