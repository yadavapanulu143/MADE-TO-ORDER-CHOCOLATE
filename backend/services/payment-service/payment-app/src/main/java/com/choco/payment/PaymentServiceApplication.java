package com.choco.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Payment service: Razorpay orders, server-side verification, webhooks, refunds and disputes.
 *
 * <p>{@code scanBasePackages} pulls in the shared modules so their auto-configurations and components
 * are registered. Component scanning alone is not enough for persistence: Spring Data discovers
 * repositories from the application class's package by default, so {@code @EnableJpaRepositories} and
 * {@code @EntityScan} explicitly extend the search to {@code com.choco.events.outbox}, where the
 * outbox entity and its repository live. Without that the outbox repository has no bean, the publisher
 * is never created, and the service fails to start with a message about a missing {@code
 * EventPublisher} that points nowhere near the real cause.
 *
 * <p>{@code @ConfigurationPropertiesScan} binds {@code RazorpayProperties}, so the provider
 * configuration is validated at startup rather than at the first payment.
 */
@SpringBootApplication(scanBasePackages = {"com.choco.payment", "com.choco.web", "com.choco.security",
        "com.choco.events"})
@EntityScan(basePackages = {"com.choco.payment", "com.choco.events.outbox"})
@EnableJpaRepositories(basePackages = {"com.choco.payment", "com.choco.events.outbox"})
@ConfigurationPropertiesScan(basePackages = "com.choco.payment")
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
