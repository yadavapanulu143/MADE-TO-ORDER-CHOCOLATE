package com.choco.reporting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Reporting service: the seller's read models, dashboards and exports.
 *
 * <p>This service owns no business fact and writes nothing another service reads. It observes the
 * platform's events and maintains local projections the seller reports on, so a report never loads the
 * order, payment or fulfilment service and works while one of them is briefly down.
 *
 * <p>{@code scanBasePackages} pulls in the shared modules so their auto-configurations and components
 * are registered. {@code @EnableJpaRepositories} and {@code @EntityScan} explicitly extend the search to
 * {@code com.choco.events.outbox}, where the outbox entity and its repository live; without that the
 * outbox repository has no bean and the service fails to start with a message about a missing
 * {@code EventPublisher} that points nowhere near the real cause. The reporting service writes no
 * outbox rows of its own today, but the shared publisher is on the classpath and must resolve.
 */
@SpringBootApplication(scanBasePackages = {"com.choco.reporting", "com.choco.web",
        "com.choco.security", "com.choco.events"})
@EntityScan(basePackages = {"com.choco.reporting", "com.choco.events.outbox"})
@EnableJpaRepositories(basePackages = {"com.choco.reporting", "com.choco.events.outbox"})
@ConfigurationPropertiesScan(basePackages = "com.choco.reporting")
public class ReportingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportingServiceApplication.class, args);
    }
}
