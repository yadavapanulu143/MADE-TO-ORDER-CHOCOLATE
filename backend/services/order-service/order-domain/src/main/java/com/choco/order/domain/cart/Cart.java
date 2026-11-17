package com.choco.order.domain.cart;

import com.choco.core.domain.AggregateRoot;
import com.choco.core.domain.Money;
import com.choco.core.domain.Quantity;
import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;
import com.choco.core.error.NotFoundException;
import com.choco.core.error.ValidationException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A customer's working basket, before it becomes an order.
 *
 * <p>A separate aggregate from {@link com.choco.order.domain.order.Order}, not a state of one. A cart
 * is mutable, has no money owed, and is thrown away when it becomes an order; an order is immutable in
 * everything that matters and is kept forever. Modelling them as one object with a "is it checked out
 * yet" flag would mean every order query has to filter carts out and every cart edit has to avoid
 * tripping an order invariant.
 *
 * <p>One active cart per customer. The cart holds display copies of the name and price so the
 * storefront can render it, but the price it holds is not trusted: checkout re-reads the catalogue and
 * the order is priced from that, so a stale cart price cannot become a stale order price.
 */
public class Cart extends AggregateRoot {

    /** Ceiling on distinct lines, so a cart cannot grow without bound. */
    public static final int MAX_LINES = 50;

    /**
     * Default per-line ceiling until the catalogue states the variant's own maximum.
     *
     * <p>Kept as a safe default rather than a business figure: the real bound is the variant's
     * configured maximum, which checkout enforces against the catalogue. This only stops an obviously
     * absurd quantity being stored.
     */
    public static final int MAX_QUANTITY_PER_LINE = 1_000;

    private final UUID id;
    private final UUID customerId;
    private final List<CartLine> lines = new ArrayList<>();
    private final Instant createdAt;
    private Instant updatedAt;

    private Cart(UUID id, UUID customerId, Instant createdAt) {
        this.id = id;
        this.customerId = customerId;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static Cart empty(UUID id, UUID customerId, Instant now) {
        if (customerId == null) {
            throw new IllegalArgumentException("A cart belongs to a customer");
        }
        return new Cart(id, customerId, now);
    }

    /** Rehydrates from persistence without re-running creation rules. */
    public static Cart rehydrate(UUID id, UUID customerId, Instant createdAt, Instant updatedAt) {
        Cart cart = new Cart(id, customerId, createdAt);
        cart.updatedAt = updatedAt;
        return cart;
    }

    public void attachLines(List<CartLine> loaded) {
        lines.clear();
        lines.addAll(loaded);
    }

    /**
     * Adds a quantity of a variant, or increases it when the variant is already present.
     *
     * <p>The unit price and display text are refreshed from the value passed in — which the application
     * layer read from the catalogue — so the cart never shows a price the seller has since changed.
     * The result is capped at the per-line ceiling; a larger request is refused rather than silently
     * truncated, so the customer is told their quantity was too large instead of quietly getting fewer.
     */
    public void addLine(UUID productId, UUID variantId, String productName, String variantLabel,
                        Money unitPrice, int quantity, Instant now) {
        if (quantity < 1) {
            throw new ValidationException(ErrorCode.VALIDATION_FAILED, "Quantity must be at least 1");
        }
        Optional<CartLine> existing = findLine(variantId);
        int target = existing.map(line -> line.quantity().value()).orElse(0) + quantity;
        if (target > MAX_QUANTITY_PER_LINE) {
            throw new BusinessRuleException(ErrorCode.QUANTITY_LIMIT_EXCEEDED,
                    "You can order at most " + MAX_QUANTITY_PER_LINE + " of one item");
        }

        if (existing.isPresent()) {
            int index = lines.indexOf(existing.get());
            lines.set(index, existing.get().withQuantity(Quantity.of(target))
                    .withPriceAndName(unitPrice, productName, variantLabel));
        } else {
            if (lines.size() >= MAX_LINES) {
                throw new BusinessRuleException(ErrorCode.CONFLICT,
                        "A cart may contain at most " + MAX_LINES + " different items");
            }
            lines.add(new CartLine(productId, variantId, productName, variantLabel, unitPrice,
                    Quantity.of(target)));
        }
        this.updatedAt = now;
    }

    /** Sets an absolute quantity for a line, used when the customer edits the number directly. */
    public void setQuantity(UUID variantId, int quantity, Instant now) {
        if (quantity < 1) {
            throw new ValidationException(ErrorCode.VALIDATION_FAILED, "Quantity must be at least 1");
        }
        if (quantity > MAX_QUANTITY_PER_LINE) {
            throw new BusinessRuleException(ErrorCode.QUANTITY_LIMIT_EXCEEDED,
                    "You can order at most " + MAX_QUANTITY_PER_LINE + " of one item");
        }
        CartLine line = findLine(variantId).orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND,
                "That item is not in your cart"));
        lines.set(lines.indexOf(line), line.withQuantity(Quantity.of(quantity)));
        this.updatedAt = now;
    }

    public void removeLine(UUID variantId, Instant now) {
        boolean removed = lines.removeIf(line -> line.variantId().equals(variantId));
        if (!removed) {
            throw new NotFoundException(ErrorCode.NOT_FOUND, "That item is not in your cart");
        }
        this.updatedAt = now;
    }

    public void clear(Instant now) {
        lines.clear();
        this.updatedAt = now;
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    public Optional<CartLine> findLine(UUID variantId) {
        return lines.stream().filter(line -> line.variantId().equals(variantId)).findFirst();
    }

    /** Sum of the line totals, using the display prices. The order is re-priced from the catalogue. */
    public Money estimatedTotal() {
        Money sum = Money.inr(0);
        for (CartLine line : lines) {
            sum = sum.plus(line.lineTotal());
        }
        return sum;
    }

    public int totalItems() {
        return lines.stream().mapToInt(line -> line.quantity().value()).sum();
    }

    /** Guards every operation so one customer cannot touch another's cart. */
    public void assertOwnedBy(UUID callerCustomerId) {
        if (callerCustomerId == null || !customerId.equals(callerCustomerId)) {
            throw new BusinessRuleException(ErrorCode.FORBIDDEN, "You are not allowed to access this cart");
        }
    }

    public UUID id() {
        return id;
    }

    public UUID customerId() {
        return customerId;
    }

    public List<CartLine> lines() {
        return lines.stream().sorted(Comparator.comparing(CartLine::variantId)).toList();
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
