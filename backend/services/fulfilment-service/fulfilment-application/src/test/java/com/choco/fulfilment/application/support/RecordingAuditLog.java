package com.choco.fulfilment.application.support;

import com.choco.fulfilment.application.port.AuditLogPort;
import java.util.ArrayList;
import java.util.List;

/** Records audit entries so a test can assert an administrative act was logged. */
public class RecordingAuditLog implements AuditLogPort {

    public record Entry(String actorType, String actorId, String action, String targetType,
                        String targetId, String outcome, String detail) {
    }

    private final List<Entry> entries = new ArrayList<>();

    @Override
    public void record(String actorType, String actorId, String action, String targetType, String targetId,
                       String outcome, String detail, String correlationId) {
        entries.add(new Entry(actorType, actorId, action, targetType, targetId, outcome, detail));
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    public boolean hasAction(String action) {
        return entries.stream().anyMatch(e -> e.action().equals(action));
    }
}
