package com.bofa.audit;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable record of a single audited action. Audit events are append-only and
 * must capture who did what, to which resource, when, and with what outcome.
 */
public final class AuditEvent {

    private final String id;
    private final AuditEventType type;
    private final AuditOutcome outcome;
    private final String actor;
    private final String resourceId;
    private final String message;
    private final Instant timestamp;

    private AuditEvent(Builder builder) {
        this.id = builder.id;
        this.type = builder.type;
        this.outcome = builder.outcome;
        this.actor = builder.actor;
        this.resourceId = builder.resourceId;
        this.message = builder.message;
        this.timestamp = builder.timestamp;
    }

    public String getId() {
        return id;
    }

    public AuditEventType getType() {
        return type;
    }

    public AuditOutcome getOutcome() {
        return outcome;
    }

    public String getActor() {
        return actor;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getMessage() {
        return message;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AuditEvent)) {
            return false;
        }
        AuditEvent that = (AuditEvent) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "AuditEvent{"
                + "id='" + id + '\''
                + ", type=" + type
                + ", outcome=" + outcome
                + ", actor='" + actor + '\''
                + ", resourceId='" + resourceId + '\''
                + ", timestamp=" + timestamp
                + '}';
    }

    public static final class Builder {
        private String id = UUID.randomUUID().toString();
        private AuditEventType type;
        private AuditOutcome outcome = AuditOutcome.SUCCESS;
        private String actor;
        private String resourceId;
        private String message;
        private Instant timestamp;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder type(AuditEventType type) {
            this.type = type;
            return this;
        }

        public Builder outcome(AuditOutcome outcome) {
            this.outcome = outcome;
            return this;
        }

        public Builder actor(String actor) {
            this.actor = actor;
            return this;
        }

        public Builder resourceId(String resourceId) {
            this.resourceId = resourceId;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public AuditEvent build() {
            if (type == null) {
                throw new IllegalArgumentException("Audit event type is required");
            }
            if (actor == null || actor.isBlank()) {
                throw new IllegalArgumentException("Audit event actor is required");
            }
            return new AuditEvent(this);
        }
    }
}
