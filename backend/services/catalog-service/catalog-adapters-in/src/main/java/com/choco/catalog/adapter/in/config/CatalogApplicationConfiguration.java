package com.choco.catalog.adapter.in.config;

import com.choco.catalog.application.port.AuditLogPort;
import com.choco.catalog.application.port.ImageStoragePort;
import com.choco.catalog.application.service.ProductCatalogService;
import com.choco.catalog.application.service.ProductQueryService;
import com.choco.catalog.application.service.WebsiteSettingsService;
import com.choco.catalog.domain.port.ProductRepository;
import com.choco.catalog.domain.port.WebsiteSettingsRepository;
import com.choco.core.port.Clock;
import com.choco.core.port.EventPublisher;
import com.choco.core.port.IdGenerator;
import com.choco.core.port.TransactionRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the catalog use cases.
 *
 * <p>The use cases are plain classes built here rather than {@code @Service}-annotated, so the
 * application layer carries no Spring import and each one can be constructed in a unit test with no
 * container. This mirrors the identity service, so a reader who knows one service knows the other.
 */
@Configuration(proxyBeanMethods = false)
class CatalogApplicationConfiguration {

    @Bean
    ProductCatalogService productCatalogService(ProductRepository productRepository,
                                                EventPublisher eventPublisher,
                                                ImageStoragePort imageStorage,
                                                AuditLogPort auditLog,
                                                TransactionRunner transactionRunner,
                                                IdGenerator idGenerator,
                                                Clock clock) {
        return new ProductCatalogService(productRepository, eventPublisher, imageStorage, auditLog,
                transactionRunner, idGenerator, clock);
    }

    @Bean
    ProductQueryService productQueryService(ProductRepository productRepository) {
        return new ProductQueryService(productRepository);
    }

    @Bean
    WebsiteSettingsService websiteSettingsService(WebsiteSettingsRepository settingsRepository,
                                                  AuditLogPort auditLog,
                                                  TransactionRunner transactionRunner,
                                                  Clock clock) {
        return new WebsiteSettingsService(settingsRepository, auditLog, transactionRunner, clock);
    }
}
