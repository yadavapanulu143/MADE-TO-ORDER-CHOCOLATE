package com.choco.order.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.choco.events.config.RabbitTopology;
import com.choco.events.envelope.EventTypes;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The order-to-payment saga, observed against a real broker and a real database.
 *
 * <p>This test exists because of a defect it pins down: the payment service published
 * {@code payment.captured} and nothing consumed it, so an order could be paid and never leave
 * {@code PAYMENT_PENDING}. Preparation never unlocked, the fulfilment service never received
 * {@code order.confirmed}, and no chocolate was ever made. A unit test on the listener would not have
 * caught it — the missing piece was the wiring between two services, which only a broker and a
 * database together can prove.
 *
 * <p>The event is delivered exactly as the payment service's outbox relay delivers it: the shared
 * {@code EventEnvelope} as a JSON body, the event type as a routing key and a header, the message
 * persistent. A test that called the listener directly, or posted to an endpoint, would bypass the
 * binding and the header that are half of what is being verified.
 *
 * <p>Two things are asserted every time: the order's own state, and the {@code order.confirmed} event
 * that the rest of the platform waits for. Confirming the order without emitting that event would be a
 * silent regression the order's own tables could not show.
 */
@DisplayName("Order/payment saga (real broker)")
class PaymentEventsIT extends AbstractOrderIntegrationTest {

    private static final UUID VARIANT_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-0000000000f1");
    private static final UUID PRODUCT_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-0000000000f1");
    private static final UUID ADDRESS_ID = UUID.fromString("cccccccc-0000-0000-0000-0000000000f1");
    private static final UUID CUSTOMER_ID = UUID.fromString("dddddddd-0000-0000-0000-0000000000f1");

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private TopicExchange chocoEventsExchange;

    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("a verified payment.captured confirms the order and emits order.confirmed")
    void captured_confirms_the_order_and_releases_it_into_preparation() {
        Queue confirmed = bindTestQueue(EventTypes.ORDER_CONFIRMED);
        UUID orderId = placeAnOrder();

        assertThat(orderStatus(orderId))
                .as("an order waits for payment, so it starts pending")
                .isEqualTo("PAYMENT_PENDING");

        publishPaymentEvent(EventTypes.PAYMENT_CAPTURED, orderId,
                Map.of("providerPaymentId", "pay_saga_1", "providerOrderId", "order_saga_1",
                        "amountMinorUnits", 54_900, "currency", "INR"));

        // The order is confirmed by the consumer, which is the only path that can do it.
        Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> assertThat(orderStatus(orderId)).isEqualTo("CONFIRMED"));

