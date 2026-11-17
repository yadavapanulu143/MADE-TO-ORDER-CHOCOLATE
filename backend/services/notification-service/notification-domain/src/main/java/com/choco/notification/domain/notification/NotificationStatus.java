package com.choco.notification.domain.notification;

/**
 * The lifecycle of a single notification, from queued to delivered or permanently failed.
 *
 * <p>The state machine is small but explicit, and {@link #canTransitionTo} is the only place that
 * decides which moves are legal. A notification that has been delivered must never be sent again, and
 * a permanently failed one must never quietly become pending, because either would mean the customer
 * receives a message twice or a message that was never actually sent is reported as delivered.
 *
 * <p>{@link #PENDING} is the entry state: the notification has been recorded and is waiting for its
 * first delivery attempt. A failed attempt rests in {@link #FAILED} rather than falling back to
 * {@code PENDING}, so a failure is a visible state in the seller's log — the notification can be
 * inspected and deliberately resent — and the retry scheduler selects it directly. The attempt
 * counter on the aggregate records how many times it has been tried, and once the budget is spent it
 * moves to {@link #DEAD_LETTER}.
 */
public enum NotificationStatus {

    /** Recorded and awaiting its first (or next) delivery attempt. */
    PENDING,

    /** Delivered to the provider and accepted. Terminal. */
    SENT,

    /** A delivery attempt failed, but retries remain. The retry scheduler picks it up again. */
    FAILED,

    /** Retries are exhausted or the failure is permanent. Terminal. */
    DEAD_LETTER,

    /** Suppressed by the recipient's own preference. Terminal. */
    SUPPRESSED;

    public boolean canTransitionTo(NotificationStatus next) {
        if (next == null || next == this) {
            return false;
        }
        return switch (this) {
            case PENDING -> next == SENT || next == FAILED || next == DEAD_LETTER
                    || next == SUPPRESSED;
            // A failed attempt rests in FAILED: it is observable in the seller's log and can be resent,
            // and the retry scheduler picks it up again. It may also go straight to SENT if the next
            // attempt succeeds, or to DEAD_LETTER once the budget is spent.
            case FAILED -> next == PENDING || next == SENT || next == DEAD_LETTER;
            // A dead-lettered notification may be revived only by a deliberate administrator resend,
            // which is the one path that calls it. Nothing automatic ever moves out of this state, so
            // the retry loop cannot resurrect a message that has already spent its budget.
            case DEAD_LETTER -> next == PENDING;
            case SENT, SUPPRESSED -> false;
        };
    }

    public boolean isTerminal() {
        return this == SENT || this == DEAD_LETTER || this == SUPPRESSED;
    }
}
