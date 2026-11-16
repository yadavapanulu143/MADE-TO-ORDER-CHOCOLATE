package com.choco.events.config;

import com.choco.core.port.Clock;
import com.choco.events.outbox.OutboxEventPublisher;
import com.choco.events.outbox.OutboxEventRepository;
import com.choco.events.outbox.OutboxRelay;
import com.choco.events.outbox.OutboxRelayScheduler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Wires the outbox publisher, relay and the shared broker topology into each producing service.
 */
@AutoConfiguration
@EnableScheduling
public class EventsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TopicExchange chocoEventsExchange() {
        return new TopicExchange(RabbitTopology.EXCHANGE, true, false);
    }

    @Bean
    @ConditionalOnMissingBean
    public TopicExchange chocoDeadLetterExchange() {
        return new TopicExchange(RabbitTopology.DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue chocoDeadLetterQueue() {
        return QueueBuilder.durable(RabbitTopology.DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding chocoDeadLetterBinding(Queue chocoDeadLetterQueue, TopicExchange chocoDeadLetterExchange) {
        return BindingBuilder.bind(chocoDeadLetterQueue).to(chocoDeadLetterExchange)
                .with(RabbitTopology.DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(OutboxEventRepository.class)
    public OutboxEventPublisher outboxEventPublisher(
            OutboxEventRepository repository,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${spring.application.name:unknown-service}") String producer) {
        return new OutboxEventPublisher(repository, objectMapper, producer, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({OutboxEventRepository.class, RabbitTemplate.class})
    public OutboxRelay outboxRelay(
            OutboxEventRepository repository,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            @Value("${choco.outbox.batch-size:100}") int batchSize,
            @Value("${choco.outbox.max-attempts:20}") int maxAttempts) {
        return new OutboxRelay(repository, rabbitTemplate, objectMapper, batchSize, maxAttempts);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(OutboxRelay.class)
    @ConditionalOnProperty(name = "choco.outbox.enabled", havingValue = "true", matchIfMissing = true)
    public OutboxRelayScheduler outboxRelayScheduler(OutboxRelay relay, OutboxEventRepository repository) {
        return new OutboxRelayScheduler(relay, repository);
    }
}
