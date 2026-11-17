package com.choco.payment.application.support;

import com.choco.payment.application.port.AuditLogPort;
import java.util.ArrayList;
import java.util.List;

/** Captures every audit call so a test can assert the act was recorded. */
public final class RecordingAuditLog implements AuditLogPort {

    private final List<String> actions = new ArrayList<>();
    private final List<String> details = new ArrayList<>();

    @Override
    public void record(String actorType, String actorId, String action, String targetType,
                       String targetId, String outcome, String detail, String correlationId) {
        actions.add(action);
        details.add(outcome + ":" + (detail == null ? "" : detail));
    }

    public List<String> actions() {
        return List.copyOf(actions);
    }

    public boolean hasAction(String action) {
        return actions.contains(action);
    }
}
