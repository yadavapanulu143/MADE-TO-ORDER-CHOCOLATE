package com.choco.fulfilment.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.choco.events.config.RabbitTopology;
import com.choco.events.envelope.EventEnvelope;
import com.choco.events.envelope.EventTypes;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The fulfilment service's public HTTP surface and its event hand-over, exercised end to end.
 *
 * <p>Every request goes through the real filter chain, the real controllers, the real use cases and a
 * real PostgreSQL. The hand-over arrives as a real envelope published to a real RabbitMQ, consumed by
 * the real listener, exactly as the order service's outbox would deliver it.
 *
 * <p>The point of these tests is the wiring: a controller that forgets the ownership check, a route
 * reachable without a token, an admin permission that is not actually enforced, or a status the
 * aggregate should have refused. The unit tests already prove the rules; this proves the rules are
 * reachable and enforced over the wire.
 */
@DisplayName("Fulfilment journey (real PostgreSQL, real broker, real security)")
class FulfilmentJourneyIT extends AbstractFulfilmentIntegrationTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000001");
    private static final UUID OTHER_CUSTOMER_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000002");
    private static final UUID ADMIN_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000001");
    private static final UUID PRODUCT_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");
    private static final UUID VARIANT_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private TopicExchange chocoEventsExchange;

    /** Publishes the same envelope the order service's outbox would, so the listener sees the real wire. */
    private void publishOrderConfirmed(String orderId, int quantity) {
        publishOrderConfirmed(orderId, CUSTOMER_ID, quantity);
    }

    private void publishOrderConfirmed(String orderId, UUID customerId, int quantity) {
        EventEnvelope envelope = EventEnvelope.of(UUID.randomUUID(), EventTypes.ORDER_CONFIRMED, 1,
                orderId, Instant.now(), "it-correlation", "order-service",
                Map.ofEntries(
                        Map.entry("orderId", orderId),
                        Map.entry("customerId", customerId.toString()),
                        Map.entry("orderNumber", "CHOCO-2026001-1"),
                        Map.entry("customerName", "Asha Rao"),
                        Map.entry("paymentId", "pay_test_1"),
                        Map.entry("deliveryChargeMinorUnits", 5000L),
                        Map.entry("currency", "INR"),
                        Map.entry("deliveryAddress", Map.of(
                                "recipientName", "Asha Rao",
                                "mobile", "9876543210",
                                "line1", "12 Cocoa Lane",
                                "city", "Bengaluru",
                                "state", "Karnataka",
                                "pincode", "560001",
                                "deliveryInstructions", "Leave at the door")),
                        Map.entry("lines", List.of(Map.of(
                                "productId", PRODUCT_ID.toString(),
                                "variantId", VARIANT_ID.toString(),
                                "productName", "Dark Chocolate Bar",
                                "variantLabel", "200 g",
                                "quantity", quantity)))));
        rabbitTemplate.convertAndSend(RabbitTopology.EXCHANGE, EventTypes.ORDER_CONFIRMED,
                serialise(envelope), message -> {
                    message.getMessageProperties().setContentType("application/json");
                    message.getMessageProperties().setContentEncoding("UTF-8");
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    message.getMessageProperties().setHeader("eventType", EventTypes.ORDER_CONFIRMED);
                    return message;
                });
    }

    private void publishOrderCancelled(String orderId) {
        EventEnvelope envelope = EventEnvelope.of(UUID.randomUUID(), EventTypes.ORDER_CANCELLED, 1,
                orderId, Instant.now(), "it-correlation", "order-service",
                Map.of("orderId", orderId, "reason", "customer changed their mind"));
        rabbitTemplate.convertAndSend(RabbitTopology.EXCHANGE, EventTypes.ORDER_CANCELLED,
                serialise(envelope), message -> {
                    message.getMessageProperties().setContentType("application/json");
                    message.getMessageProperties().setHeader("eventType", EventTypes.ORDER_CANCELLED);
                    return message;
                });
    }

    private String serialise(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("could not serialise the event", e);
        }
    }

    /** Waits until the listener has opened a job for the order, then returns the queue listing. */
    private JsonNode awaitJobsFor(String orderId, String adminToken, int expected) {
        Awaitility.await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    HttpResponse<String> response = get(
                            "/api/v1/admin/fulfilment/production/orders/" + orderId, adminToken);
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(json(response)).hasSize(expected);
                });
        return json(get("/api/v1/admin/fulfilment/production/orders/" + orderId, adminToken));
    }

    @Nested
    @DisplayName("made-to-order hand-over")
    class HandOver {

        @Test
        @DisplayName("a confirmed order opens a preparation job the seller can see")
        void confirmedOrderOpensJob() {
            String orderId = UUID.randomUUID().toString();
            publishOrderConfirmed(orderId, 3);

            JsonNode jobs = awaitJobsFor(orderId, adminToken(ADMIN_ID), 1);
            assertThat(jobs.get(0).get("status").asText()).isEqualTo("RECEIVED");
            assertThat(jobs.get(0).get("quantity").asInt()).isEqualTo(3);
            assertThat(jobs.get(0).get("orderNumber").asText()).isEqualTo("CHOCO-2026001-1");
        }

        @Test
        @DisplayName("a redelivered confirmation does not open a second job")
        void redeliveryIsIdempotent() {
            String orderId = UUID.randomUUID().toString();
            publishOrderConfirmed(orderId, 1);
            awaitJobsFor(orderId, adminToken(ADMIN_ID), 1);
            publishOrderConfirmed(orderId, 1);

            // Give the second message time to be consumed, then assert nothing changed.
            Awaitility.await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5))
                    .untilAsserted(() -> assertThat(json(get(
                            "/api/v1/admin/fulfilment/production/orders/" + orderId,
                            adminToken(ADMIN_ID)))).hasSize(1));
        }

        @Test
        @DisplayName("the seller walks a job from received to ready for dispatch, which opens a delivery")
        void sellerWalksTheWholePreparation() {
            String orderId = UUID.randomUUID().toString();
            publishOrderConfirmed(orderId, 1);
            JsonNode jobs = awaitJobsFor(orderId, adminToken(ADMIN_ID), 1);
            String jobId = jobs.get(0).get("id").asText();
            String token = adminToken(ADMIN_ID);

            for (String action : List.of("ACKNOWLEDGE", "START", "COMPLETE", "PACKAGING",
                    "READY_FOR_DISPATCH")) {
                HttpResponse<String> response = post(
                        "/api/v1/admin/fulfilment/production/jobs/" + jobId + "/progress",
                        jsonBody(Map.of("action", action)), token);
                assertThat(response.statusCode())
                        .as("action %s should be accepted", action).isEqualTo(200);
            }

            JsonNode job = json(get("/api/v1/admin/fulfilment/production/jobs/" + jobId, token));
            assertThat(job.get("status").asText()).isEqualTo("READY_FOR_DISPATCH");

            JsonNode delivery = json(get("/api/v1/admin/fulfilment/deliveries/orders/" + orderId, token));
            assertThat(delivery.get("status").asText()).isEqualTo("PENDING");
            assertThat(delivery.get("pincode").asText()).isEqualTo("560001");
        }

        @Test
        @DisplayName("assigning the delivery publishes a delivery-assigned event the customer is told about")
        void assigningPublishesAnEvent() {
            String orderId = UUID.randomUUID().toString();
            publishOrderConfirmed(orderId, 1);
            JsonNode jobs = awaitJobsFor(orderId, adminToken(ADMIN_ID), 1);
            String jobId = jobs.get(0).get("id").asText();
            String token = adminToken(ADMIN_ID);

            for (String action : List.of("ACKNOWLEDGE", "START", "COMPLETE", "PACKAGING",
                    "READY_FOR_DISPATCH")) {
                post("/api/v1/admin/fulfilment/production/jobs/" + jobId + "/progress",
                        jsonBody(Map.of("action", action)), token);
            }

            HttpResponse<String> assigned = post(
                    "/api/v1/admin/fulfilment/deliveries/orders/" + orderId + "/assign",
                    jsonBody(Map.of("deliveryPersonName", "Ravi", "deliveryPersonPhone", "9000000000")),
                    token);
            assertThat(assigned.statusCode()).isEqualTo(200);
            assertThat(json(assigned).get("status").asText()).isEqualTo("ASSIGNED");

            // The whole point of the assignment is that the customer is told. The use case must drain
            // the aggregate's event and the outbox relay must carry it to the broker; asserting only on
            // the HTTP response would miss an assignment that is saved but never announced.
            assertThat(receiveEvent(EventTypes.DELIVERY_ASSIGNED, orderId))
                    .as("the delivery-assigned event must reach the broker")
                    .isTrue();
        }

        @Test
        @DisplayName("an illegal preparation jump is refused with a 4xx, not silently applied")
        void illegalJumpIsRefused() {
            String orderId = UUID.randomUUID().toString();
            publishOrderConfirmed(orderId, 1);
            JsonNode jobs = awaitJobsFor(orderId, adminToken(ADMIN_ID), 1);
            String jobId = jobs.get(0).get("id").asText();

            HttpResponse<String> response = post(
                    "/api/v1/admin/fulfilment/production/jobs/" + jobId + "/progress",
                    jsonBody(Map.of("action", "READY_FOR_DISPATCH")), adminToken(ADMIN_ID));

            assertThat(response.statusCode()).isBetween(400, 499);
        }

        @Test
        @DisplayName("a cancelled order abandons its jobs")
        void cancelledOrderAbandonsJobs() {
            String orderId = UUID.randomUUID().toString();
            publishOrderConfirmed(orderId, 1);
            awaitJobsFor(orderId, adminToken(ADMIN_ID), 1);

            publishOrderCancelled(orderId);

            Awaitility.await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(200))
                    .untilAsserted(() -> assertThat(json(get(
                            "/api/v1/admin/fulfilment/production/orders/" + orderId,
                            adminToken(ADMIN_ID))).get(0).get("status").asText())
                            .isEqualTo("CANCELLED"));
        }
    }

    @Nested
    @DisplayName("authorization")
    class Authorization {

        @Test
        @DisplayName("an unauthenticated request is rejected")
        void unauthenticatedIsRejected() {
            assertThat(get("/api/v1/admin/fulfilment/production/jobs", null).statusCode())
                    .isIn(401, 403);
        }

        @Test
        @DisplayName("a customer cannot reach the admin surface")
        void customerCannotReachAdmin() {
            assertThat(get("/api/v1/admin/fulfilment/production/jobs", customerToken(CUSTOMER_ID))
                    .statusCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("an admin without the production permission cannot advance a job")
        void adminWithoutPermissionCannotAdvance() {
            String orderId = UUID.randomUUID().toString();
            publishOrderConfirmed(orderId, 1);
            JsonNode jobs = awaitJobsFor(orderId, adminToken(ADMIN_ID), 1);
            String jobId = jobs.get(0).get("id").asText();

            HttpResponse<String> response = post(
                    "/api/v1/admin/fulfilment/production/jobs/" + jobId + "/progress",
                    jsonBody(Map.of("action", "ACKNOWLEDGE")), unpermissionedAdminToken(ADMIN_ID));

            assertThat(response.statusCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("changing delivery coverage needs the settings permission, not just a delivery one")
        void coverageNeedsSettingsPermission() {
            String deliveryOnly = tokenWithPermissions(CUSTOMER_ID, "delivery:write");
            HttpResponse<String> response = post("/api/v1/admin/fulfilment/serviceable-areas",
                    jsonBody(Map.of("pincodePrefix", "560001", "areaName", "MG Road",
                            "deliveryChargeMinorUnits", 5000)), deliveryOnly);

            assertThat(response.statusCode()).isEqualTo(403);
        }
    }

    @Nested
    @DisplayName("customer tracking")
    class CustomerTracking {

        @Test
        @DisplayName("a customer sees only their own order's preparation progress")
        void customerSeesOwnProgress() {
            String orderId = UUID.randomUUID().toString();
            publishOrderConfirmed(orderId, 1);
            awaitJobsFor(orderId, adminToken(ADMIN_ID), 1);

            HttpResponse<String> own = get(
                    "/api/v1/fulfilment/me/orders/" + orderId + "/preparation",
                    customerToken(CUSTOMER_ID));
            assertThat(own.statusCode()).isEqualTo(200);
            assertThat(json(own)).hasSize(1);

            HttpResponse<String> other = get(
                    "/api/v1/fulfilment/me/orders/" + orderId + "/preparation",
                    customerToken(OTHER_CUSTOMER_ID));
            assertThat(other.statusCode()).isIn(403, 404);
        }
    }

    @Nested
    @DisplayName("serviceability")
    class Serviceability {

        @Test
        @DisplayName("an uncovered PIN code is answered, not refused as an error")
        void uncoveredIsAnswered() {
            HttpResponse<String> response = get("/api/v1/serviceability/560001", customerToken(CUSTOMER_ID));

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(json(response).get("serviceable").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("the serviceability probe answers an anonymous visitor, matching the gateway")
        void anonymousIsAnswered() {
            // The gateway lists this path as public and the storefront shows a "Delivery areas" page
            // before sign-in. If the service keeps it protected the page returns 401 through the
            // gateway even though every other public path works, which is exactly what happened.
            HttpResponse<String> response = get("/api/v1/serviceability/560001", null);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(json(response).get("serviceable").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("an anonymous visitor still cannot reach the administrative kitchen")
        void anonymousCannotReachAdmin() {
            assertThat(get("/api/v1/admin/fulfilment/production/jobs", null).statusCode()).isIn(401, 403);
            assertThat(get("/api/v1/admin/fulfilment/serviceable-areas", null).statusCode()).isIn(401, 403);
        }

        @Test
        @DisplayName("a covered PIN code is quoted at the configured charge")
        void coveredIsQuoted() {
            post("/api/v1/admin/fulfilment/serviceable-areas",
                    jsonBody(Map.of("pincodePrefix", "560001", "areaName", "MG Road",
                            "deliveryChargeMinorUnits", 4900)), adminToken(ADMIN_ID));

            JsonNode quote = json(get("/api/v1/serviceability/560001", customerToken(CUSTOMER_ID)));
            assertThat(quote.get("serviceable").asBoolean()).isTrue();
            assertThat(quote.get("deliveryChargeMinorUnits").asLong()).isEqualTo(4900);
        }
    }

    /**
     * Waits for an event of the given type for the given aggregate to appear on the broker.
     *
     * <p>A throwaway queue is bound to the event's routing key and the raw body is read, exactly as
     * {@code OutboxRelayIT} does in the identity suite. This proves the event left the service — the
     * exact seam a use case that saves an aggregate but never drains its domain events would leave
     * broken — rather than only that the HTTP call returned 200.
     */
    private boolean receiveEvent(String eventType, String aggregateId) {
        Queue queue = QueueBuilder.nonDurable("it." + UUID.randomUUID()).exclusive().autoDelete().build();
        rabbitAdmin.declareQueue(queue);
        rabbitAdmin.declareBinding(BindingBuilder.bind(queue).to(chocoEventsExchange).with(eventType));
        try {
            org.springframework.amqp.core.Message message = Awaitility.await()
                    .atMost(Duration.ofSeconds(20))
                    .pollInterval(Duration.ofMillis(200))
                    .until(() -> rabbitTemplate.receive(queue.getName()), value -> value != null);
            if (message == null) {
                return false;
            }
            String body = new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8);
            return body.contains(aggregateId);
        } catch (org.awaitility.core.ConditionTimeoutException timedOut) {
            return false;
        } finally {
            rabbitAdmin.deleteQueue(queue.getName());
        }
    }

    /** A token carrying exactly one permission, for the authorization checks. */
    private String tokenWithPermissions(UUID subject, String permission) {
        Instant now = Instant.now();
        org.springframework.security.oauth2.jwt.JwtClaimsSet claims =
                org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                        .subject(subject.toString())
                        .issuer("choco-identity")
                        .issuedAt(now)
                        .expiresAt(now.plus(Duration.ofMinutes(15)))
                        .claim("email", "staff@choco.test")
                        .claim("role", com.choco.security.principal.Roles.ADMIN)
                        .claim("permissions", List.of(permission))
                        .build();
        return jwtEncoder.encode(
                        org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(claims))
                .getTokenValue();
    }
}
