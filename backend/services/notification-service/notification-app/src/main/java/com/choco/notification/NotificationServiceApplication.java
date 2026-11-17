package com.choco.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Notification service: turns the platform's business events into customer and seller messages.
 *
 * <p>This service owns no business fact. It observes events the other services already publish and
 * records what it did about them. That is why it is a consumer of everything and an authority over
 * nothing: an order or a payment is never affected by whether a message went out, and a message is
 * never a reason to fail a business operation.
 *
 * <p>{@code scanBasePackages} pulls in the shared modules so their auto-configurations and components
 * are registered. Component scanning alone is not enough for persistence: Spring Data discovers
 * repositories from the application class's package by default, so {@code @EnableJpaRepositories} and
 * {@code @EntityScan} explicitly extend the search to {@code com.choco.events.outbox}, where the outbox
 * entity and its repository live. Without that the outbox repository has no bean, the publisher is
 * never created, and the service fails to start with a message about a missing {@code EventPublisher}
 * that points nowhere near the real cause.
 */
@SpringBootApplication(scanBasePackages = {"com.choco.notification", "com.choco.web",
        "com.choco.security", "com.choco.events"})
@EntityScan(basePackages = {"com.choco.notification", "com.choco.events.outbox"})
@EnableJpaRepositories(basePackages = {"com.choco.notification", "com.choco.events.outbox"})
@ConfigurationPropertiesScan(basePackages = "com.choco.notification")
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
