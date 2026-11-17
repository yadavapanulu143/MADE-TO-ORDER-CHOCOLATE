package com.choco.fulfilment.application.service;

import com.choco.core.domain.Money;
import com.choco.core.domain.Pincode;
import com.choco.core.domain.Quantity;
import com.choco.core.error.NotFoundException;
import com.choco.core.error.ErrorCode;
import com.choco.core.error.ValidationException;
import com.choco.core.port.Clock;
import com.choco.core.port.EventPublisher;
import com.choco.core.port.IdGenerator;
import com.choco.core.port.TransactionRunner;
import com.choco.fulfilment.application.command.AddProductionNoteCommand;
import com.choco.fulfilment.application.command.OrderCancelledCommand;
import com.choco.fulfilment.application.command.OrderConfirmedCommand;
import com.choco.fulfilment.application.command.ProductionProgressCommand;
import com.choco.fulfilment.application.dto.ProductionJobView;
import com.choco.fulfilment.application.port.AuditLogPort;
import com.choco.fulfilment.domain.delivery.Delivery;
import com.choco.fulfilment.domain.delivery.DeliveryAddress;
import com.choco.fulfilment.domain.inventory.Ingredient;
import com.choco.fulfilment.domain.inventory.RecipeLine;
import com.choco.fulfilment.domain.inventory.StockLedgerEntry;
import com.choco.fulfilment.domain.inventory.StockLedgerReason;
import com.choco.fulfilment.domain.port.DeliveryRepository;
import com.choco.fulfilment.domain.port.IngredientRepository;
import com.choco.fulfilment.domain.port.ProductionJobRepository;
import com.choco.fulfilment.domain.port.RecipeRepository;
import com.choco.fulfilment.domain.port.StockLedgerRepository;
import com.choco.fulfilment.domain.production.ProductionJob;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The kitchen: turning a paid order into prepared, packed chocolate, and the seller's steps along the
 * way.
 *
 * <p><b>Preparation never begins without a verified payment.</b> Jobs are opened only from the order
 * service's {@code order.confirmed} event, which is raised when a payment has been verified. There is
 * no method here that opens a job for an unpaid order, and no endpoint a browser can reach to start
 * one.
 *
 * <p><b>Ingredients are optional.</b> A job advances through its statuses whether or not a recipe is
 * configured. When a recipe exists, starting preparation takes the ingredients out of stock in the
 * same transaction as the status change, so the two cannot disagree; when no recipe exists, the step
 * is a pure status change. This is what lets the seller run made-to-order from day one and add stock
 * tracking later without changing the flow.
 *
 * <p><b>Capacity is enforced when configured.</b> With a cap set, a new order whose units would push
 * the kitchen past what the seller can make at once is refused at hand-over rather than accepted and
 * quietly missed. The check runs under a lock, so two orders arriving together cannot both slip past.
 */
public class ProductionService {

    private static final Logger log = LoggerFactory.getLogger(ProductionService.class);

    private static final String ACTOR_ADMIN = "ADMIN";
    private static final String ACTOR_SYSTEM = "SYSTEM";
    private static final String TARGET_JOB = "PRODUCTION_JOB";
    private static final String TARGET_ORDER = "ORDER";

    private final ProductionJobRepository jobRepository;
    private final DeliveryRepository deliveryRepository;
    private final IngredientRepository ingredientRepository;
    private final RecipeRepository recipeRepository;
    private final StockLedgerRepository ledgerRepository;
    private final EventPublisher eventPublisher;
    private final AuditLogPort auditLog;
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final TransactionRunner transactions;
    private final int maxUnitsInProgress;

    public ProductionService(ProductionJobRepository jobRepository, DeliveryRepository deliveryRepository,
                             IngredientRepository ingredientRepository, RecipeRepository recipeRepository,
                             StockLedgerRepository ledgerRepository, EventPublisher eventPublisher,
                             AuditLogPort auditLog, Clock clock, IdGenerator idGenerator,
                             TransactionRunner transactions, int maxUnitsInProgress) {
        this.jobRepository = jobRepository;
        this.deliveryRepository = deliveryRepository;
        this.ingredientRepository = ingredientRepository;
        this.recipeRepository = recipeRepository;
        this.ledgerRepository = ledgerRepository;
        this.eventPublisher = eventPublisher;
        this.auditLog = auditLog;
        this.clock = clock;
        this.idGenerator = idGenerator;
        this.transactions = transactions;
        this.maxUnitsInProgress = maxUnitsInProgress;
    }

    // -------------------------------------------------------------------------------------------
    // Hand-over from the order service
    // -------------------------------------------------------------------------------------------

