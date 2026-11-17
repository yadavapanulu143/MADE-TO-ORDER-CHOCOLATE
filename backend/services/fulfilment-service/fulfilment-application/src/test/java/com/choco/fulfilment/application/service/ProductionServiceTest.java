package com.choco.fulfilment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.domain.Money;
import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ValidationException;
import com.choco.fulfilment.application.command.OrderCancelledCommand;
import com.choco.fulfilment.application.command.OrderConfirmedCommand;
import com.choco.fulfilment.application.command.ProductionProgressCommand;
import com.choco.fulfilment.application.command.ProductionProgressCommand.ProductionAction;
import com.choco.fulfilment.application.dto.ProductionJobView;
import com.choco.fulfilment.application.support.InMemoryDeliveryRepository;
import com.choco.fulfilment.application.support.InMemoryIngredientRepository;
import com.choco.fulfilment.application.support.InMemoryProductionJobRepository;
import com.choco.fulfilment.application.support.InMemoryRecipeRepository;
import com.choco.fulfilment.application.support.InMemoryStockLedgerRepository;
import com.choco.fulfilment.application.support.RecordingAuditLog;
import com.choco.fulfilment.domain.inventory.StockLevel;
import com.choco.fulfilment.domain.inventory.StockLedgerReason;
import com.choco.fulfilment.domain.inventory.UnitOfMeasure;
import com.choco.fulfilment.domain.production.ProductionStatus;
import com.choco.test.Fakes;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Production service")
class ProductionServiceTest {

    private static final String CUSTOMER = UUID.randomUUID().toString();
    private static final String ORDER = UUID.randomUUID().toString();
    private static final String PRODUCT = UUID.randomUUID().toString();
    private static final String VARIANT = UUID.randomUUID().toString();
    private static final String ADMIN = "admin-1";

    private InMemoryProductionJobRepository jobs;
    private InMemoryDeliveryRepository deliveries;
    private InMemoryIngredientRepository ingredients;
    private InMemoryRecipeRepository recipes;
    private InMemoryStockLedgerRepository ledger;
    private Fakes.RecordingEventPublisher events;
    private RecordingAuditLog audit;
    private Fakes.FixedClock clock;
    private ProductionService service;

    @BeforeEach
    void setUp() {
        jobs = new InMemoryProductionJobRepository();
        deliveries = new InMemoryDeliveryRepository();
        ingredients = new InMemoryIngredientRepository();
        recipes = new InMemoryRecipeRepository();
        ledger = new InMemoryStockLedgerRepository();
        events = new Fakes.RecordingEventPublisher();
        audit = new RecordingAuditLog();
        clock = Fakes.FixedClock.startingAt("2026-01-01T10:00:00Z");
        service = new ProductionService(jobs, deliveries, ingredients, recipes, ledger, events, audit,
                clock, new Fakes.SequentialIdGenerator(), new Fakes.DirectTransactionRunner(), 0);
    }

    private OrderConfirmedCommand confirmedOrder(int quantity) {
        return new OrderConfirmedCommand(ORDER, CUSTOMER, "CHOCO-2026001-1", "Asha Rao",
                new OrderConfirmedCommand.Address("Asha Rao", "9876543210", "12 Cocoa Lane", null,
                        "Mumbai", "Maharashtra", "400001", "Ring the bell"),
                5000, List.of(new OrderConfirmedCommand.Line(PRODUCT, VARIANT, "Dark Bar", "200g",
                        quantity)));
    }

    @Test
    @DisplayName("a confirmed order opens one job per line, carrying the frozen address and charge")
    void confirmedOrderOpensJob() {
        service.acceptConfirmedOrder(confirmedOrder(3));

        List<ProductionJobView> forOrder = serviceQuery().forOrder(UUID.fromString(ORDER));
        assertThat(forOrder).hasSize(1);
        ProductionJobView job = forOrder.getFirst();
        assertThat(job.status()).isEqualTo(ProductionStatus.RECEIVED.name());
        assertThat(job.quantity()).isEqualTo(3);
        assertThat(job.orderNumber()).isEqualTo("CHOCO-2026001-1");
    }

    @Test
    @DisplayName("a redelivered confirmation opens no second job")
    void redeliveredConfirmationIsIdempotent() {
        service.acceptConfirmedOrder(confirmedOrder(2));
        service.acceptConfirmedOrder(confirmedOrder(2));

        assertThat(serviceQuery().forOrder(UUID.fromString(ORDER))).hasSize(1);
    }

    @Nested
    @DisplayName("seller steps")
    class SellerSteps {

        private UUID jobId;

        @BeforeEach
        void openJob() {
            service.acceptConfirmedOrder(confirmedOrder(1));
            jobId = serviceQuery().forOrder(UUID.fromString(ORDER)).getFirst().id();
        }

