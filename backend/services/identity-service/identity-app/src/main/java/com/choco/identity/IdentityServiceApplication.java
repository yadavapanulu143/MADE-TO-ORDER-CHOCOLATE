package com.choco.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Identity service: registration, verification, authentication and customer profiles.
 *
 * <p>{@code scanBasePackages} covers the shared modules so their auto-configurations and components
 * are picked up. Component scanning alone is not enough for persistence: Spring Data discovers
 * repositories by scanning from the application class's package by default, so
 * {@code @EnableJpaRepositories} and {@code @EntityScan} explicitly extend the search to
 * {@code com.choco.events}, where the outbox entity and its repository live. Without that, the
 * outbox repository has no bean, the publisher is never created, and the service fails to start
 * with a message about a missing {@code EventPublisher} that points nowhere near the real cause.
 */
@SpringBootApplication(scanBasePackages = {"com.choco.identity", "com.choco.web", "com.choco.security",
        "com.choco.events"})
@EntityScan(basePackages = {"com.choco.identity", "com.choco.events.outbox"})
@EnableJpaRepositories(basePackages = {"com.choco.identity", "com.choco.events.outbox"})
public class IdentityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityServiceApplication.class, args);
    }
}
