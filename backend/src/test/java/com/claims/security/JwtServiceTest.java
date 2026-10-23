package com.claims.security;

import static org.junit.jupiter.api.Assertions.*;

import com.claims.entity.Role;
import com.claims.entity.User;
import io.jsonwebtoken.JwtException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
    private static final long EXPIRATION_MS = 60_000;
    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        String secret = Base64.getEncoder().encodeToString(
                "a-test-secret-that-is-at-least-32-bytes".getBytes(StandardCharsets.UTF_8));
        jwtService = new JwtService(secret, EXPIRATION_MS);
        user = new User("jane@example.com", "encoded", "Jane Doe", Role.CLAIMANT);
    }

    @Test
    void generatesSignedTokenWithEmailAndConfiguredExpiration() {
        String token = jwtService.generateToken(user);

        assertEquals("jane@example.com", jwtService.extractEmail(token));
        assertTrue(jwtService.isValid(token, "JANE@example.com"));
        assertEquals(EXPIRATION_MS, jwtService.getExpirationMs());
    }

    @Test
    void rejectsTokenForDifferentUserAndMalformedToken() {
        String token = jwtService.generateToken(user);

        assertFalse(jwtService.isValid(token, "other@example.com"));
        assertThrows(JwtException.class, () -> jwtService.extractEmail("not-a-jwt"));
    }
}
