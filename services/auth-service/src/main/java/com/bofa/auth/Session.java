package com.bofa.auth;

import java.time.Instant;

/** An established, authenticated customer session. */
public final class Session {

    private final String sessionId;
    private final String subject;
    private final Instant createdAt;

    public Session(String sessionId, String subject, Instant createdAt) {
        this.sessionId = sessionId;
        this.subject = subject;
        this.createdAt = createdAt;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getSubject() {
        return subject;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
