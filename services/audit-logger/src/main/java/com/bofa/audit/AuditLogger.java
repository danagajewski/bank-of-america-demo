package com.bofa.audit;

import java.time.Clock;
import java.util.List;

/**
 * Entry point for emitting audit events. Every compliance-relevant action in the
 * platform (authentication, money movement, PII access) flows through here so the
 * bank can demonstrate a complete, classified, timestamped audit trail.
 */
public class AuditLogger {

    private final AuditLogStore store;
    private final Clock clock;

    public AuditLogger(AuditLogStore store) {
        this(store, Clock.systemUTC());
    }

    public AuditLogger(AuditLogStore store, Clock clock) {
        if (store == null) {
            throw new IllegalArgumentException("AuditLogStore is required");
        }
        if (clock == null) {
            throw new IllegalArgumentException("Clock is required");
        }
        this.store = store;
        this.clock = clock;
    }

    /**
     * Persist a fully-formed audit event, stamping it with the current time if the
     * caller did not supply one.
     */
    public AuditEvent log(AuditEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        AuditEvent stamped = event;
        if (event.getTimestamp() == null) {
            stamped = AuditEvent.builder()
                    .id(event.getId())
                    .type(event.getType())
                    .outcome(event.getOutcome())
                    .actor(event.getActor())
                    .resourceId(event.getResourceId())
                    .message(event.getMessage())
                    .timestamp(clock.instant())
                    .build();
        }
        store.save(stamped);
        return stamped;
    }

    /** Convenience emitter for a failed authentication attempt. */
    public AuditEvent logAuthFailure(String actor, String reason) {
        AuditEvent event = AuditEvent.builder()
                .type(AuditEventType.AUTH_FAILURE)
                .outcome(AuditOutcome.FAILURE)
                .actor(actor == null ? "unknown" : actor)
                .message(reason)
                .timestamp(clock.instant())
                .build();
        store.save(event);
        return event;
    }

    /** Convenience emitter for a successfully booked transaction. */
    public AuditEvent logTransactionCreated(String actor, String transactionId, String message) {
        AuditEvent event = AuditEvent.builder()
                .type(AuditEventType.TRANSACTION_CREATED)
                .outcome(AuditOutcome.SUCCESS)
                .actor(actor)
                .resourceId(transactionId)
                .message(message)
                .timestamp(clock.instant())
                .build();
        store.save(event);
        return event;
    }

    /**
     * Map an event to a monitoring severity. Examiners look closely at how failed
     * auth and rejected transactions are escalated.
     */
    public Severity classify(AuditEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        if (event.getOutcome() == AuditOutcome.FAILURE
                || event.getOutcome() == AuditOutcome.DENIED) {
            return Severity.WARN;
        }
        if (event.getType().isSecuritySensitive()) {
            return Severity.WARN;
        }
        return Severity.INFO;
    }

    public List<AuditEvent> findByType(AuditEventType type) {
        return store.findByType(type);
    }

    public List<AuditEvent> recent() {
        return store.findAll();
    }
}
