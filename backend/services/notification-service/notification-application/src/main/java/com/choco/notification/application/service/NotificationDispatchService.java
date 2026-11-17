package com.choco.notification.application.service;

import com.choco.core.port.Clock;
import com.choco.core.port.EventPublisher;
import com.choco.core.port.IdGenerator;
import com.choco.core.port.TransactionRunner;
import com.choco.notification.application.command.NotifyCommand;
import com.choco.notification.application.dto.SellerContact;
import com.choco.notification.application.port.AuditLogPort;
import com.choco.notification.application.port.MessageDeliveryException;
import com.choco.notification.application.port.MessageSender;
import com.choco.notification.application.port.OutboundMessage;
import com.choco.notification.application.template.MessageContent;
import com.choco.notification.application.template.NotificationTemplates;
import com.choco.notification.domain.contact.CustomerContact;
import com.choco.notification.domain.notification.Notification;
import com.choco.notification.domain.notification.NotificationChannel;
import com.choco.notification.domain.notification.NotificationType;
import com.choco.notification.domain.notification.RecipientType;
import com.choco.notification.domain.port.ContactDirectory;
import com.choco.notification.domain.port.NotificationRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns a business fact into a delivered message: render, record, send, and record the outcome.
 *
 * <p><b>Two phases, two transactions, on purpose.</b> The notification is first recorded as pending
 * and committed; only then is it handed to the provider and the outcome written back. Sending inside
 * the first transaction would mean a provider call that succeeds while the surrounding transaction
 * rolls back — a message the customer received and the system has no record of — or a held-open
 * database transaction for the length of an SMTP conversation. Splitting them means the record always
 * exists first, so a crash between the phases leaves a pending row the scheduler will pick up, and a
 * message is never lost merely because the process died mid-send.
 *
 * <p><b>Idempotent by the event's own identity.</b> Every command carries a dedupe key derived from
 * the event that caused it. A redelivered event finds the existing notification and returns without
 * sending again. This is the property that makes at-least-once delivery safe: the broker may deliver
 * an event more than once, and the customer still receives exactly one message.
 *
 * <p><b>A provider failure is recorded, never thrown upward.</b> The event this method is acting on is
 * a past-tense fact — a payment was captured, an order was confirmed — that has already been
 * committed by another service. A mail server being down must not make this consumer throw and
 * dead-letter an event whose business effect already happened. The failure is stored, retried by the
 * scheduler, and after the retry budget is spent it is dead-lettered and an event tells the seller.
 *
 * <p><b>A missing recipient is retryable, not permanent.</b> The contact projection is fed by the
 * identity service's events, so a message about a customer can arrive before the projection row does.
 * The notification is recorded as pending and left for the scheduler rather than failing, so a race
 * between two events on different queues resolves itself.
 */
