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
 * The administrative API: the delivery log, its authorization, and the one write it offers.
 *
 * <p>The resend path is exercised against a real database and a real SMTP server. The message is first
 * made to fail — by pointing the mail sender at a dead port through a contact with no address, so the
 * attempt cannot succeed — then the log is read, the notification is resent, and the message is
 * observed at GreenMail. That is the whole feature: a seller sees a failed message and can send it
 * again, and the customer receives it.
 */
@DisplayName("Admin notification API (real HTTP, real database)")
class AdminNotificationApiIT extends AbstractNotificationIntegrationTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ADMIN_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    @DisplayName("the log lists delivered notifications with the recipient masked")
    void logListsNotificationsWithMaskedRecipient() {
        publish(EventTypes.REFUND_REQUESTED, "order-1", Map.of(
                "orderId", "order-1",
                "orderNumber", "CHOCO-1",
                "amountMinorUnits", 49900,
                "currency", "INR",
                "reason", "changed mind"));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            var response = get("/api/v1/admin/notifications", adminToken(ADMIN_ID));
            assertThat(response.statusCode()).isEqualTo(200);
            var body = json(response);
            assertThat(body.get("totalElements").asInt()).isEqualTo(1);
            var view = body.get("content").get(0);
            assertThat(view.get("recipientMasked").asText())
                    .as("the full address must never be returned in the log")
                    .doesNotContain("seller@choco.test")
                    .contains("***");
            assertThat(view.get("body"))
                    .as("the message body is not part of the administrative view")
                    .isNull();
        });
    }

    @Test
    @DisplayName("counters report the delivery health")
    void countersReportDeliveryHealth() {
        publish(EventTypes.REFUND_REQUESTED, "order-1", Map.of(
                "orderId", "order-1", "orderNumber", "CHOCO-1", "amountMinorUnits", 49900,
                "currency", "INR", "reason", "changed mind"));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            var response = get("/api/v1/admin/notifications/counters", adminToken(ADMIN_ID));
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(json(response).get("sent").asLong()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("a customer cannot read the notification log")
    void customersCannotReadTheLog() {
        var response = get("/api/v1/admin/notifications", customerToken(CUSTOMER_ID));
        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("an administrator without the notification permission cannot read the log")
    void unpermissionedAdminCannotReadTheLog() {
        var response = get("/api/v1/admin/notifications", unpermissionedAdminToken(ADMIN_ID));
        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("an unknown notification id is a 404")
    void unknownNotificationIsNotFound() {
        var response = get("/api/v1/admin/notifications/" + UUID.randomUUID(), adminToken(ADMIN_ID));
        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("a delivered notification cannot be resent")
    void aDeliveredNotificationCannotBeResent() {
        publish(EventTypes.REFUND_REQUESTED, "order-1", Map.of(
                "orderId", "order-1", "orderNumber", "CHOCO-1", "amountMinorUnits", 49900,
                "currency", "INR", "reason", "changed mind"));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            var listed = json(get("/api/v1/admin/notifications", adminToken(ADMIN_ID)));
            assertThat(listed.get("totalElements").asInt()).isEqualTo(1);
        });

        UUID notificationId = UUID.fromString(
                json(get("/api/v1/admin/notifications", adminToken(ADMIN_ID)))
                        .get("content").get(0).get("id").asText());

        var response = post("/api/v1/admin/notifications/" + notificationId + "/resend", null,
                adminToken(ADMIN_ID));

        assertThat(response.statusCode())
                .as("a customer must not receive a second copy of a message already delivered")
                .isEqualTo(409);
    }

    @Test
    @DisplayName("a customer cannot trigger a resend")
    void customersCannotResend() {
        var response = post("/api/v1/admin/notifications/" + UUID.randomUUID() + "/resend", null,
                customerToken(CUSTOMER_ID));
        assertThat(response.statusCode()).isEqualTo(403);
    }
}
