package com.choco.fulfilment.domain.delivery;

import java.time.Instant;
import java.util.UUID;

/**
 * One recorded delivery attempt.
 *
 * <p>A delivery can fail and be retried, so the attempts are kept rather than overwritten: the seller
 * needs to see that the customer was out twice before a third try, and a dispute turns on how many
 * times the door was actually knocked. The failure reason is free text because the real reasons are
 * not a closed set — nobody home, wrong address, customer refused — and forcing them into an enum
 * would lose the detail the seller actually typed.
 */
public record DeliveryAttempt(
        UUID id,
        int attemptNumber,
        DeliveryStatus outcome,
        String failureReason,
        String notes,
        Instant attemptedAt) {

    public static final int MAX_REASON_LENGTH = 500;
    public static final int MAX_NOTES_LENGTH = 500;

    public DeliveryAttempt {
        if (id == null) {
            throw new IllegalArgumentException("Attempt id is required");
        }
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("An attempt number starts at 1");
        }
        if (outcome == null) {
            throw new IllegalArgumentException("An attempt must record its outcome");
        }
        failureReason = normalise(failureReason, MAX_REASON_LENGTH);
        notes = normalise(notes, MAX_NOTES_LENGTH);
        if (attemptedAt == null) {
            throw new IllegalArgumentException("An attempt must be timestamped");
        }
    }

    public static DeliveryAttempt of(UUID id, int attemptNumber, DeliveryStatus outcome,
                                     String failureReason, String notes, Instant at) {
        return new DeliveryAttempt(id, attemptNumber, outcome, failureReason, notes, at);
    }

    private static String normalise(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
