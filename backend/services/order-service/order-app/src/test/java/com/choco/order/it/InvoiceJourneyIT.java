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
 * Invoice issuance and access, against a real broker, a real database and the real HTTP surface.
 *
 * <p>The invoice is issued from a verified {@code payment.captured}, so the whole path is exercised:
 * the payment event reaches the consumer, the order is confirmed, and the invoice is written with its
 * snapshot of the order. A unit test on the service would not show that the event actually triggers
 * issuance, nor that the unique index on {@code order_id} holds when the same capture is redelivered.
 *
 * <p>Ownership is asserted at the boundary, not just in the service: a second customer presenting
 * another customer's invoice id must receive a {@code 404}, never the document.
 */
@DisplayName("Invoice journey (real broker and database)")
class InvoiceJourneyIT extends AbstractOrderIntegrationTest {

    private static final UUID VARIANT_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-0000000000e1");
    private static final UUID PRODUCT_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-0000000000e1");
    private static final UUID ADDRESS_ID = UUID.fromString("cccccccc-0000-0000-0000-0000000000e1");
    private static final UUID CUSTOMER_ID = UUID.fromString("dddddddd-0000-0000-0000-0000000000e1");
    private static final UUID OTHER_CUSTOMER_ID = UUID.fromString("eeeeeeee-0000-0000-0000-0000000000e1");

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private TopicExchange chocoEventsExchange;

    @Test
    @DisplayName("a verified payment issues one invoice, readable by its owner")
    void capture_issues_an_invoice() {
        Queue issued = bindTestQueue(EventTypes.INVOICE_ISSUED);
        UUID orderId = confirmedOrder();

        // The invoice is issued by the consumer that confirms the order.
        JsonNode invoice = awaitInvoiceForOrder(orderId);
        assertThat(invoice.get("orderId").asText()).isEqualTo(orderId.toString());
        assertThat(invoice.get("invoiceNumber").asText()).startsWith("INV-");
        assertThat(invoice.get("totalMinorUnits").asLong()).isEqualTo(54_900);
        assertThat(invoice.get("lines")).hasSize(1);
        assertThat(invoice.get("lines").get(0).get("productName").asText())
                .isEqualTo("Dark Chocolate Bar");

        // And the fact is on the broker for the notification service to act on.
        String body = receiveBody(issued.getName());
        assertThat(body).isNotNull();
        assertThat(body).contains(orderId.toString());
    }

