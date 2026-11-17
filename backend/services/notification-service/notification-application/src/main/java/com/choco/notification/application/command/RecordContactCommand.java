package com.choco.notification.application.command;

import java.time.Instant;
import java.util.UUID;

/**
 * A snapshot of a customer's contact details, taken from an identity event.
 *
 * <p>An upsert command: the identity service emits this on registration, on a contact change and on
 * verification, and the newest one wins. {@code email} and {@code mobile} may each be null when a
 * particular event does not carry that channel; a null means "leave what is known unchanged" rather
 * than "clear it", because a verification event carries no address at all.
 */
public record RecordContactCommand(
        UUID customerId,
        String email,
        String mobile,
        String fullName,
        Instant occurredAt) {

    public RecordContactCommand {
        if (customerId == null) {
            throw new IllegalArgumentException("customerId is required");
        }
    }
}
