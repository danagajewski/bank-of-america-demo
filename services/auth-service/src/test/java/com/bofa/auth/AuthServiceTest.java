package com.bofa.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.bofa.audit.AuditLogger;
import com.bofa.audit.InMemoryAuditLogStore;
import com.bofa.auth.jwt.Claims;
import com.bofa.auth.jwt.JwtService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Baseline coverage for the authentication service. Only the successful-login path
 * is covered today. The compliance-critical paths -- expired-token rejection,
 * "session not created on expiry", and audit-trail emission -- are intentionally
 * NOT yet tested.
 *
 * <p>Synthetic credentials only.
 */
class AuthServiceTest {

    private Clock clock;
    private JwtService jwtService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);
        jwtService = new JwtService("synthetic-demo-signing-secret-please-rotate", clock);
        AuditLogger auditLogger = new AuditLogger(new InMemoryAuditLogStore(), clock);
        authService = new AuthService(jwtService, auditLogger, new SessionRegistry(), clock);
    }

    @Test
    void loginWithValidCredentialsIssuesToken() {
        String token = authService.login("customer-demo", "Sup3rSynthetic!");

        assertNotNull(token);
        Claims claims = jwtService.parse(token);
        assertEquals("customer-demo", claims.getSub());
    }
}