    @Test
    @DisplayName("a redelivered capture does not issue a second invoice")
    void a_redelivered_capture_is_idempotent() {
        UUID orderId = confirmedOrder();
        JsonNode first = awaitInvoiceForOrder(orderId);

        // The same capture delivered again — a broker retry, or a restart before the ack.
        publishPaymentEvent(EventTypes.PAYMENT_CAPTURED, orderId, capturePayload());

        Awaitility.await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(awaitInvoiceForOrder(orderId).get("id").asText())
                        .as("a duplicate capture must not issue a second invoice")
                        .isEqualTo(first.get("id").asText()));
    }

    @Test
    @DisplayName("a customer cannot read another customer's invoice; it is a 404")
    void another_customer_cannot_read_the_invoice() {
        UUID orderId = confirmedOrder();
        JsonNode invoice = awaitInvoiceForOrder(orderId);
        UUID invoiceId = UUID.fromString(invoice.get("id").asText());

        HttpResponse<String> response = get("/api/v1/invoices/me/" + invoiceId,
                customerToken(OTHER_CUSTOMER_ID));
        assertThat(response.statusCode())
                .as("a cross-customer read must not confirm the invoice exists")
                .isEqualTo(404);
    }

    @Test
    @DisplayName("a customer cannot read another customer's invoice by order id")
    void another_customer_cannot_read_by_order() {
        UUID orderId = confirmedOrder();
        awaitInvoiceForOrder(orderId);

        HttpResponse<String> response = get("/api/v1/invoices/me/by-order/" + orderId,
                customerToken(OTHER_CUSTOMER_ID));
        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("an unauthenticated request cannot read an invoice")
    void unauthenticated_is_refused() {
        UUID orderId = confirmedOrder();
        JsonNode invoice = awaitInvoiceForOrder(orderId);

        HttpResponse<String> response = get("/api/v1/invoices/me/" + invoice.get("id").asText(), null);
        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("the customer's invoice list contains only their own")
    void list_is_scoped_to_the_customer() {
        awaitInvoiceForOrder(confirmedOrder());

        HttpResponse<String> response = get("/api/v1/invoices/me", customerToken(CUSTOMER_ID));
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = json(response);
        assertThat(body.get("totalElements").asLong()).isEqualTo(1);
        assertThat(body.get("content").get(0).get("customerId").asText())
                .isEqualTo(CUSTOMER_ID.toString());
    }

    @Test
    @DisplayName("the seller sees every invoice and can read one")
    void admin_sees_all() {
        UUID orderId = confirmedOrder();
        JsonNode invoice = awaitInvoiceForOrder(orderId);

        HttpResponse<String> list = get("/api/v1/admin/invoices", adminToken(UUID.randomUUID()));
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(json(list).get("totalElements").asLong()).isEqualTo(1);

        HttpResponse<String> one = get("/api/v1/admin/invoices/" + invoice.get("id").asText(),
                adminToken(UUID.randomUUID()));
        assertThat(one.statusCode()).isEqualTo(200);
        assertThat(json(one).get("invoiceNumber").asText())
                .isEqualTo(invoice.get("invoiceNumber").asText());
    }

    @Test
    @DisplayName("a customer cannot reach the administrative invoice surface")
    void customer_is_refused_the_admin_surface() {
        UUID orderId = confirmedOrder();
        JsonNode invoice = awaitInvoiceForOrder(orderId);

        HttpResponse<String> response = get("/api/v1/admin/invoices/" + invoice.get("id").asText(),
                customerToken(CUSTOMER_ID));
        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("the seller can issue the invoice for a confirmed order and it is idempotent")
    void admin_can_issue_by_hand() {
        UUID orderId = confirmedOrder();
        JsonNode issued = awaitInvoiceForOrder(orderId);

        HttpResponse<String> again = post("/api/v1/admin/invoices/order/" + orderId, null,
                adminToken(UUID.randomUUID()));
        assertThat(again.statusCode()).isEqualTo(200);
        assertThat(json(again).get("id").asText()).isEqualTo(issued.get("id").asText());
    }

    @Test
    @DisplayName("an unpaid order has no invoice, and asking for one is not found")
    void unpaid_order_has_no_invoice() {
        UUID orderId = placeAnOrder();

        HttpResponse<String> response = get("/api/v1/invoices/me/by-order/" + orderId,
                customerToken(CUSTOMER_ID));
        assertThat(response.statusCode()).isEqualTo(404);
    }

    // ---------------------------------------------------------------------------------------------

    /** Places an order and confirms it through a real payment.captured, leaving an invoice behind. */
    private UUID confirmedOrder() {
        UUID orderId = placeAnOrder();
        publishPaymentEvent(EventTypes.PAYMENT_CAPTURED, orderId, capturePayload());
        Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> assertThat(orderStatus(orderId)).isEqualTo("CONFIRMED"));
        return orderId;
    }

    private Map<String, Object> capturePayload() {
        return Map.of("providerPaymentId", "pay_inv_" + UUID.randomUUID(), "providerOrderId",
                "order_inv_" + UUID.randomUUID(), "amountMinorUnits", 54_900, "currency", "INR");
    }

    private JsonNode awaitInvoiceForOrder(UUID orderId) {
        Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    HttpResponse<String> response = get("/api/v1/invoices/me/by-order/" + orderId,
                            customerToken(CUSTOMER_ID));
                    assertThat(response.statusCode()).isEqualTo(200);
                });
        return json(get("/api/v1/invoices/me/by-order/" + orderId, customerToken(CUSTOMER_ID)));
    }

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
                customerToken(CUSTOMER_ID), "invoice-" + UUID.randomUUID());
        assertThat(placed.statusCode()).isEqualTo(201);
        return UUID.fromString(json(placed).get("id").asText());
    }

    private String orderStatus(UUID orderId) {
        HttpResponse<String> response = get("/api/v1/orders/me/" + orderId, customerToken(CUSTOMER_ID));
        assertThat(response.statusCode()).isEqualTo(200);
        return json(response).get("status").asText();
    }

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
