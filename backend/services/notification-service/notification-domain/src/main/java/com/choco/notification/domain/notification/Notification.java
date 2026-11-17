package com.choco.notification.domain.notification;

import com.choco.core.domain.AggregateRoot;
import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;
import com.choco.notification.domain.event.NotificationFailed;
import com.choco.notification.domain.event.NotificationSent;
import java.time.Instant;
import java.util.UUID;

/**
 * One message to one recipient, and the record of what happened to it.
 *
 * <p>A notification is an aggregate in its own right rather than a log line, because the business
 * needs to answer questions about it after the fact: was this order confirmation ever sent, how many
 * times was it tried, and did it finally fail? A log line is rotated away; a row survives.
 *
 * <p><b>Deduplication is part of the aggregate's identity.</b> Every notification carries a
 * {@code dedupeKey} derived from the event that produced it (event id plus type), and the database
 * enforces it unique. A redelivered event therefore cannot create a second notification: the caller
 * checks for an existing key first and, if two consumers race, the unique index refuses the second
 * insert. Without that, a broker retry — which is normal, not exceptional — would send a customer a
 * second order confirmation.
 *
 * <p><b>The message is rendered before it is stored.</b> The subject and body are the finished text, so
 * a retry re-sends exactly what the customer would have received, and a later change to a template
 * cannot rewrite the history of what was actually sent. This is the same snapshot principle the order
 * service applies to prices.
 *
 * <p><b>A provider failure is never a business failure.</b> The aggregate records the failure and
 * moves on; it cannot throw in a way that would reverse a payment or an order, because the events
 * that reach it are past-tense facts that have already been committed elsewhere.
 */
public class Notification extends AggregateRoot {

    /** Longest provider-neutral failure summary kept on the record. */
    public static final int MAX_ERROR_LENGTH = 300;

    private final UUID id;
    private final NotificationType type;
    private final NotificationChannel channel;
    private final RecipientType recipientType;
    private final String recipient;
    private final String dedupeKey;
    private final String referenceId;
    private final Instant createdAt;

    private String subject;
    private String body;
    private NotificationStatus status;
    private int attempts;
    private String lastError;
    private Instant sentAt;
    private Instant updatedAt;

    private Notification(UUID id, NotificationType type, RecipientType recipientType, String recipient,
                         String dedupeKey, String referenceId, Instant createdAt) {
        this.id = id;
        this.type = type;
        this.channel = type.channel();
        this.recipientType = recipientType;
        this.recipient = recipient;
        this.dedupeKey = dedupeKey;
        this.referenceId = referenceId;
        this.createdAt = createdAt;
        this.status = NotificationStatus.PENDING;
        this.updatedAt = createdAt;
    }

