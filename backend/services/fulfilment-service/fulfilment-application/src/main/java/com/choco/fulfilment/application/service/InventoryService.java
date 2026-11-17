package com.choco.fulfilment.application.service;

import com.choco.core.error.ConflictException;
import com.choco.core.error.ErrorCode;
import com.choco.core.error.NotFoundException;
import com.choco.core.error.ValidationException;
import com.choco.core.page.PageResult;
import com.choco.core.port.Clock;
import com.choco.core.port.IdGenerator;
import com.choco.core.port.PageQuery;
import com.choco.core.port.TransactionRunner;
import com.choco.fulfilment.application.command.AdjustStockCommand;
import com.choco.fulfilment.application.command.ConfigureRecipeCommand;
import com.choco.fulfilment.application.command.CreateIngredientCommand;
import com.choco.fulfilment.application.command.UpdateIngredientCommand;
import com.choco.fulfilment.application.dto.IngredientView;
import com.choco.fulfilment.application.dto.RecipeView;
import com.choco.fulfilment.application.dto.StockLedgerEntryView;
import com.choco.fulfilment.application.port.AuditLogPort;
import com.choco.fulfilment.domain.inventory.Ingredient;
import com.choco.fulfilment.domain.inventory.RecipeLine;
import com.choco.fulfilment.domain.inventory.StockLedgerEntry;
import com.choco.fulfilment.domain.inventory.StockLedgerReason;
import com.choco.fulfilment.domain.inventory.StockLevel;
import com.choco.fulfilment.domain.port.IngredientRepository;
import com.choco.fulfilment.domain.port.RecipeRepository;
import com.choco.fulfilment.domain.port.StockLedgerRepository;
import java.util.List;
import java.util.UUID;

/**
 * The seller's stock of ingredients and packaging, and the recipes that draw on it.
 *
 * <p>This is the optional half of made-to-order. The seller may never configure an ingredient or a
 * recipe, and the production flow still works — nothing here is on the critical path for making
 * chocolate. When it <em>is</em> configured, it exists so the seller is warned before an order arrives
 * that cannot be fulfilled, and can reconcile a stock-take against a ledger of what actually moved.
 *
 * <p>Every level change writes a ledger entry in the same transaction, so the current level and the
 * history explaining it cannot disagree. The ledger is append-only: there is no update or delete here,
 * because a ledger that can be rewritten answers nothing.
 */
public class InventoryService {

    private static final String ACTOR_ADMIN = "ADMIN";
    private static final String TARGET = "INGREDIENT";

    private final IngredientRepository ingredientRepository;
    private final RecipeRepository recipeRepository;
    private final StockLedgerRepository ledgerRepository;
    private final AuditLogPort auditLog;
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final TransactionRunner transactions;

    public InventoryService(IngredientRepository ingredientRepository, RecipeRepository recipeRepository,
                            StockLedgerRepository ledgerRepository, AuditLogPort auditLog, Clock clock,
                            IdGenerator idGenerator, TransactionRunner transactions) {
        this.ingredientRepository = ingredientRepository;
        this.recipeRepository = recipeRepository;
        this.ledgerRepository = ledgerRepository;
        this.auditLog = auditLog;
        this.clock = clock;
        this.idGenerator = idGenerator;
        this.transactions = transactions;
    }

    /** Adds an ingredient, with an opening level recorded as a receipt in the ledger. */
    public IngredientView create(String adminId, CreateIngredientCommand command) {
        return transactions.execute(() -> {
            if (ingredientRepository.existsByNameIgnoreCase(command.name().trim())) {
                throw new ConflictException(ErrorCode.CONFLICT,
                        "An ingredient named " + command.name() + " already exists");
            }
            Ingredient ingredient = Ingredient.create(idGenerator.nextId(), command.name(), command.unit(),
                    StockLevel.of(command.openingLevel()),
                    command.minimumLevel() == null ? StockLevel.zero() : StockLevel.of(command.minimumLevel()),
                    clock.now());
            Ingredient saved = ingredientRepository.save(ingredient);
            if (saved.onHand().isPositive()) {
                ledgerRepository.append(StockLedgerEntry.of(idGenerator.nextId(), saved.id(),
                        StockLedgerReason.RECEIVED, saved.onHand(), saved.onHand(), null,
                        "Opening level", clock.now()));
            }
            audit(adminId, "inventory.ingredient.create", saved.id().toString(), "SUCCESS", saved.name());
            return FulfilmentViewMapper.toView(saved);
        });
    }

