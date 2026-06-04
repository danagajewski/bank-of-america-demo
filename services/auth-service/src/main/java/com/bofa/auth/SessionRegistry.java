package com.bofa.auth;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks currently-established customer sessions. */
public class SessionRegistry {

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public void register(Session session) {
        sessions.put(session.getSessionId(), session);
    }

    public Optional<Session> get(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    public boolean hasSessionForSubject(String subject) {
        return sessions.values().stream()
                .anyMatch(s -> s.getSubject().equals(subject));
    }

    public int activeCount() {
        return sessions.size();
    }
}