    /**
     * Records a new notification, ready for its first attempt.
     *
     * <p>The rendered subject and body are supplied by the caller: the domain does not know how to
     * format an order number into a sentence, and putting template text in the aggregate would tie the
     * business rule to presentation. What the domain does decide is the channel and audience, which it
     * takes from the {@link NotificationType}.
     */
    public static Notification schedule(UUID id, NotificationType type, RecipientType recipientType,
                                        String recipient, String subject, String body, String dedupeKey,
                                        String referenceId, Instant now) {
        if (recipient == null || recipient.isBlank()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "A notification needs a recipient");
        }
        if (dedupeKey == null || dedupeKey.isBlank()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "A notification needs a deduplication key");
        }
        Notification notification = new Notification(id, type, recipientType, recipient.trim(), dedupeKey,
                referenceId, now);
        notification.subject = subject;
        notification.body = body;
        return notification;
    }

    /** Rehydrates from persistence without re-running creation rules or re-raising events. */
    public static Notification rehydrate(UUID id, NotificationType type, RecipientType recipientType,
                                         String recipient, String subject, String body, String dedupeKey,
                                         String referenceId, NotificationStatus status, int attempts,
                                         String lastError, Instant sentAt, Instant createdAt,
                                         Instant updatedAt) {
        Notification notification = new Notification(id, type, recipientType, recipient, dedupeKey,
                referenceId, createdAt);
        notification.subject = subject;
        notification.body = body;
        notification.status = status;
        notification.attempts = attempts;
        notification.lastError = lastError;
        notification.sentAt = sentAt;
        notification.updatedAt = updatedAt;
        return notification;
    }

    // -------------------------------------------------------------------------------------------
    // Delivery outcomes
    // -------------------------------------------------------------------------------------------

    /**
     * Records a successful hand-over to the provider.
     *
     * <p>Only a pending or previously failed notification can be sent. A delivered one is terminal, so
     * a duplicated consumer call — the very thing the dedupe key usually prevents — cannot send the
     * message a second time through this path either. That is the second line of defence behind the
     * unique index.
     */
    public void markSent(Instant now) {
        if (status == NotificationStatus.SENT) {
            // A repeat call is a no-op rather than an error: the message was sent, and saying so again
            // changes nothing. Throwing here would turn a harmless retry into a dead letter.
            return;
        }
        moveTo(NotificationStatus.SENT, now);
        this.attempts++;
        this.sentAt = now;
        this.lastError = null;
        registerEvent(new NotificationSent(UUID.randomUUID(), now, id.toString(), type.name(),
                channel.name(), recipientType.name(), referenceId));
    }

    /**
     * Records a failed attempt, deciding between another retry and a dead letter.
     *
     * <p>The attempt counter is incremented whether or not the notification will be retried, because
     * the number of times we tried is a fact about what happened. Once {@code maxAttempts} is reached
     * the notification is dead-lettered and a {@code notification.failed} event is raised so the seller
     * learns a customer never got their message. Below the limit the notification rests in
     * {@code FAILED}, where it is visible in the seller's log and eligible for the next retry; the
     * scheduler picks it up again after the backoff.
     */
    public void recordFailure(String error, int maxAttempts, Instant now) {
        if (status.isTerminal()) {
            // A terminal notification ignores a late failure report: it was already sent, or already
            // dead-lettered, and neither is undone by a straggler.
            return;
        }
        this.attempts++;
        this.lastError = summarise(error);
        this.updatedAt = now;
        if (attempts >= maxAttempts) {
            moveTo(NotificationStatus.DEAD_LETTER, now);
            registerEvent(new NotificationFailed(UUID.randomUUID(), now, id.toString(), type.name(),
                    channel.name(), recipientType.name(), referenceId, lastError));
        } else if (status != NotificationStatus.FAILED) {
            // Already FAILED means this is a retry that failed again; it stays where it is, with the
            // newer error and count, rather than making a transition to the state it is already in.
            moveTo(NotificationStatus.FAILED, now);
        }
    }

    /**
     * Marks the notification suppressed because the recipient opted out.
     *
     * <p>Terminal and distinct from a failure: nothing went wrong, the customer simply asked not to be
     * contacted on this channel. Recording it as a failure would make the seller's failure count lie.
     */
    public void suppress(Instant now) {
        if (!status.canTransitionTo(NotificationStatus.SUPPRESSED)) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "A " + status + " notification cannot be suppressed");
        }
        moveTo(NotificationStatus.SUPPRESSED, now);
    }

    // -------------------------------------------------------------------------------------------

    /**
     * Whether an administrator may resend this notification.
     *
     * <p>Only a failed or dead-lettered one: those are the states in which the customer never got the
     * message. A sent notification is excluded because the customer already has it, and a suppressed one
     * because the recipient asked not to be contacted — resending either would be the opposite of what
     * the record says should happen.
     */
    public boolean isResendable() {
        return status == NotificationStatus.FAILED || status == NotificationStatus.DEAD_LETTER;
    }

    /**
     * Revives a failed or dead-lettered notification for another attempt.
     *
     * <p>The attempt counter is reset, not incremented: a resend is a fresh decision by a person, not
     * another automatic retry, and the message should get a full budget rather than immediately
     * dead-letter again on the next failure. The previous error is cleared so the record shows the
     * current state of the attempt rather than a stale reason.
     */
    public Notification resetForResend(Instant now) {
        if (!isResendable()) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "A " + status + " notification cannot be resent");
        }
        moveTo(NotificationStatus.PENDING, now);
        this.attempts = 0;
        this.lastError = null;
        return this;
    }

    private void moveTo(NotificationStatus next, Instant now) {
        if (!status.canTransitionTo(next)) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "A notification cannot move from " + status + " to " + next);
        }
        this.status = next;
        this.updatedAt = now;
    }

    /**
     * Truncates a provider failure to a bounded, provider-neutral summary.
     *
     * <p>The summary is stored and, on a terminal failure, put on an event. A raw transport error can
     * echo the destination address back, so it is cut to a length that keeps the reason readable
     * without turning the record into a copy of the provider's response.
     */
    private static String summarise(String error) {
        if (error == null || error.isBlank()) {
            return "Delivery failed";
        }
        String trimmed = error.trim().replaceAll("\\s+", " ");
        return trimmed.length() <= MAX_ERROR_LENGTH ? trimmed : trimmed.substring(0, MAX_ERROR_LENGTH);
    }

    public boolean isTerminal() {
        return status.isTerminal();
    }

    public UUID id() {
        return id;
    }

    public NotificationType type() {
        return type;
    }

    public NotificationChannel channel() {
        return channel;
    }

    public RecipientType recipientType() {
        return recipientType;
    }

    public String recipient() {
        return recipient;
    }

    public String subject() {
        return subject;
    }

    public String body() {
        return body;
    }

    public String dedupeKey() {
        return dedupeKey;
    }

    public String referenceId() {
        return referenceId;
    }

    public NotificationStatus status() {
        return status;
    }

    public int attempts() {
        return attempts;
    }

    public String lastError() {
        return lastError;
    }

    public Instant sentAt() {
        return sentAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
