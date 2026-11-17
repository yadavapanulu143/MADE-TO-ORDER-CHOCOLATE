package com.choco.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Catalog service: chocolate products, variants, prices, images and website settings.
 *
 * <p>{@code scanBasePackages} pulls in the shared modules so their auto-configurations and components
 * are registered. Component scanning alone is not enough for persistence: Spring Data discovers
 * repositories from the application class's package by default, so {@code @EnableJpaRepositories} and
 * {@code @EntityScan} explicitly extend the search to {@code com.choco.events.outbox}, where the
 * outbox entity and its repository live. Without that the outbox repository has no bean, the publisher
 * is never created, and the service fails to start with a message about a missing {@code
 * EventPublisher} that points nowhere near the real cause — the same trap the identity service
 * documents.
 */
@SpringBootApplication(scanBasePackages = {"com.choco.catalog", "com.choco.web", "com.choco.security",
        "com.choco.events"})
@EntityScan(basePackages = {"com.choco.catalog", "com.choco.events.outbox"})
@EnableJpaRepositories(basePackages = {"com.choco.catalog", "com.choco.events.outbox"})
public class CatalogServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogServiceApplication.class, args);
    }
}
