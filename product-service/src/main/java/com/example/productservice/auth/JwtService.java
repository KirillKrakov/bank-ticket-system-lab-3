package com.example.productservice.auth;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMillis;

    public JwtService(
            @Value("${jwt.secret:very_long_random_secret_at_least_32_chars_for_local_testing_change_me}") String secret,
            @Value("${jwt.expiration-ms:3600000}") long expirationMillis) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        // Ensure minimum length
        this.key = Keys.hmacShaKeyFor(Arrays.copyOf(bytes, Math.max(bytes.length, 32)));
        this.expirationMillis = expirationMillis;
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public String extractUsername(String token) {
        Claims c = parseClaims(token);
        return c.getSubject();
    }

    public String extractUserId(String token) {
        Claims c = parseClaims(token);
        Object uid = c.get("uid");
        return uid != null ? uid.toString() : null;
    }

    public String extractRole(String token) {
        Claims c = parseClaims(token);
        Object role = c.get("role");
        return role != null ? role.toString() : null;
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
    }
}