package com.choco.fulfilment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ConflictException;
import com.choco.core.error.NotFoundException;
import com.choco.core.error.ValidationException;
import com.choco.fulfilment.application.command.AdjustStockCommand;
import com.choco.fulfilment.application.command.ConfigureRecipeCommand;
import com.choco.fulfilment.application.command.CreateIngredientCommand;
import com.choco.fulfilment.application.command.UpdateIngredientCommand;
import com.choco.fulfilment.application.dto.IngredientView;
import com.choco.fulfilment.application.support.InMemoryIngredientRepository;
import com.choco.fulfilment.application.support.InMemoryRecipeRepository;
import com.choco.fulfilment.application.support.InMemoryStockLedgerRepository;
import com.choco.fulfilment.application.support.RecordingAuditLog;
import com.choco.fulfilment.domain.inventory.StockLedgerReason;
import com.choco.fulfilment.domain.inventory.UnitOfMeasure;
import com.choco.test.Fakes;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Inventory service")
class InventoryServiceTest {

    private static final String ADMIN = "admin-1";

    private InMemoryIngredientRepository ingredients;
    private InMemoryRecipeRepository recipes;
    private InMemoryStockLedgerRepository ledger;
    private RecordingAuditLog audit;
    private Fakes.FixedClock clock;
    private InventoryService service;

    @BeforeEach
    void setUp() {
        ingredients = new InMemoryIngredientRepository();
        recipes = new InMemoryRecipeRepository();
        ledger = new InMemoryStockLedgerRepository();
        audit = new RecordingAuditLog();
        clock = Fakes.FixedClock.startingAt("2026-01-01T10:00:00Z");
        service = new InventoryService(ingredients, recipes, ledger, audit, clock,
                new Fakes.SequentialIdGenerator(), new Fakes.DirectTransactionRunner());
    }

    private UUID createIngredient(String name, String opening, String minimum) {
        IngredientView view = service.create(ADMIN, new CreateIngredientCommand(name, UnitOfMeasure.GRAM,
                new BigDecimal(opening), new BigDecimal(minimum)));
        return view.id();
    }

    @Test
    @DisplayName("creating an ingredient records its opening level as a receipt in the ledger")
    void createRecordsOpeningLevel() {
        UUID id = createIngredient("Cocoa butter", "1000", "200");

        assertThat(ingredients.findById(id).orElseThrow().onHand().toUnits())
                .isEqualByComparingTo("1000");
        assertThat(ledger.all()).hasSize(1);
        assertThat(ledger.all().getFirst().reason()).isEqualTo(StockLedgerReason.RECEIVED);
        assertThat(audit.hasAction("inventory.ingredient.create")).isTrue();
    }

    @Test
    @DisplayName("a duplicate name is refused, case-insensitively")
    void duplicateNameIsRefused() {
        createIngredient("Sugar", "500", "0");

        assertThatThrownBy(() -> service.create(ADMIN, new CreateIngredientCommand("sugar",
                UnitOfMeasure.GRAM, new BigDecimal("100"), BigDecimal.ZERO)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("an ingredient below its minimum shows in the low-stock list")
    void lowStockIsReported() {
        UUID id = createIngredient("Milk powder", "100", "250");

        assertThat(service.lowStock()).extracting(IngredientView::id).containsExactly(id);
    }

    @Test
    @DisplayName("a zero threshold never alerts, so an untracked item does not nag the seller")
    void zeroThresholdNeverAlerts() {
        createIngredient("Vanilla", "0", "0");
        assertThat(service.lowStock()).isEmpty();
    }

    @Test
    @DisplayName("a positive adjustment raises the level and is recorded")
    void positiveAdjustment() {
        UUID id = createIngredient("Butter", "100", "0");
        service.adjustStock(ADMIN, new AdjustStockCommand(id, new BigDecimal("50"),
                StockLedgerReason.RECEIVED, "supplier delivery"));

        assertThat(ingredients.findById(id).orElseThrow().onHand().toUnits())
                .isEqualByComparingTo("150");
        assertThat(ledger.all()).hasSize(2);
    }

    @Test
    @DisplayName("a negative adjustment writes stock off")
    void negativeAdjustment() {
        UUID id = createIngredient("Cream", "100", "0");
        service.adjustStock(ADMIN, new AdjustStockCommand(id, new BigDecimal("-30"),
                StockLedgerReason.ADJUSTED, "spoiled"));

        assertThat(ingredients.findById(id).orElseThrow().onHand().toUnits())
                .isEqualByComparingTo("70");
    }

    @Test
    @DisplayName("consumption cannot be recorded by hand, only by production")
    void manualConsumptionIsRefused() {
        UUID id = createIngredient("Cocoa", "100", "0");

        assertThatThrownBy(() -> service.adjustStock(ADMIN, new AdjustStockCommand(id,
                new BigDecimal("-10"), StockLedgerReason.CONSUMED, null)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("adjusting stock below zero is refused")
    void cannotGoNegative() {
        UUID id = createIngredient("Cocoa", "10", "0");

        assertThatThrownBy(() -> service.adjustStock(ADMIN, new AdjustStockCommand(id,
                new BigDecimal("-50"), StockLedgerReason.ADJUSTED, "mistake")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("a zero adjustment is refused: it would record a movement that moved nothing")
    void zeroAdjustmentIsRefused() {
        UUID id = createIngredient("Cocoa", "10", "0");

        assertThatThrownBy(() -> service.adjustStock(ADMIN, new AdjustStockCommand(id,
                BigDecimal.ZERO, StockLedgerReason.ADJUSTED, null)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("a recipe referencing an unknown ingredient is refused")
    void recipeWithUnknownIngredientIsRefused() {
        assertThatThrownBy(() -> service.configureRecipe(ADMIN, new ConfigureRecipeCommand(
                UUID.randomUUID(), List.of(new ConfigureRecipeCommand.Line(UUID.randomUUID(),
                new BigDecimal("10"))))))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("a recipe is replaced as a whole and resolves ingredient names for display")
    void configureRecipe() {
        UUID id = createIngredient("Sugar", "1000", "0");
        UUID variant = UUID.randomUUID();
        service.configureRecipe(ADMIN, new ConfigureRecipeCommand(variant,
                List.of(new ConfigureRecipeCommand.Line(id, new BigDecimal("25")))));

        var recipe = service.recipe(variant);
        assertThat(recipe.lines()).hasSize(1);
        assertThat(recipe.lines().getFirst().ingredientName()).isEqualTo("Sugar");
        assertThat(recipe.lines().getFirst().perUnit()).isEqualByComparingTo("25");
    }

    @Test
    @DisplayName("deactivating an ingredient retires it from the active list")
    void deactivateIngredient() {
        UUID id = createIngredient("Old stock", "10", "0");
        service.update(ADMIN, new UpdateIngredientCommand(id, new BigDecimal("5"), false));

        assertThat(ingredients.findById(id).orElseThrow().active()).isFalse();
        assertThat(service.listIngredients(false, null, null).content()).isEmpty();
    }
}