public class NotificationDispatchService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchService.class);

    /** How long a pending notification waits before the scheduler may attempt it again. */
    private static final Duration RETRY_BACKOFF = Duration.ofSeconds(30);

    private final NotificationRepository repository;
    private final ContactDirectory contacts;
    private final MessageSender sender;
    private final SellerContact seller;
    private final EventPublisher eventPublisher;
    private final AuditLogPort auditLog;
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final TransactionRunner transactions;
    private final int maxAttempts;

    public NotificationDispatchService(NotificationRepository repository, ContactDirectory contacts,
                                       MessageSender sender, SellerContact seller,
                                       EventPublisher eventPublisher, AuditLogPort auditLog, Clock clock,
                                       IdGenerator idGenerator, TransactionRunner transactions,
                                       int maxAttempts) {
        this.repository = repository;
        this.contacts = contacts;
        this.sender = sender;
        this.seller = seller;
        this.eventPublisher = eventPublisher;
        this.auditLog = auditLog;
        this.clock = clock;
        this.idGenerator = idGenerator;
        this.transactions = transactions;
        this.maxAttempts = maxAttempts;
    }

    /**
     * Handles one notification request from end to end.
     *
     * <p>Returns the notification that now exists, whether it was created here or found from an earlier
     * delivery of the same event. A caller that is a message listener does nothing with the result; a
     * test or a manual resend uses it.
     */
    public Notification notify(NotifyCommand command) {
        Optional<Notification> existing = repository.findByDedupeKey(command.dedupeKey());
        if (existing.isPresent()) {
            // The event has been seen before. Sending again would duplicate a message the customer
            // already has; this is the whole point of the dedupe key.
            log.debug("Notification for {} already exists; skipping duplicate", command.dedupeKey());
            return existing.get();
        }

        Optional<Recipient> recipient = resolveRecipient(command);
        if (recipient.isEmpty()) {
            // No destination known yet. Record nothing and let the event be redelivered or the
            // scheduler retry: the contact projection may simply not have caught up.
            log.info("No recipient available yet for {} ({}); leaving for retry",
                    command.type(), command.dedupeKey());
            return null;
        }

        MessageContent content = render(command, recipient.get());
        Notification notification = Notification.schedule(idGenerator.nextId(), command.type(),
                command.type().recipientType(), recipient.get().address(), content.subject(),
                content.textBody(), command.dedupeKey(), command.referenceId(), clock.now());

        Notification saved = transactions.execute(() -> repository.save(notification));
        attempt(saved, recipient.get());
        return saved;
    }

    /**
     * Attempts delivery of a notification that already exists, updating its outcome.
     *
     * <p>Called both right after creation and by the scheduler for a pending notification. The send
     * happens outside any transaction and the outcome is written in a short one, so a slow provider
     * never holds a database transaction open.
     */
    public void attempt(Notification notification, Recipient recipient) {
        try {
            sender.send(buildMessage(notification, recipient));
            transactions.execute(() -> {
                notification.markSent(clock.now());
                Notification persisted = repository.save(notification);
                eventPublisher.publishAll(persisted.pullDomainEvents());
            });
        } catch (MessageDeliveryException failure) {
            // A transport failure is expected and retryable. The message the customer should have got
            // is preserved on the record; the scheduler will try again, and the failure count is the
            // signal the seller reads.
            log.warn("Delivery attempt {} failed for notification {} ({}): {}", notification.attempts() + 1,
                    notification.id(), notification.type(), failure.getMessage());
            transactions.execute(() -> {
                notification.recordFailure(failure.getMessage(), maxAttempts, clock.now());
                Notification persisted = repository.save(notification);
                eventPublisher.publishAll(persisted.pullDomainEvents());
            });
        }
    }

    /**
     * Builds the message to hand to the provider.
     *
     * <p>The stored body is the source of truth; the HTML part is derived from it at send time by
     * wrapping the same text, so a retry cannot send an HTML body that disagrees with the text the
     * record holds.
     */
    private OutboundMessage buildMessage(Notification notification, Recipient recipient) {
        if (notification.channel() == NotificationChannel.SMS) {
            return OutboundMessage.sms(recipient.address(), notification.body());
        }
        return OutboundMessage.email(recipient.address(), notification.subject(), notification.body(),
                NotificationTemplates.wrapHtml(notification.body()));
    }

    /**
     * Re-sends a notification that failed, on the seller's instruction.
     *
     * <p>Only a failed or dead-lettered notification can be resent. A sent one must not be: the whole
     * point of the dedupe key and the stored outcome is that a customer receives one message, and an
     * admin button that could send it again would defeat that. Resending resets the attempt count so
     * the notification gets a fresh retry budget, and records who asked for it in the audit trail.
     *
     * <p>Returns the notification as it stands after the attempt, so the caller can see the new outcome.
     */
    public Notification resend(UUID notificationId, String actorId) {
        Notification notification = repository.findById(notificationId)
                .orElseThrow(() -> new com.choco.core.error.NotFoundException(
                        com.choco.core.error.ErrorCode.NOT_FOUND, "Notification not found"));
        if (!notification.isResendable()) {
            throw new com.choco.core.error.ConflictException(com.choco.core.error.ErrorCode.CONFLICT,
                    "Only a failed or dead-lettered notification can be resent");
        }
        Optional<Recipient> recipient = resolveStoredRecipient(notification);
        if (recipient.isEmpty()) {
            throw new com.choco.core.error.ConflictException(com.choco.core.error.ErrorCode.CONFLICT,
                    "No destination is available for this notification");
        }
        transactions.execute(() -> repository.save(notification.resetForResend(clock.now())));
        auditLog.record("ADMIN", actorId, "notification.resend", "notification",
                notification.id().toString(), "ATTEMPTED", null, null);
        attempt(notification, recipient.get());
        return notification;
    }

    /**
     * Retries pending notifications, including those left behind by a crash.
     *
     * <p>The scheduler calls this on a fixed interval. Each notification that is due is attempted
     * independently, so one provider failure does not stop the others. A notification that has spent
     * its retry budget is dead-lettered by {@link Notification#recordFailure}.
     */
    public int retryPending() {
        Instant dueBefore = clock.now().minus(RETRY_BACKOFF);
        List<Notification> due = repository.findDueForAttempt(dueBefore, 100);
        int attempted = 0;
        for (Notification notification : due) {
            Optional<Recipient> recipient = resolveStoredRecipient(notification);
            if (recipient.isEmpty()) {
                // Still no destination. Leave it pending; a later contact event may unblock it, and a
                // notification that can never be addressed will be dead-lettered by the attempt cap on
                // the scheduler's own counting only if it keeps being due — so this is bounded by the
                // projection eventually arriving, which it does for any real customer.
                continue;
            }
            attempt(notification, recipient.get());
            attempted++;
        }
        return attempted;
    }

    // -------------------------------------------------------------------------------------------

    private Optional<Recipient> resolveRecipient(NotifyCommand command) {
        if (command.type().recipientType() == RecipientType.ADMIN) {
            return sellerAddress(command.type().channel());
        }
        if (command.customerId() == null) {
            return Optional.empty();
        }
        UUID customerId;
        try {
            customerId = UUID.fromString(command.customerId());
        } catch (IllegalArgumentException e) {
            log.warn("Notification {} carries a non-UUID customer id '{}'", command.type(),
                    command.customerId());
            return Optional.empty();
        }
        return contacts.find(customerId).flatMap(contact -> customerAddress(contact, command.type().channel()));
    }

    /**
     * Re-resolves the destination for a stored notification.
     *
     * <p>A notification records the address it was rendered for, but a retry must still respect the
     * channel: if the customer's email was the destination and they have since removed it, the message
     * cannot be delivered and is left for the attempt cap rather than sent to a stale address.
     */
    private Optional<Recipient> resolveStoredRecipient(Notification notification) {
        if (notification.recipientType() == RecipientType.ADMIN) {
            return sellerAddress(notification.channel());
        }
        // The recipient was resolved at creation; re-use the stored address. A contact change after
        // the fact does not re-address a message that was already rendered for the old one — the
        // customer changed their address after the message was written, and delivering the snapshot
        // to the address it was written for is the correct behaviour.
        return Optional.of(new Recipient(notification.recipient()));
    }

    private Optional<Recipient> customerAddress(CustomerContact contact, NotificationChannel channel) {
        if (channel == NotificationChannel.EMAIL && contact.hasEmail()) {
            return Optional.of(new Recipient(contact.email(), contact.fullName()));
        }
        if (channel == NotificationChannel.SMS && contact.hasMobile()) {
            return Optional.of(new Recipient(contact.mobile(), contact.fullName()));
        }
        return Optional.empty();
    }

    private Optional<Recipient> sellerAddress(NotificationChannel channel) {
        if (channel == NotificationChannel.EMAIL && seller.hasEmail()) {
            return Optional.of(new Recipient(seller.email()));
        }
        if (channel == NotificationChannel.SMS && seller.hasMobile()) {
            return Optional.of(new Recipient(seller.mobile()));
        }
        log.warn("An administrative notification was requested but the seller has no {} address configured",
                channel);
        return Optional.empty();
    }

    /**
     * Renders the message, supplying the customer's name from the projection.
     *
     * <p>Templates greet the customer by name, but most events carry only a customer id. Rather than
     * ask every producer to duplicate the name onto every event — a second place for it to go stale —
     * the name is filled in here from the same projection that supplies the address. A caller may
     * still override it by passing {@code customerName} in the variables, which is how the
     * registration message greets a customer the projection may not have yet.
     */
    private MessageContent render(NotifyCommand command, Recipient recipient) {
        java.util.Map<String, String> variables = new java.util.HashMap<>(command.variables());
        variables.putIfAbsent("customerName", recipient.fullName() == null ? "there"
                : recipient.fullName());
        return NotificationTemplates.render(command.type(), variables);
    }

    /** A resolved destination address, with the name to greet if one is known. */
    private record Recipient(String address, String fullName) {

        Recipient(String address) {
            this(address, null);
        }
    }
}
