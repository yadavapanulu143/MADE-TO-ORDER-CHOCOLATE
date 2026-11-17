package com.choco.notification.application.command;

import com.choco.notification.domain.notification.NotificationType;
import java.util.Map;

/**
 * A request to notify someone about a business fact.
 *
 * <p>Carries the type, the audience, the variables a template needs, and a dedupe key derived from the
 * event that caused it. It deliberately does <em>not</em> carry the recipient's address: the use case
 * resolves that from the contact projection, so an event handler cannot accidentally send a message to
 * an address it invented, and the address is never part of a command that might be logged.
 *
 * <p>{@code customerId} is present only for a customer-addressed notification. An administrative
 * notification resolves its destination from the seller's configuration instead.
 */
public record NotifyCommand(
        NotificationType type,
        String dedupeKey,
        String referenceId,
        String customerId,
        Map<String, String> variables) {

    public NotifyCommand {
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }
        if (dedupeKey == null || dedupeKey.isBlank()) {
            throw new IllegalArgumentException("dedupeKey is required");
        }
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }
}
