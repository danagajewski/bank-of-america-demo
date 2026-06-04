package com.bofa.audit;

import java.util.List;

/** Persistence boundary for audit events. */
public interface AuditLogStore {

    void save(AuditEvent event);

    List<AuditEvent> findAll();

    List<AuditEvent> findByType(AuditEventType type);

    List<AuditEvent> findByActor(String actor);
}