    /**
     * Opens a production job for every line of a confirmed order.
     *
     * <p>Idempotent: a redelivered {@code order.confirmed} finds the existing jobs and opens nothing
     * new, so a broker retry cannot double the kitchen's workload. A job is skipped only when one
     * already exists for that order line, which is exactly the dedupe key the event carries.
     */
    public void acceptConfirmedOrder(OrderConfirmedCommand command) {
        transactions.execute(() -> {
            UUID orderId = UUID.fromString(command.orderId());
            List<OrderConfirmedCommand.Line> lines = command.lines();
            if (lines.isEmpty()) {
                log.warn("Confirmed order {} has no lines; nothing to prepare", command.orderNumber());
                return;
            }

            int newUnits = lines.stream().mapToInt(OrderConfirmedCommand.Line::quantity).sum();
            List<OrderConfirmedCommand.Line> fresh = lines.stream()
                    .filter(line -> !jobRepository.existsForOrderAndVariant(orderId,
                            UUID.fromString(line.variantId())))
                    .toList();
            if (fresh.isEmpty()) {
                log.debug("Order {} already handed to the kitchen; ignoring redelivery",
                        command.orderNumber());
                return;
            }
            enforceCapacity(fresh.stream().mapToInt(OrderConfirmedCommand.Line::quantity).sum());

            Instant now = clock.now();
            UUID customerId = UUID.fromString(command.customerId());
            DeliveryAddress address = toAddress(command.deliveryAddress());
            Money charge = Money.ofMinorUnits(command.deliveryChargeMinorUnits(),
                    java.util.Currency.getInstance("INR"));
            for (OrderConfirmedCommand.Line line : fresh) {
                ProductionJob job = ProductionJob.open(idGenerator.nextId(), orderId, command.orderNumber(),
                        customerId, UUID.fromString(line.productId()), UUID.fromString(line.variantId()),
                        line.variantLabel(), Quantity.of(line.quantity()), address, charge, now);
                jobRepository.save(job);
            }
            log.info("Opened {} production job(s) for confirmed order {} ({} units)", fresh.size(),
                    command.orderNumber(), newUnits);
        });
    }

    /**
     * Abandons the jobs of a cancelled order and returns any ingredients already taken.
     *
     * <p>Returning the stock matters: if preparation had started, the ingredients are already out of
     * the ledger, and leaving them consumed for chocolate that will never be made would make the
     * seller's stock level wrong.
     */
    public void handleOrderCancelled(OrderCancelledCommand command) {
        transactions.execute(() -> {
            UUID orderId = UUID.fromString(command.orderId());
            Instant now = clock.now();
            for (ProductionJob job : jobRepository.findByOrderId(orderId)) {
                if (job.ingredientsConsumed()) {
                    returnIngredients(job, now);
                }
                if (!job.status().isTerminal()) {
                    job.cancel(command.reason(), ACTOR_SYSTEM, now);
                    jobRepository.save(job);
                }
            }
        });
    }

    // -------------------------------------------------------------------------------------------
    // Seller steps
    // -------------------------------------------------------------------------------------------

    /**
     * Advances one production job.
     *
     * <p>One entry point dispatching on the requested action. The aggregate refuses a step that is not
     * legal from the current status, so the seller gets a clear error rather than a corrupted job.
     * Starting preparation is the one step with a side effect beyond the status: it takes the recipe's
     * ingredients out of stock, once, guarded by the job's own consumption flag.
     */
    public ProductionJobView advance(String adminId, ProductionProgressCommand command) {
        return transactions.execute(() -> {
            ProductionJob job = requireJob(command.jobId());
            Instant now = clock.now();
            switch (command.action()) {
                case ACKNOWLEDGE -> job.acknowledge(adminId, now);
                case START -> {
                    job.startPreparation(adminId, now);
                    consumeIngredients(job, now);
                }
                case HOLD -> job.hold(command.reason(), adminId, now);
                case RESUME -> job.resume(adminId, now);
                case COMPLETE -> job.completePreparation(adminId, now);
                case PACKAGING -> job.startPackaging(adminId, now);
                case READY_FOR_DISPATCH -> {
                    job.markReadyForDispatch(adminId, now);
                    openDeliveryIfAbsent(job, now);
                }
                case CANCEL -> {
                    if (job.ingredientsConsumed()) {
                        returnIngredients(job, now);
                    }
                    job.cancel(command.reason(), adminId, now);
                }
            }
            ProductionJob saved = jobRepository.save(job);
            eventPublisher.publishAll(job.pullDomainEvents());
            audit(ACTOR_ADMIN, adminId, "production." + command.action().name().toLowerCase(), TARGET_JOB,
                    saved.id().toString(), "SUCCESS", command.reason());
            return FulfilmentViewMapper.toView(saved);
        });
    }

    /** The seller attaches a note to a job. */
    public ProductionJobView addNote(String adminId, AddProductionNoteCommand command) {
        return transactions.execute(() -> {
            ProductionJob job = requireJob(command.jobId());
            job.addNote(command.text(), adminId, clock.now());
            ProductionJob saved = jobRepository.save(job);
            audit(ACTOR_ADMIN, adminId, "production.note.add", TARGET_JOB, saved.id().toString(), "SUCCESS",
                    null);
            return FulfilmentViewMapper.toView(saved);
        });
    }

