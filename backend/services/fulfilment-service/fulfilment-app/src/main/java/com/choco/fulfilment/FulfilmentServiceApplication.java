package com.choco.fulfilment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Fulfilment service: made-to-order preparation, optional ingredient stock, and seller-managed
 * delivery.
 *
 * <p>{@code scanBasePackages} pulls in the shared modules so their auto-configurations and components
 * are registered. Component scanning alone is not enough for persistence: Spring Data discovers
 * repositories from the application class's package by default, so {@code @EnableJpaRepositories} and
 * {@code @EntityScan} explicitly extend the search to {@code com.choco.events.outbox}, where the
 * outbox entity and its repository live. Without that the outbox repository has no bean, the publisher
 * is never created, and the service fails to start with a message about a missing {@code
 * EventPublisher} that points nowhere near the real cause.
 *
 * <p>There is no service-specific properties class here: this service's tunables — the preparation
 * capacity and the proof-of-delivery flag — are read as plain values where the use cases are built, so
 * there is nothing to bind. {@code @ConfigurationPropertiesScan} is kept so a future properties class
 * is picked up without a change to this file.
 */
@SpringBootApplication(scanBasePackages = {"com.choco.fulfilment", "com.choco.web", "com.choco.security",
        "com.choco.events"})
@EntityScan(basePackages = {"com.choco.fulfilment", "com.choco.events.outbox"})
@EnableJpaRepositories(basePackages = {"com.choco.fulfilment", "com.choco.events.outbox"})
@ConfigurationPropertiesScan(basePackages = "com.choco.fulfilment")
public class FulfilmentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FulfilmentServiceApplication.class, args);
    }
}