    /** Changes an ingredient's low-stock threshold, or retires/reactivates it. */
    public IngredientView update(String adminId, UpdateIngredientCommand command) {
        return transactions.execute(() -> {
            Ingredient ingredient = requireIngredient(command.ingredientId());
            if (command.minimumLevel() != null) {
                ingredient.setMinimumLevel(StockLevel.of(command.minimumLevel()), clock.now());
            }
            if (command.active() != null) {
                if (command.active()) {
                    ingredient.activate(clock.now());
                } else {
                    ingredient.deactivate(clock.now());
                }
            }
            Ingredient saved = ingredientRepository.save(ingredient);
            audit(adminId, "inventory.ingredient.update", saved.id().toString(), "SUCCESS", null);
            return FulfilmentViewMapper.toView(saved);
        });
    }

    /**
     * Records a manual stock movement: a receipt, a correction or a write-off.
     *
     * <p>The sign is taken from the amount, so a positive value adds and a negative value removes; the
     * reason says why. A reason of {@code CONSUMED} is refused — consumption is recorded by production
     * against the job that caused it, and accepting it here would let the seller take stock out with no
     * job to attribute it to.
     */
    public IngredientView adjustStock(String adminId, AdjustStockCommand command) {
        return transactions.execute(() -> {
            if (command.reason() == StockLedgerReason.CONSUMED) {
                throw new ValidationException(
                        "Consumption is recorded automatically when preparation starts, not by hand");
            }
            Ingredient ingredient = requireIngredient(command.ingredientId());
            StockLevel delta = StockLevel.of(command.amount());
            if (delta.isZero()) {
                throw new ValidationException("A stock adjustment must move the level");
            }
            ingredient.adjust(delta, command.note(), clock.now());
            Ingredient saved = ingredientRepository.save(ingredient);
            ledgerRepository.append(StockLedgerEntry.of(idGenerator.nextId(), saved.id(), command.reason(),
                    delta, saved.onHand(), null, command.note(), clock.now()));
            audit(adminId, "inventory.stock." + command.reason().name().toLowerCase(), saved.id().toString(),
                    "SUCCESS", command.note());
            return FulfilmentViewMapper.toView(saved);
        });
    }

    /** Replaces a variant's recipe. An empty list clears it. */
    public RecipeView configureRecipe(String adminId, ConfigureRecipeCommand command) {
        return transactions.execute(() -> {
            List<RecipeLine> lines = command.lines() == null ? List.of() : command.lines().stream()
                    .map(line -> {
                        if (!ingredientRepository.findById(line.ingredientId()).isPresent()) {
                            throw new NotFoundException(ErrorCode.NOT_FOUND,
                                    "An ingredient in the recipe does not exist");
                        }
                        return RecipeLine.of(idGenerator.nextId(), command.variantId(), line.ingredientId(),
                                StockLevel.of(line.perUnit()));
                    })
                    .toList();
            recipeRepository.replaceForVariant(command.variantId(), lines);
            audit(adminId, "inventory.recipe.configure", command.variantId().toString(), "SUCCESS",
                    lines.size() + " line(s)");
            return recipeView(command.variantId(), lines);
        });
    }

    /** The recipe for a variant, with ingredient names resolved for display. */
    public RecipeView recipe(UUID variantId) {
        return recipeView(variantId, recipeRepository.findByVariantId(variantId));
    }

    public PageResult<IngredientView> listIngredients(boolean includeInactive, String search,
                                                      PageQuery page) {
        return ingredientRepository.search(includeInactive, search, page)
                .map(FulfilmentViewMapper::toView);
    }

    /** Every active ingredient at or below its minimum, for the seller's alerts view. */
    public List<IngredientView> lowStock() {
        return ingredientRepository.findLowStock().stream().map(FulfilmentViewMapper::toView).toList();
    }

    public PageResult<StockLedgerEntryView> ledger(UUID ingredientId, PageQuery page) {
        requireIngredient(ingredientId);
        return ledgerRepository.findByIngredient(ingredientId, page).map(FulfilmentViewMapper::toView);
    }

    private RecipeView recipeView(UUID variantId, List<RecipeLine> lines) {
        return new RecipeView(variantId, lines.stream()
                .map(line -> {
                    Ingredient ingredient = ingredientRepository.findById(line.ingredientId()).orElse(null);
                    return new RecipeView.Line(line.ingredientId(),
                            ingredient == null ? "(removed)" : ingredient.name(),
                            ingredient == null ? "" : ingredient.unit().symbol(),
                            line.perUnit().toUnits(), line.perUnit().milliUnits());
                })
                .toList());
    }

    private Ingredient requireIngredient(UUID id) {
        return ingredientRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "Ingredient not found"));
    }

    private void audit(String actorId, String action, String targetId, String outcome, String detail) {
        auditLog.record(ACTOR_ADMIN, actorId, action, TARGET, targetId, outcome, detail, null);
    }
}
