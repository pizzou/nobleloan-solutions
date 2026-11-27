package com.patrick.fintech.loan_backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtSessionIdleTest {

    private JwtUtils configured(long defaultJwtMs, long idleMs) {
        JwtUtils jwt = new JwtUtils();
        ReflectionTestUtils.setField(jwt, "secret", "nls-unit-test-signing-secret-at-least-32-bytes-long-00000000");
        ReflectionTestUtils.setField(jwt, "expirationMs", defaultJwtMs);
        ReflectionTestUtils.setField(jwt, "idleTimeoutMs", idleMs);
        return jwt;
    }

    @Test
    void defaultFifteenMinuteJwtIsCappedAtFiveMinutesIdle() {
        JwtUtils jwt = configured(900_000L, 300_000L);
        assertEquals(300_000L, jwt.sessionLifetimeMs());
        String token = jwt.generateToken("user@example.com");
        assertTrue(jwt.validateToken(token));
        assertEquals("user@example.com", jwt.getEmailFromToken(token));
    }

    @Test
    void shorterSessionConfigurationIsNotExtended() {
        JwtUtils jwt = configured(60_000L, 300_000L);
        assertEquals(60_000L, jwt.sessionLifetimeMs());
    }

    @Test
    void challengeTokensCannotBeMistakenForRegularSessionTokens() {
        JwtUtils jwt = configured(900_000L, 300_000L);
        assertFalse(jwt.isLoginOtpChallengeToken(jwt.generateToken("user@example.com")));
    }
}
