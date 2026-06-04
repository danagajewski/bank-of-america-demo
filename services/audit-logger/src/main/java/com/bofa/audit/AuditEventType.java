package com.bofa.audit;

/**
 * Classification of audit events. OCC examiners expect every security- and
 * money-movement-relevant action to be classified and retained.
 */
public enum AuditEventType {
    AUTH_SUCCESS,
    AUTH_FAILURE,
    SESSION_EXPIRED,
    TRANSACTION_CREATED,
    TRANSACTION_REJECTED,
    PII_ACCESS,
    PII_MASKED,
    CONFIGURATION_CHANGE;

    /**
     * Whether this event type represents a security-relevant signal that must be
     * surfaced to the fraud / SOC pipelines.
     */
    public boolean isSecuritySensitive() {
        switch (this) {
            case AUTH_FAILURE:
            case SESSION_EXPIRED:
            case TRANSACTION_REJECTED:
            case PII_ACCESS:
                return true;
            default:
                return false;
        }
    }
}
