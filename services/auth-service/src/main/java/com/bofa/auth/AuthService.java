package com.bofa.auth;

import com.bofa.audit.AuditEvent;
import com.bofa.audit.AuditEventType;
import com.bofa.audit.AuditLogger;
import com.bofa.audit.AuditOutcome;
import com.bofa.auth.jwt.Claims;
import com.bofa.auth.jwt.JwtService;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Customer authentication and session lifecycle. Backed in production by the
 * bank's SSO/MFA provider; here the credential check is stubbed to a synthetic
 * directory so the surrounding token + audit logic can be exercised.
 */
public class AuthService {

    private static final long DEFAULT_TTL_SECONDS = 900; // 15 minutes

    // Synthetic credential directory -- NEVER real customer data.
    private static final Map<String, String> SYNTHETIC_USERS = Map.of(
            "customer-demo", "Sup3rSynthetic!",
            "teller-demo", "Branch$ynthetic1");

    private final JwtService jwtService;
    private final AuditLogger auditLogger;
    private final SessionRegistry sessionRegistry;
    private final Clock clock;

    public AuthService(JwtService jwtService,
                       AuditLogger auditLogger,
                       SessionRegistry sessionRegistry,
                       Clock clock) {
        this.jwtService = jwtService;
        this.auditLogger = auditLogger;
        this.sessionRegistry = sessionRegistry;
        this.clock = clock;
    }

    /**
     * Verify credentials and issue a signed session token.
     *
     * @throws InvalidCredentialsException if the username/password do not match
     */
    public String login(String username, String password) {
        String expected = SYNTHETIC_USERS.get(username);
        if (expected == null || !expected.equals(password)) {
            auditLogger.logAuthFailure(username, "Invalid credentials");
            throw new InvalidCredentialsException("Invalid username or password");
        }
        String token = jwtService.issue(username, "RETAIL_CUSTOMER", DEFAULT_TTL_SECONDS);
        auditLogger.log(AuditEvent.builder()
                .type(AuditEventType.AUTH_SUCCESS)
                .outcome(AuditOutcome.SUCCESS)
                .actor(username)
                .message("Login succeeded")
                .build());
        return token;
    }

    /**
     * Validate a bearer token and establish a session.
     *
     * @throws TokenExpiredException if the token has expired -- no session is created
     *                               and a SESSION_EXPIRED audit event is written
     * @throws InvalidTokenException if the token is malformed or unsigned
     */
    public Session establishSession(String token) {
        Claims claims;
        try {
            claims = jwtService.parse(token);
        } catch (TokenExpiredException e) {
            auditLogger.log(AuditEvent.builder()
                    .type(AuditEventType.SESSION_EXPIRED)
                    .outcome(AuditOutcome.DENIED)
                    .actor(subjectFrom(token))
                    .message("Rejected expired session token")
                    .build());
            throw e;
        } catch (InvalidTokenException e) {
            auditLogger.logAuthFailure(subjectFrom(token), "Invalid token: " + e.getMessage());
            throw e;
        }

        Session session = new Session(UUID.randomUUID().toString(), claims.getSub(), now());
        sessionRegistry.register(session);
        return session;
    }

    private Instant now() {
        return clock.instant();
    }

    /**
     * Best-effort subject extraction for audit context; never throws. Ignores
     * expiry so that the actor of an expired token is still recorded.
     */
    private String subjectFrom(String token) {
        try {
            return jwtService.parseIgnoringExpiry(token).getSub();
        } catch (RuntimeException ignored) {
            return "unknown";
        }
    }
}
