package com.choco.notification.application.service;

import static com.choco.notification.application.service.NotificationFakes.deliveryFailure;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.error.ConflictException;
import com.choco.notification.application.command.NotifyCommand;
import com.choco.notification.application.dto.SellerContact;
import com.choco.notification.application.service.NotificationFakes.InMemoryContactDirectory;
import com.choco.notification.application.service.NotificationFakes.InMemoryNotificationRepository;
import com.choco.notification.application.service.NotificationFakes.RecordingAuditLog;
import com.choco.notification.application.service.NotificationFakes.RecordingMessageSender;
import com.choco.notification.domain.contact.CustomerContact;
import com.choco.notification.domain.notification.Notification;
import com.choco.notification.domain.notification.NotificationStatus;
import com.choco.notification.domain.notification.NotificationType;
import com.choco.test.Fakes;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("The notification dispatcher")
class NotificationDispatchServiceTest {

    private static final Instant T0 = Instant.parse("2026-02-01T09:00:00Z");
    private static final UUID CUSTOMER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final InMemoryNotificationRepository repository = new InMemoryNotificationRepository();
    private final InMemoryContactDirectory contacts = new InMemoryContactDirectory();
    private final RecordingMessageSender sender = new RecordingMessageSender();
    private final RecordingAuditLog auditLog = new RecordingAuditLog();
    private final Fakes.RecordingEventPublisher events = new Fakes.RecordingEventPublisher();
    private final Fakes.FixedClock clock = new Fakes.FixedClock(T0);

    private NotificationDispatchService service;

    @BeforeEach
    void setUp() {
        service = new NotificationDispatchService(repository, contacts, sender,
                new SellerContact("seller@choco.test", "9000000000"), events, auditLog, clock,
                new Fakes.SequentialIdGenerator(), new Fakes.DirectTransactionRunner(), 3);
        contacts.put(new CustomerContact(CUSTOMER_ID, "asha@example.com", "9876543210", "Asha Rao", T0));
    }

    private NotifyCommand orderConfirmed() {
        return new NotifyCommand(NotificationType.ORDER_CONFIRMED, "order.confirmed:order-1",
                "order-1", CUSTOMER_ID.toString(), Map.of("orderNumber", "CHOCO-1"));
    }

    @Test
    @DisplayName("renders, records and sends a customer email, greeting them by name from the projection")
    void sendsAnEmail() {
        Notification notification = service.notify(orderConfirmed());

        assertThat(notification.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(sender.sent).hasSize(1);
        assertThat(sender.sent.getFirst().recipient()).isEqualTo("asha@example.com");
        assertThat(sender.sent.getFirst().subject()).contains("CHOCO-1");
        assertThat(sender.sent.getFirst().textBody())
                .as("the greeting comes from the projection, which the event did not carry")
                .contains("Asha Rao");
        assertThat(events.eventsOfType("notification.sent")).hasSize(1);
    }

    @Test
    @DisplayName("a redelivered event sends nothing a second time")
    void isIdempotentByDedupeKey() {
        service.notify(orderConfirmed());
        service.notify(orderConfirmed());

        assertThat(sender.sent)
                .as("the dedupe key is the whole guarantee against duplicate messages")
                .hasSize(1);
        assertThat(repository.all()).hasSize(1);
    }

    @Test
    @DisplayName("an unknown customer is left for retry rather than sent to nowhere")
    void defersWhenNoRecipientIsKnown() {
        Notification result = service.notify(new NotifyCommand(NotificationType.ORDER_CONFIRMED,
                "order.confirmed:order-2", "order-2",
                UUID.randomUUID().toString(), Map.of("orderNumber", "CHOCO-2")));

        assertThat(result).isNull();
        assertThat(sender.sent).isEmpty();
        assertThat(repository.all())
                .as("nothing is recorded until a destination is known")
                .isEmpty();
    }

    @Test
    @DisplayName("a provider failure is recorded, not thrown, so the business event is not undone")
    void recordsProviderFailure() {
        sender.failAlwaysWith(deliveryFailure("smtp timeout"));

        Notification notification = service.notify(orderConfirmed());

        assertThat(notification.status()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.attempts()).isEqualTo(1);
        assertThat(notification.lastError()).contains("smtp timeout");
        assertThat(events.eventsOfType("notification.sent")).isEmpty();
    }

    @Test
    @DisplayName("a retry re-sends a pending notification and marks it sent")
    void retriesPending() {
        sender.failAlwaysWith(deliveryFailure("smtp timeout"));
        service.notify(orderConfirmed());
        sender.sent.clear();

        clock.advanceSeconds(60);
        int attempted = service.retryPending();

        assertThat(attempted).isEqualTo(1);
        assertThat(sender.sent).hasSize(1);
    }

    @Test
    @DisplayName("a notification is not retried before its backoff elapses")
    void respectsBackoff() {
        sender.failAlwaysWith(deliveryFailure("smtp timeout"));
        service.notify(orderConfirmed());

        assertThat(service.retryPending())
                .as("a message that just failed must not be retried in the same instant")
                .isZero();
    }

    @Test
    @DisplayName("an SMS notification routes to the mobile number and carries no subject")
    void sendsAnSms() {
        Notification notification = service.notify(new NotifyCommand(
                NotificationType.OUT_FOR_DELIVERY, "delivery.out:del-1", "order-1",
                CUSTOMER_ID.toString(), Map.of("orderNumber", "CHOCO-1")));

        assertThat(notification.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(sender.sent.getFirst().recipient()).isEqualTo("9876543210");
        assertThat(sender.sent.getFirst().subject()).isNull();
        assertThat(sender.sent.getFirst().htmlBody()).isNull();
    }

    @Test
    @DisplayName("an administrative notification goes to the seller, not the customer")
    void sendsToTheSeller() {
        Notification notification = service.notify(new NotifyCommand(
                NotificationType.REFUND_REQUESTED, "order.refund-requested:order-1", "order-1",
                CUSTOMER_ID.toString(),
                Map.of("orderNumber", "CHOCO-1", "amount", "500.00", "reason", "changed mind")));

        assertThat(notification.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(sender.sent.getFirst().recipient()).isEqualTo("seller@choco.test");
    }

    @Test
    @DisplayName("a resend of a failed notification sends it again and is audited")
    void resendsAFailedNotification() {
        sender.failAlwaysWith(deliveryFailure("smtp timeout"));
        Notification notification = service.notify(orderConfirmed());
        sender.sent.clear();

        Notification resent = service.resend(notification.id(), "admin-1");

        assertThat(resent.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(sender.sent).hasSize(1);
        assertThat(auditLog.actions).containsExactly("ADMIN:admin-1:notification.resend:" + notification.id());
    }

    @Test
    @DisplayName("a sent notification cannot be resent")
    void refusesToResendASentNotification() {
        Notification notification = service.notify(orderConfirmed());

        assertThatThrownBy(() -> service.resend(notification.id(), "admin-1"))
                .isInstanceOf(ConflictException.class);
        assertThat(sender.sent).hasSize(1);
    }

    @Test
    @DisplayName("escaping: a customer name with markup cannot inject HTML into the message")
    void escapesInterpolatedValues() {
        contacts.put(new CustomerContact(CUSTOMER_ID, "asha@example.com", "9876543210",
                "<script>alert(1)</script>", T0));

        service.notify(orderConfirmed());

        assertThat(sender.sent.getFirst().htmlBody())
                .doesNotContain("<script>")
                .contains("&lt;script&gt;");
    }
}
