package com.choco.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Order service: carts, orders, status history, cancellations and refund requests.
 *
 * <p>{@code scanBasePackages} pulls in the shared modules so their auto-configurations and components
 * are registered. Component scanning alone is not enough for persistence: Spring Data discovers
 * repositories from the application class's package by default, so {@code @EnableJpaRepositories} and
 * {@code @EntityScan} explicitly extend the search to {@code com.choco.events.outbox}, where the
 * outbox entity and its repository live. Without that the outbox repository has no bean, the publisher
 * is never created, and the service fails to start with a message about a missing {@code
 * EventPublisher} that points nowhere near the real cause.
 *
 * <p>{@code @ConfigurationPropertiesScan} binds {@code DownstreamProperties} so a missing downstream
 * address fails at startup rather than at the first checkout.
 */
@SpringBootApplication(scanBasePackages = {"com.choco.order", "com.choco.web", "com.choco.security",
        "com.choco.events"})
@EntityScan(basePackages = {"com.choco.order", "com.choco.events.outbox"})
@EnableJpaRepositories(basePackages = {"com.choco.order", "com.choco.events.outbox"})
@ConfigurationPropertiesScan(basePackages = "com.choco.order")
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
