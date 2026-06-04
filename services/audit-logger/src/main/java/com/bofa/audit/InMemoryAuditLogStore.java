package com.bofa.audit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * In-memory append-only audit store. A production deployment would back this with
 * a WORM (write-once-read-many) store; the contract is identical.
 */
public class InMemoryAuditLogStore implements AuditLogStore {

    private final List<AuditEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void save(AuditEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Cannot persist a null audit event");
        }
        events.add(event);
    }

    @Override
    public List<AuditEvent> findAll() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    @Override
    public List<AuditEvent> findByType(AuditEventType type) {
        return events.stream()
                .filter(e -> e.getType() == type)
                .collect(Collectors.toList());
    }

    @Override
    public List<AuditEvent> findByActor(String actor) {
        return events.stream()
                .filter(e -> actor != null && actor.equals(e.getActor()))
                .collect(Collectors.toList());
    }

    public int size() {
        return events.size();
    }
}