    // -------------------------------------------------------------------------------------------

    /**
     * Takes the recipe's ingredients out of stock for a job, once.
     *
     * <p>Does nothing when the variant has no recipe — that is a normal state, not an error. The
     * consumption and the status change share this transaction, so a stock shortage rolls the status
     * back too and the job stays where it was rather than appearing to have started without its
     * ingredients.
     */
    private void consumeIngredients(ProductionJob job, Instant now) {
        if (job.ingredientsConsumed()) {
            return;
        }
        List<RecipeLine> recipe = recipeRepository.findByVariantId(job.variantId());
        if (recipe.isEmpty()) {
            return;
        }
        int units = job.quantity().value();
        for (RecipeLine line : recipe) {
            Ingredient ingredient = ingredientRepository.findById(line.ingredientId())
                    .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND,
                            "An ingredient in this variant's recipe no longer exists"));
            var required = line.forQuantity(units);
            ingredient.consume(required, now);
            ingredientRepository.save(ingredient);
            ledgerRepository.append(StockLedgerEntry.of(idGenerator.nextId(), ingredient.id(),
                    StockLedgerReason.CONSUMED, required.negate(), ingredient.onHand(), job.id(),
                    "Consumed preparing " + job.orderNumber(), now));
            eventPublisher.publishAll(ingredient.pullDomainEvents());
        }
        job.markIngredientsConsumed(now);
    }

    /** Puts back the ingredients a job took, e.g. because it was cancelled. */
    private void returnIngredients(ProductionJob job, Instant now) {
        List<RecipeLine> recipe = recipeRepository.findByVariantId(job.variantId());
        int units = job.quantity().value();
        for (RecipeLine line : recipe) {
            ingredientRepository.findById(line.ingredientId()).ifPresent(ingredient -> {
                var returned = line.forQuantity(units);
                ingredient.adjust(returned, "Returned from cancelled " + job.orderNumber(), now);
                ingredientRepository.save(ingredient);
                ledgerRepository.append(StockLedgerEntry.of(idGenerator.nextId(), ingredient.id(),
                        StockLedgerReason.ADJUSTED, returned, ingredient.onHand(), job.id(),
                        "Returned from cancelled " + job.orderNumber(), now));
            });
        }
        job.markIngredientsReturned(now);
    }

    /**
     * Opens the delivery record when a job becomes ready, unless the order already has one.
     *
     * <p>The delivery is created here rather than on a separate event so the hand-off from production
     * to delivery is one transaction: an order can never be "ready for dispatch" with no delivery to
     * assign. The address and charge come from the job's own copies, so nothing is read back from the
     * order service.
     */
    private void openDeliveryIfAbsent(ProductionJob job, Instant now) {
        if (deliveryRepository.existsForOrder(job.orderId())) {
            return;
        }
        DeliveryAddress address = job.deliveryAddress();
        if (address == null) {
            log.warn("Job {} is ready for dispatch but has no delivery address on file; the delivery "
                    + "record cannot be created yet", job.id());
            return;
        }
        Delivery delivery = Delivery.open(idGenerator.nextId(), job.orderId(), job.orderNumber(),
                job.customerId(), address, job.deliveryCharge() == null ? Money.inr(0) : job.deliveryCharge(),
                now);
        deliveryRepository.save(delivery);
    }

    /**
     * Refuses a hand-over that would exceed the seller's configured preparation capacity.
     *
     * <p>The count is taken under a lock, so two confirmed orders arriving at the same moment cannot
     * both see room and both take it. A cap of zero means the seller has not set one, and every order
     * is accepted.
     */
    private void enforceCapacity(int requestedUnits) {
        if (maxUnitsInProgress <= 0) {
            return;
        }
        long inProgress = jobRepository.countUnitsOccupyingCapacityForUpdate();
        if (inProgress + requestedUnits > maxUnitsInProgress) {
            throw new ValidationException(
                    "The kitchen is already preparing " + inProgress + " of " + maxUnitsInProgress
                            + " units it can make at once; this order cannot be accepted yet");
        }
    }

    /** Rebuilds the fulfilment service's own address type from the event's wire shape. */
    private static DeliveryAddress toAddress(OrderConfirmedCommand.Address address) {
        if (address == null) {
            return null;
        }
        return new DeliveryAddress(address.recipientName(),
                com.choco.core.domain.PhoneNumber.of(address.mobile()), address.line1(), address.line2(),
                address.city(), address.state(), Pincode.of(address.pincode()),
                address.deliveryInstructions());
    }

    private ProductionJob requireJob(UUID jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "Production job not found"));
    }

    private void audit(String actorType, String actorId, String action, String targetType, String targetId,
                       String outcome, String detail) {
        auditLog.record(actorType, actorId, action, targetType, targetId, outcome, detail, null);
    }

    /** The configured capacity, exposed for the admin dashboard's "workload" card. */
    public int maxUnitsInProgress() {
        return maxUnitsInProgress;
    }
}
