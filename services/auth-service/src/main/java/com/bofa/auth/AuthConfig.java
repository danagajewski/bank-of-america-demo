package com.bofa.auth;

import com.bofa.audit.AuditLogStore;
import com.bofa.audit.AuditLogger;
import com.bofa.audit.InMemoryAuditLogStore;
import com.bofa.auth.jwt.JwtService;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the authentication collaborators as Spring beans. */
@Configuration
public class AuthConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public AuditLogStore auditLogStore() {
        return new InMemoryAuditLogStore();
    }

    @Bean
    public AuditLogger auditLogger(AuditLogStore store, Clock clock) {
        return new AuditLogger(store, clock);
    }

    @Bean
    public JwtService jwtService(
            @Value("${auth.jwt.secret:synthetic-demo-signing-secret-please-rotate}") String secret,
            Clock clock) {
        return new JwtService(secret, clock);
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistry();
    }

    @Bean
    public AuthService authService(
            JwtService jwtService,
            AuditLogger auditLogger,
            SessionRegistry sessionRegistry,
            Clock clock) {
        return new AuthService(jwtService, auditLogger, sessionRegistry, clock);
    }
}