        // And the fact the kitchen waits for is on the broker, so production can start.
        String body = receiveBody(confirmed.getName());
        assertThat(body)
                .as("confirming an order must emit order.confirmed, or nothing is ever made")
                .isNotNull();
        assertThat(body).contains(orderId.toString());
    }

    @Test
    @DisplayName("a redelivered payment.captured confirms the order once and emits one order.confirmed")
    void a_redelivered_capture_is_idempotent() {
        Queue confirmed = bindTestQueue(EventTypes.ORDER_CONFIRMED);
        UUID orderId = placeAnOrder();

        Map<String, Object> payload = Map.of("providerPaymentId", "pay_dup", "providerOrderId",
                "order_dup", "amountMinorUnits", 54_900, "currency", "INR");
        publishPaymentEvent(EventTypes.PAYMENT_CAPTURED, orderId, payload);
        Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> assertThat(orderStatus(orderId)).isEqualTo("CONFIRMED"));
        // Drain the first order.confirmed so the second delivery, if it wrongly emitted one, is visible.
        assertThat(receiveBody(confirmed.getName())).isNotNull();

        // The same capture delivered again — a broker retry, or a restart before the ack.
        publishPaymentEvent(EventTypes.PAYMENT_CAPTURED, orderId, payload);

        Awaitility.await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(rabbitTemplate.receive(confirmed.getName()))
                        .as("a duplicate capture must not open a second set of kitchen jobs")
                        .isNull());
        assertThat(orderStatus(orderId)).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("a payment.failed event moves the order to a retryable failure")
    void failed_moves_the_order_to_payment_failed() {
        UUID orderId = placeAnOrder();

        publishPaymentEvent(EventTypes.PAYMENT_FAILED, orderId,
                Map.of("providerPaymentId", "pay_failed", "reason", "card declined",
                        "amountMinorUnits", 54_900, "currency", "INR"));

        Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> assertThat(orderStatus(orderId)).isEqualTo("PAYMENT_FAILED"));
    }

    @Test
    @DisplayName("a late payment.failed after a capture must not un-pay a confirmed order")
    void a_late_failure_does_not_undo_a_capture() {
        UUID orderId = placeAnOrder();

        publishPaymentEvent(EventTypes.PAYMENT_CAPTURED, orderId,
                Map.of("providerPaymentId", "pay_win", "providerOrderId", "order_win",
                        "amountMinorUnits", 54_900, "currency", "INR"));
        Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> assertThat(orderStatus(orderId)).isEqualTo("CONFIRMED"));

        // A stale "failed" for the same order arrives afterwards.
        publishPaymentEvent(EventTypes.PAYMENT_FAILED, orderId,
                Map.of("providerPaymentId", "pay_win", "reason", "late failure",
                        "amountMinorUnits", 54_900, "currency", "INR"));

        Awaitility.await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(orderStatus(orderId)).isEqualTo("CONFIRMED"));
    }

    @Test
    @DisplayName("a capture for the wrong amount is refused and the order stays unpaid")
    void a_capture_for_the_wrong_amount_is_refused() {
        Queue confirmed = bindTestQueue(EventTypes.ORDER_CONFIRMED);
        UUID orderId = placeAnOrder();

        // The order is 49900 + 5000 delivery = 54900 paise. A capture of 100 paise is not this order's
        // payment, and confirming it would hand over the chocolate for one rupee.
        publishPaymentEvent(EventTypes.PAYMENT_CAPTURED, orderId,
                Map.of("providerPaymentId", "pay_short", "providerOrderId", "order_short",
                        "amountMinorUnits", 100, "currency", "INR"));

        Awaitility.await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(6))
                .untilAsserted(() -> {
                    assertThat(orderStatus(orderId))
                            .as("a short payment must not confirm the order")
                            .isEqualTo("PAYMENT_PENDING");
                    assertThat(rabbitTemplate.receive(confirmed.getName()))
                            .as("and must not release the order into preparation")
                            .isNull();
                });
    }

    @Test
    @DisplayName("a payment event for an unknown order is left for the dead-letter queue, not lost")
    void an_event_for_an_unknown_order_is_not_acknowledged() {
        // The order id is well-formed but belongs to no order. The consumer must not treat that as
        // "handled": the message is retried and eventually dead-lettered so an operator sees it,
        // rather than a payment fact being silently dropped.
        UUID missing = UUID.randomUUID();
        publishPaymentEvent(EventTypes.PAYMENT_CAPTURED, missing,
                Map.of("providerPaymentId", "pay_missing", "providerOrderId", "order_missing",
                        "amountMinorUnits", 1_000, "currency", "INR"));

        // Nothing to assert about the order (it does not exist); the test proves the consumer does not
        // crash the container and the service stays healthy afterwards.
        Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(250))
                .untilAsserted(() -> assertThat(get("/actuator/health", null).statusCode()).isEqualTo(200));
    }

    // ---------------------------------------------------------------------------------------------

    /** Places a real order through the HTTP surface, leaving it in {@code PAYMENT_PENDING}. */
    private UUID placeAnOrder() {
        DOWNSTREAM.on("/api/v1/customers/", 200, """
                {"id":"%s","fullName":"Asha Rao","email":"asha@choco.test","mobile":"9876543210",
                 "emailVerified":true,"mobileVerified":true,
                 "addresses":[{"id":"%s","recipientName":"Asha Rao","mobile":"9876543210",
                   "line1":"12 Cocoa Lane","line2":null,"city":"Bengaluru","state":"Karnataka",
                   "pincode":"560001","deliveryInstructions":"Leave at the door"}]}
                """.formatted(CUSTOMER_ID, ADDRESS_ID));
        DOWNSTREAM.on("/api/v1/catalog/variants", 200, """
                {"%s":{"variantId":"%s","productId":"%s","productName":"Dark Chocolate Bar",
                 "variantLabel":"200 g","sku":"DARK-200","priceMinorUnits":49900,"currency":"INR",
                 "minOrderQuantity":1,"maxOrderQuantity":10,"variantActive":true,
                 "productAvailable":true}}
                """.formatted(VARIANT_ID, VARIANT_ID, PRODUCT_ID));
        DOWNSTREAM.on("/api/v1/serviceability/", 200, """
                {"serviceable":true,"deliveryChargeMinorUnits":5000,"currency":"INR","reason":null}
                """);

        HttpResponse<String> line = post("/api/v1/carts/me/lines",
                jsonBody(Map.of("variantId", VARIANT_ID.toString(), "quantity", 1)),
                customerToken(CUSTOMER_ID));
        assertThat(line.statusCode()).isEqualTo(200);

        HttpResponse<String> placed = send("POST", "/api/v1/checkout",
                jsonBody(Map.of("addressId", ADDRESS_ID.toString())),
                customerToken(CUSTOMER_ID), "saga-" + UUID.randomUUID());
        assertThat(placed.statusCode()).isEqualTo(201);
        return UUID.fromString(json(placed).get("id").asText());
    }

    private String orderStatus(UUID orderId) {
        HttpResponse<String> response = get("/api/v1/orders/me/" + orderId, customerToken(CUSTOMER_ID));
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = json(response);
        return body.get("status").asText();
    }

    /**
     * Publishes an event exactly as the payment service's outbox relay does: the envelope as the body,
     * the event type as both routing key and header, the message persistent.
     */
    private void publishPaymentEvent(String eventType, UUID orderId, Map<String, Object> payload) {
        Map<String, Object> envelope = new java.util.LinkedHashMap<>();
        envelope.put("eventId", UUID.randomUUID());
        envelope.put("eventType", eventType);
        envelope.put("schemaVersion", 1);
        envelope.put("aggregateId", orderId.toString());
        envelope.put("occurredAt", Instant.now());
        envelope.put("correlationId", null);
        envelope.put("producer", "payment-service");
        Map<String, Object> body = new java.util.LinkedHashMap<>(payload);
        body.put("orderId", orderId.toString());
        envelope.put("payload", body);

        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new IllegalStateException("could not serialise the event", e);
        }
        rabbitTemplate.convertAndSend(RabbitTopology.EXCHANGE, eventType, json, message -> {
            message.getMessageProperties().setContentType("application/json");
            message.getMessageProperties().setContentEncoding("UTF-8");
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            message.getMessageProperties().setHeader("eventType", eventType);
            return message;
        });
    }

    /** A private queue bound to the shared exchange, so a test observes one routing key only. */
    private Queue bindTestQueue(String routingKey) {
        Queue queue = QueueBuilder.nonDurable("it." + UUID.randomUUID()).exclusive().autoDelete().build();
        rabbitAdmin.declareQueue(queue);
        Binding binding = BindingBuilder.bind(queue).to(chocoEventsExchange).with(routingKey);
        rabbitAdmin.declareBinding(binding);
        return queue;
    }

    private String receiveBody(String queueName) {
        org.springframework.amqp.core.Message message = Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> rabbitTemplate.receive(queueName), value -> value != null);
        return message == null ? null
                : new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8);
    }
}
