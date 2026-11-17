package com.choco.notification.adapter.in.messaging;

import com.choco.events.envelope.EventTypes;
import com.choco.notification.application.command.RecordContactCommand;
import com.choco.notification.application.service.ContactProjectionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Maintains the customer-contact projection from the identity service's events.
 *
 * <p>Three events feed it: {@code customer.registered} carries both channels, {@code customer.verified}
 * carries neither (it exists so the service knows an account is now usable), and
 * {@code customer.contact-changed} carries a fresh snapshot. Registration and a contact change are the
 * two that actually write; verification is handled so the queue binding is complete and a future need
 * for it does not require a topology change.
 *
 * <p><b>The message is read as raw JSON.</b> Binding it to this service's classes would couple the
 * consumer to the producer's Java types, so a rename over there would be a runtime failure here rather
 * than a contract change. The envelope's payload is read into a local record that states exactly the
 * fields this service uses, and unknown fields are ignored so identity can add one without breaking
 * this consumer.
 *
 * <p><b>Idempotent.</b> The same event may be delivered twice; the projection upsert simply writes the
 * same values again, and the merge keeps fields an event does not carry.
 */
@Component
class CustomerContactListener {

    private static final Logger log = LoggerFactory.getLogger(CustomerContactListener.class);

    private final ContactProjectionService projectionService;
    private final ObjectMapper objectMapper;

    CustomerContactListener(ContactProjectionService projectionService, ObjectMapper objectMapper) {
        this.projectionService = projectionService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = NotificationMessagingConfiguration.CONTACTS_QUEUE)
    void onMessage(Message message) {
        String eventType = (String) message.getMessageProperties().getHeaders().get("eventType");
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        if (eventType == null) {
            log.warn("Received a contact message with no eventType header; ignoring");
            return;
        }
        switch (eventType) {
            case EventTypes.CUSTOMER_REGISTERED, EventTypes.CUSTOMER_CONTACT_CHANGED ->
                    record(readPayload(body, ContactPayload.class));
            case EventTypes.CUSTOMER_VERIFIED ->
                    // No address on this event. The projection is already populated from registration;
                    // this is accepted so the binding is complete and a redelivery is a no-op.
                    log.debug("Ignoring {} for the contact projection; it carries no address", eventType);
            default -> log.debug("Ignoring event {} on the contacts queue", eventType);
        }
    }

    private void record(ContactPayload payload) {
        if (payload.aggregateId() == null) {
            log.warn("Contact event carried no aggregateId; cannot record a projection");
            return;
        }
        UUID customerId;
        try {
            customerId = UUID.fromString(payload.aggregateId());
        } catch (IllegalArgumentException e) {
            log.warn("Contact event carried a non-UUID customer id '{}'", payload.aggregateId());
            return;
        }
        projectionService.record(new RecordContactCommand(customerId, payload.email(), payload.mobile(),
                payload.fullName(), payload.occurredAt()));
    }

    private <T> T readPayload(String body, Class<T> type) {
        try {
            Map<String, Object> envelope = objectMapper.readValue(body, new TypeReference<>() {
            });
            return objectMapper.convertValue(envelope.get("payload"), type);
        } catch (Exception ex) {
            // Rethrown so the container's retry and dead-letter policy applies: a malformed message is
            // not something to acknowledge and forget, it is something an operator needs to see.
            throw new IllegalArgumentException("Unreadable contact event payload", ex);
        }
    }

    /** The wire shape of an identity contact event, narrowed to the fields this service uses. */
    private record ContactPayload(
            String aggregateId,
            String email,
            String mobile,
            String fullName,
            java.time.Instant occurredAt) {
    }
}