        @Test
        @DisplayName("starting preparation raises the event the customer is told about")
        void startPreparationPublishesEvent() {
            service.advance(ADMIN, new ProductionProgressCommand(jobId, ProductionAction.ACKNOWLEDGE, null));
            ProductionJobView view = service.advance(ADMIN,
                    new ProductionProgressCommand(jobId, ProductionAction.START, null));

            assertThat(view.status()).isEqualTo(ProductionStatus.PREPARATION_STARTED.name());
            assertThat(events.eventsOfType("fulfilment.production-started")).hasSize(1);
            assertThat(audit.hasAction("production.start")).isTrue();
        }

        @Test
        @DisplayName("an illegal jump is refused by the aggregate, not silently applied")
        void illegalStepIsRefused() {
            assertThatThrownBy(() -> service.advance(ADMIN,
                    new ProductionProgressCommand(jobId, ProductionAction.READY_FOR_DISPATCH, null)))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("a job cannot be readied for dispatch without being prepared first")
        void cannotSkipMakingTheChocolate() {
            assertThatThrownBy(() -> service.advance(ADMIN,
                    new ProductionProgressCommand(jobId, ProductionAction.COMPLETE, null)))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("the full preparation path ends ready for dispatch and opens a delivery")
        void fullPathOpensDelivery() {
            service.advance(ADMIN, new ProductionProgressCommand(jobId, ProductionAction.ACKNOWLEDGE, null));
            service.advance(ADMIN, new ProductionProgressCommand(jobId, ProductionAction.START, null));
            service.advance(ADMIN, new ProductionProgressCommand(jobId, ProductionAction.COMPLETE, null));
            service.advance(ADMIN, new ProductionProgressCommand(jobId, ProductionAction.PACKAGING, null));
            ProductionJobView view = service.advance(ADMIN,
                    new ProductionProgressCommand(jobId, ProductionAction.READY_FOR_DISPATCH, null));

            assertThat(view.status()).isEqualTo(ProductionStatus.READY_FOR_DISPATCH.name());
            assertThat(deliveries.existsForOrder(UUID.fromString(ORDER))).isTrue();
            assertThat(events.eventsOfType("fulfilment.ready-for-dispatch")).hasSize(1);
        }

        @Test
        @DisplayName("a seller's note is attached and audited")
        void addNote() {
            ProductionJobView view = service.addNote(ADMIN,
                    new com.choco.fulfilment.application.command.AddProductionNoteCommand(jobId,
                            "customer asked for less sugar"));
            assertThat(view.notes()).hasSize(1);
            assertThat(audit.hasAction("production.note.add")).isTrue();
        }
    }

    @Nested
    @DisplayName("ingredient consumption")
    class Consumption {

        @Test
        @DisplayName("starting preparation takes the recipe's ingredients out of stock")
        void startConsumesIngredients() {
            UUID ingredientId = UUID.randomUUID();
            ingredients.save(com.choco.fulfilment.domain.inventory.Ingredient.create(ingredientId,
                    "Cocoa butter", UnitOfMeasure.GRAM, StockLevel.ofUnits(1000), StockLevel.zero(),
                    clock.now()));
            recipes.replaceForVariant(UUID.fromString(VARIANT), List.of(
                    com.choco.fulfilment.domain.inventory.RecipeLine.of(UUID.randomUUID(),
                            UUID.fromString(VARIANT), ingredientId, StockLevel.of(new BigDecimal("50")))));

            service.acceptConfirmedOrder(confirmedOrder(2));
            UUID jobId = serviceQuery().forOrder(UUID.fromString(ORDER)).getFirst().id();
            service.advance(ADMIN, new ProductionProgressCommand(jobId, ProductionAction.ACKNOWLEDGE, null));
            service.advance(ADMIN, new ProductionProgressCommand(jobId, ProductionAction.START, null));

            assertThat(ingredients.findById(ingredientId).orElseThrow().onHand().milliUnits())
                    .isEqualTo(900_000L);
            assertThat(ledger.all()).hasSize(1);
            assertThat(ledger.all().getFirst().reason()).isEqualTo(StockLedgerReason.CONSUMED);
        }

        @Test
        @DisplayName("a variant with no recipe starts preparation and consumes nothing")
        void noRecipeConsumesNothing() {
            service.acceptConfirmedOrder(confirmedOrder(1));
            UUID jobId = serviceQuery().forOrder(UUID.fromString(ORDER)).getFirst().id();
            service.advance(ADMIN, new ProductionProgressCommand(jobId, ProductionAction.ACKNOWLEDGE, null));
            ProductionJobView view = service.advance(ADMIN,
                    new ProductionProgressCommand(jobId, ProductionAction.START, null));

            assertThat(view.status()).isEqualTo(ProductionStatus.PREPARATION_STARTED.name());
            assertThat(ledger.all()).isEmpty();
        }

        @Test
        @DisplayName("consumption is recorded once even if the job is held and resumed")
        void consumptionIsOnceOnly() {
            UUID ingredientId = UUID.randomUUID();
            ingredients.save(com.choco.fulfilment.domain.inventory.Ingredient.create(ingredientId,
                    "Sugar", UnitOfMeasure.GRAM, StockLevel.ofUnits(1000), StockLevel.zero(), clock.now()));
            recipes.replaceForVariant(UUID.fromString(VARIANT), List.of(
                    com.choco.fulfilment.domain.inventory.RecipeLine.of(UUID.randomUUID(),
                            UUID.fromString(VARIANT), ingredientId, StockLevel.of(new BigDecimal("10")))));

            service.acceptConfirmedOrder(confirmedOrder(1));
            UUID jobId = serviceQuery().forOrder(UUID.fromString(ORDER)).getFirst().id();
            service.advance(ADMIN, new ProductionProgressCommand(jobId, ProductionAction.ACKNOWLEDGE, null));
            service.advance(ADMIN, new ProductionProgressCommand(jobId, ProductionAction.START, null));
            service.advance(ADMIN, new ProductionProgressCommand(jobId, ProductionAction.HOLD, "ran out"));
            service.advance(ADMIN, new ProductionProgressCommand(jobId, ProductionAction.RESUME, null));

            assertThat(ledger.all()).hasSize(1);
            assertThat(ingredients.findById(ingredientId).orElseThrow().onHand().milliUnits())
                    .isEqualTo(990_000L);
        }
    }

    @Nested
    @DisplayName("cancellation")
    class Cancellation {

        @Test
        @DisplayName("a cancelled order abandons its jobs and returns the ingredients it took")
        void cancellationReturnsIngredients() {
            UUID ingredientId = UUID.randomUUID();
            ingredients.save(com.choco.fulfilment.domain.inventory.Ingredient.create(ingredientId,
                    "Milk powder", UnitOfMeasure.GRAM, StockLevel.ofUnits(500), StockLevel.zero(),
                    clock.now()));
            recipes.replaceForVariant(UUID.fromString(VARIANT), List.of(
                    com.choco.fulfilment.domain.inventory.RecipeLine.of(UUID.randomUUID(),
                            UUID.fromString(VARIANT), ingredientId, StockLevel.of(new BigDecimal("100")))));

            service.acceptConfirmedOrder(confirmedOrder(1));
            UUID jobId = serviceQuery().forOrder(UUID.fromString(ORDER)).getFirst().id();
            service.advance(ADMIN, new ProductionProgressCommand(jobId, ProductionAction.ACKNOWLEDGE, null));
            service.advance(ADMIN, new ProductionProgressCommand(jobId, ProductionAction.START, null));
            assertThat(ingredients.findById(ingredientId).orElseThrow().onHand().milliUnits())
                    .isEqualTo(400_000L);

            service.handleOrderCancelled(new OrderCancelledCommand(ORDER, "customer changed their mind"));

            assertThat(ingredients.findById(ingredientId).orElseThrow().onHand().milliUnits())
                    .isEqualTo(500_000L);
            assertThat(serviceQuery().forOrder(UUID.fromString(ORDER)).getFirst().status())
                    .isEqualTo(ProductionStatus.CANCELLED.name());
        }

        @Test
        @DisplayName("a cancelled order is idempotent: a redelivery changes nothing")
        void cancellationIsIdempotent() {
            service.acceptConfirmedOrder(confirmedOrder(1));
            service.handleOrderCancelled(new OrderCancelledCommand(ORDER, "why not"));
            service.handleOrderCancelled(new OrderCancelledCommand(ORDER, "why not"));

            assertThat(serviceQuery().forOrder(UUID.fromString(ORDER))).hasSize(1);
            assertThat(serviceQuery().forOrder(UUID.fromString(ORDER)).getFirst().status())
                    .isEqualTo(ProductionStatus.CANCELLED.name());
        }
    }

    @Nested
    @DisplayName("capacity")
    class Capacity {

        @Test
        @DisplayName("with no cap set, every order is accepted")
        void noCapAcceptsEverything() {
            service.acceptConfirmedOrder(confirmedOrder(500));
            assertThat(jobs.countUnitsOccupyingCapacity()).isEqualTo(500);
        }

        @Test
        @DisplayName("a hand-over that would exceed the cap is refused")
        void capRefusesOverflow() {
            ProductionService capped = new ProductionService(jobs, deliveries, ingredients, recipes,
                    ledger, events, audit, clock, new Fakes.SequentialIdGenerator(),
                    new Fakes.DirectTransactionRunner(), 10);
            capped.acceptConfirmedOrder(confirmedOrder(8));

            String otherOrder = UUID.randomUUID().toString();
            assertThatThrownBy(() -> capped.acceptConfirmedOrder(new OrderConfirmedCommand(otherOrder,
                    CUSTOMER, "CHOCO-2026001-2", "Asha Rao",
                    new OrderConfirmedCommand.Address("Asha Rao", "9876543210", "12 Cocoa Lane", null,
                            "Mumbai", "Maharashtra", "400001", null),
                    5000, List.of(new OrderConfirmedCommand.Line(PRODUCT, UUID.randomUUID().toString(),
                            "Dark Bar", "200g", 5)))))
                    .isInstanceOf(ValidationException.class);
        }
    }

    private ProductionQueryService serviceQuery() {
        return new ProductionQueryService(jobs);
    }
}
