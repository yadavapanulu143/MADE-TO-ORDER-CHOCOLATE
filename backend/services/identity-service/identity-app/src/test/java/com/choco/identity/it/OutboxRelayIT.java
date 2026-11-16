package com.choco.identity.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.choco.events.config.RabbitTopology;
import com.choco.events.envelope.EventTypes;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Proves the transactional outbox actually delivers events to RabbitMQ.
 *
 * <p>The outbox exists so a business transaction and its event are committed together, with the
 * relay publishing afterwards. That design is only worth anything if the relay genuinely reaches the
 * broker, which is what this test observes: a real RabbitMQ, a real queue bound to the exchange, and
 * a registration over HTTP that must end with a message in that queue.
 *
 * <p>A mock would prove nothing here — the failure this guards against is a payload that will not
 * serialise, an exchange name that does not match, or a converter that rejects the body, all of
 * which only appear against a live broker.
 */
@DisplayName("Outbox to RabbitMQ (real broker)")
class OutboxRelayIT extends AbstractIdentityIntegrationTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private TopicExchange chocoEventsExchange;

    /**
     * Declares a private queue bound to the shared exchange for the lifetime of this test class.
     *
     * <p>A random queue name keeps the test from competing with any other consumer, and an
     * exclusive, auto-delete queue leaves no residue in the broker between runs.
     */
    private Queue bindTestQueue(String routingKey) {
        Queue queue = QueueBuilder.nonDurable("it." + UUID.randomUUID()).exclusive().autoDelete().build();
        rabbitAdmin.declareQueue(queue);
        Binding binding = BindingBuilder.bind(queue).to(chocoEventsExchange).with(routingKey);
        rabbitAdmin.declareBinding(binding);
        return queue;
    }

    @Test
    @DisplayName("a registration publishes customer.registered to the broker")
    void registration_event_reaches_the_broker() {
        Queue queue = bindTestQueue(EventTypes.CUSTOMER_REGISTERED);

        registerVerifiedCustomer("outbox");

        String body = receiveBody(queue.getName());
        assertThat(body)
                .as("the relay must publish the registration event to the exchange")
                .isNotNull();
        assertThat(body).contains(EventTypes.CUSTOMER_REGISTERED);
        // The envelope, not a bare payload: consumers deduplicate on eventId before parsing.
        assertThat(body).contains("eventId").contains("aggregateId");
    }

    @Test
    @DisplayName("the outbox row is marked published once the broker has accepted it")
    void outbox_row_is_marked_published() {
        Queue queue = bindTestQueue(EventTypes.CUSTOMER_REGISTERED);

        registerVerifiedCustomer("outbox-marked");

        Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    try (var connection = POSTGRES.createConnection("");
                         var statement = connection.createStatement();
                         var rows = statement.executeQuery("""
                                 SELECT count(*) FROM outbox_event
                                 WHERE event_type = 'customer.registered' AND published_at IS NULL
                                 """)) {
                        rows.next();
                        assertThat(rows.getLong(1))
                                .as("no registration event should remain unpublished")
                                .isZero();
                    }
                });
        assertThat(receiveBody(queue.getName())).isNotNull();
    }

    @Test
    @DisplayName("the routing key on the message is the event type")
    void routing_key_is_the_event_type() {
        Queue queue = bindTestQueue(EventTypes.CUSTOMER_REGISTERED);

        registerVerifiedCustomer("outbox-routing");

        // Binding on the exact routing key is itself the assertion: a message published under a
        // different key would not arrive in this queue at all.
        org.springframework.amqp.core.Message message = Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> rabbitTemplate.receive(queue.getName()), value -> value != null);
        assertThat(message).isNotNull();
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
