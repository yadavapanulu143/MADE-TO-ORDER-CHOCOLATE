package com.choco.notification.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.choco.events.envelope.EventTypes;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * End-to-end: a business event published to RabbitMQ becomes a delivered message.
 *
 * <p>Nothing here is mocked. The event goes onto a real broker and is routed by the real binding to the
 * real consumer, which resolves the recipient from the projection, renders the template, and hands the
 * message to a real SMTP server. The assertion is on the message GreenMail received, which is the only
 * proof that the whole chain — binding, header, payload parsing, projection, template, transport —
 * actually works together.
 */
@DisplayName("Notification journey (real broker, real SMTP)")
class NotificationJourneyIT extends AbstractNotificationIntegrationTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private void registerCustomer() {
        publish(EventTypes.CUSTOMER_REGISTERED, CUSTOMER_ID.toString(), Map.of(
                "email", "asha@example.com",
                "mobile", "9876543210",
                "fullName", "Asha Rao"));
        // The contact projection must be in place before the order event arrives, or the order event
        // would find no recipient and be left for retry. Wait for it so the journey is deterministic.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(contactExists()).isTrue());
    }

    @Test
    @DisplayName("an order confirmation reaches the customer's inbox, greeting them by name")
    void orderConfirmationIsEmailed() throws Exception {
        registerCustomer();

        publish(EventTypes.ORDER_CONFIRMED, "order-1", Map.of(
                "orderId", "order-1",
                "orderNumber", "CHOCO-2026001-1",
                "customerId", CUSTOMER_ID.toString()));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            var messages = GREEN_MAIL.getReceivedMessages();
            assertThat(messages).isNotEmpty();
            var message = messages[messages.length - 1];
            assertThat(message.getAllRecipients()[0].toString()).isEqualTo("asha@example.com");
            assertThat(message.getSubject()).contains("CHOCO-2026001-1");
            assertThat(messageText(message))
                    .as("the greeting comes from the contact projection")
                    .contains("Asha Rao");
        });

        assertThat(notificationCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("a redelivered event does not send a second email")
    void redeliveryIsDeduplicated() throws Exception {
        registerCustomer();

        Map<String, Object> order = Map.of(
                "orderId", "order-1",
                "orderNumber", "CHOCO-2026001-1",
                "customerId", CUSTOMER_ID.toString());
        publish(EventTypes.ORDER_CONFIRMED, "order-1", order);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(GREEN_MAIL.getReceivedMessages()).hasSize(1));

        // The same event id and dedupe key delivered again. In production a broker redelivery carries
        // the same event id; here the dedupe key is derived from the order, which is what the handler
        // keys on, so a second publish of the same order must not produce a second message.
        publish(EventTypes.ORDER_CONFIRMED, "order-1", order);

        Thread.sleep(2000);
        assertThat(GREEN_MAIL.getReceivedMessages())
                .as("the customer must receive exactly one confirmation")
                .hasSize(1);
        assertThat(notificationCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("a customer contact change re-addresses later messages")
    void contactChangeUpdatesTheProjection() throws Exception {
        registerCustomer();

        publish(EventTypes.CUSTOMER_CONTACT_CHANGED, CUSTOMER_ID.toString(), Map.of(
                "email", "asha.new@example.com",
                "mobile", "9123456780",
                "fullName", "Asha R Menon"));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var emails = contactEmails();
            assertThat(emails).contains("asha.new@example.com");
        });

        publish(EventTypes.ORDER_PLACED, "order-2", Map.of(
                "orderId", "order-2",
                "orderNumber", "CHOCO-2026001-2",
                "customerId", CUSTOMER_ID.toString(),
                "totalMinorUnits", 49900,
                "currency", "INR"));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            var messages = GREEN_MAIL.getReceivedMessages();
            assertThat(messages).isNotEmpty();
            assertThat(messages[messages.length - 1].getAllRecipients()[0].toString())
                    .as("the message goes to the address the customer changed to")
                    .isEqualTo("asha.new@example.com");
        });
    }

    @Test
    @DisplayName("a seller-addressed event goes to the seller, not the customer")
    void refundRequestGoesToTheSeller() throws Exception {
        publish(EventTypes.REFUND_REQUESTED, "order-3", Map.of(
                "orderId", "order-3",
                "orderNumber", "CHOCO-2026001-3",
                "amountMinorUnits", 49900,
                "currency", "INR",
                "reason", "changed my mind"));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            var messages = GREEN_MAIL.getReceivedMessages();
            assertThat(messages).isNotEmpty();
            assertThat(messages[messages.length - 1].getAllRecipients()[0].toString())
                    .isEqualTo("seller@choco.test");
        });
    }

    // --- helpers -------------------------------------------------------------------------------

    /** Flattens a multipart message to its text, so the assertion is on the content, not the envelope. */
    private static String messageText(jakarta.mail.internet.MimeMessage message) throws Exception {
        Object content = message.getContent();
        return flatten(content);
    }

    private static String flatten(Object content) throws Exception {
        if (content instanceof jakarta.mail.Multipart multipart) {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                text.append(flatten(multipart.getBodyPart(i).getContent())).append('\n');
            }
            return text.toString();
        }
        return String.valueOf(content);
    }

    private boolean contactExists() {
        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT count(*) FROM customer_contacts")) {
            rows.next();
            return rows.getInt(1) > 0;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private java.util.List<String> contactEmails() {
        java.util.List<String> emails = new java.util.ArrayList<>();
        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT email FROM customer_contacts")) {
            while (rows.next()) {
                emails.add(rows.getString(1));
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return emails;
    }

    private int notificationCount() {
        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT count(*) FROM notifications")) {
            rows.next();
            return rows.getInt(1);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
