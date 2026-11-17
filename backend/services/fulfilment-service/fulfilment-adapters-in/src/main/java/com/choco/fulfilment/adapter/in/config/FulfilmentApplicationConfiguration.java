package com.choco.fulfilment.adapter.in.config;

import com.choco.core.port.Clock;
import com.choco.core.port.EventPublisher;
import com.choco.core.port.IdGenerator;
import com.choco.core.port.TransactionRunner;
import com.choco.fulfilment.application.port.AuditLogPort;
import com.choco.fulfilment.application.service.DeliveryService;
import com.choco.fulfilment.application.service.InventoryService;
import com.choco.fulfilment.application.service.ProductionQueryService;
import com.choco.fulfilment.application.service.ProductionService;
import com.choco.fulfilment.domain.port.DeliveryRepository;
import com.choco.fulfilment.domain.port.IngredientRepository;
import com.choco.fulfilment.domain.port.ProductionJobRepository;
import com.choco.fulfilment.domain.port.RecipeRepository;
import com.choco.fulfilment.domain.port.ServiceableAreaRepository;
import com.choco.fulfilment.domain.port.StockLedgerRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the fulfilment use cases.
 *
 * <p>The use cases are plain classes built here rather than {@code @Service}-annotated, so the
 * application layer carries no Spring import and each can be constructed in a unit test with no
 * container. This mirrors the order and payment services, so a reader who knows one service knows the
 * others.
 *
 * <p>The capacity cap and the proof-of-delivery flag are business settings read from configuration and
 * passed in as plain values. A cap of zero means "no cap", which is the correct default for a seller
 * who has not yet decided how much they can make at once.
 */
@Configuration(proxyBeanMethods = false)
class FulfilmentApplicationConfiguration {

    @Bean
    ProductionService productionService(ProductionJobRepository jobRepository,
                                        DeliveryRepository deliveryRepository,
                                        IngredientRepository ingredientRepository,
                                        RecipeRepository recipeRepository,
                                        StockLedgerRepository ledgerRepository,
                                        EventPublisher eventPublisher, AuditLogPort auditLog, Clock clock,
                                        IdGenerator idGenerator, TransactionRunner transactionRunner,
                                        @Value("${choco.fulfilment.capacity.max-units-in-progress:0}")
                                        int maxUnitsInProgress) {
        return new ProductionService(jobRepository, deliveryRepository, ingredientRepository,
                recipeRepository, ledgerRepository, eventPublisher, auditLog, clock, idGenerator,
                transactionRunner, maxUnitsInProgress);
    }

    @Bean
    ProductionQueryService productionQueryService(ProductionJobRepository jobRepository) {
        return new ProductionQueryService(jobRepository);
    }

    @Bean
    DeliveryService deliveryService(DeliveryRepository deliveryRepository,
                                    ServiceableAreaRepository areaRepository,
                                    EventPublisher eventPublisher, AuditLogPort auditLog, Clock clock,
                                    IdGenerator idGenerator, TransactionRunner transactionRunner,
                                    @Value("${choco.fulfilment.delivery.proof-required:false}")
                                    boolean proofOfDeliveryRequired) {
        return new DeliveryService(deliveryRepository, areaRepository, eventPublisher, auditLog, clock,
                idGenerator, transactionRunner, proofOfDeliveryRequired);
    }

    @Bean
    InventoryService inventoryService(IngredientRepository ingredientRepository,
                                      RecipeRepository recipeRepository,
                                      StockLedgerRepository ledgerRepository, AuditLogPort auditLog,
                                      Clock clock, IdGenerator idGenerator,
                                      TransactionRunner transactionRunner) {
        return new InventoryService(ingredientRepository, recipeRepository, ledgerRepository, auditLog,
                clock, idGenerator, transactionRunner);
    }
}
