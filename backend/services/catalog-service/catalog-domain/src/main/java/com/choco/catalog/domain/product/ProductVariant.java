package com.choco.catalog.domain.product;

import com.choco.core.domain.Money;
import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;
import java.util.UUID;

/**
 * A buyable configuration of a product: a size, a weight, a flavour, or a combination.
 *
 * <p>Modelled as a child of the {@link Product} aggregate rather than an aggregate of its own. A
 * variant has no meaning without its product, is always edited as part of the product, and is never
 * loaded on its own, so giving it its own consistency boundary would add a repository and a
 * transaction for no benefit.
 *
 * <p>Price lives here, not on the product, because the seller charges by configuration: a 250 g bar
 * and a 1 kg box of the same chocolate do not share a price. The product carries no price of its own,
 * which removes the question of which one is authoritative.
 */
public class ProductVariant {

    public static final int MAX_LABEL_LENGTH = 80;
    public static final int MAX_SKU_LENGTH = 64;

    /**
     * Upper bound on a single order line, so one customer cannot ask for more than the seller can
     * physically make in a batch. Configurable per variant; this is the hard ceiling.
     */
    public static final int MAX_ORDER_QUANTITY = 1_000;

    private final UUID id;
    private final UUID productId;
    private String label;
    private String sku;
    private Money price;
    private int minOrderQuantity;
    private int maxOrderQuantity;
    private boolean active;
    private int displayOrder;

    private ProductVariant(UUID id, UUID productId, String label, String sku, Money price,
                           int minOrderQuantity, int maxOrderQuantity, boolean active, int displayOrder) {
        this.id = id;
        this.productId = productId;
        this.label = requireLabel(label);
        this.sku = normaliseSku(sku);
        this.price = requirePrice(price);
        this.minOrderQuantity = requireMin(minOrderQuantity);
        this.maxOrderQuantity = requireMax(maxOrderQuantity, this.minOrderQuantity);
        this.active = active;
        this.displayOrder = displayOrder;
    }

    public static ProductVariant create(UUID id, UUID productId, String label, String sku, Money price,
                                        int minOrderQuantity, int maxOrderQuantity, int displayOrder) {
        if (productId == null) {
            throw new IllegalArgumentException("Product id is required");
        }
        return new ProductVariant(id, productId, label, sku, price, minOrderQuantity, maxOrderQuantity,
                true, displayOrder);
    }

    /** Rehydrates from persistence without re-running creation rules on already-valid stored data. */
    public static ProductVariant rehydrate(UUID id, UUID productId, String label, String sku, Money price,
                                           int minOrderQuantity, int maxOrderQuantity, boolean active,
                                           int displayOrder) {
        return new ProductVariant(id, productId, label, sku, price, minOrderQuantity, maxOrderQuantity,
                active, displayOrder);
    }

    /**
     * Applies an edit.
     *
     * <p>Every field is validated into a local before any is assigned, so a rejected later field
     * cannot leave the variant half-updated and then saved as a mix of old and new values.
     */
    public void update(String label, String sku, Money price, int minOrderQuantity, int maxOrderQuantity) {
        String newLabel = requireLabel(label);
        String newSku = normaliseSku(sku);
        Money newPrice = requirePrice(price);
        int newMin = requireMin(minOrderQuantity);
        int newMax = requireMax(maxOrderQuantity, newMin);

        this.label = newLabel;
        this.sku = newSku;
        this.price = newPrice;
        this.minOrderQuantity = newMin;
        this.maxOrderQuantity = newMax;
    }

    /** Re-prices the variant on its own, which is the change customers and other services care about. */
    public void changePrice(Money newPrice) {
        this.price = requirePrice(newPrice);
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public void reorder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    /**
     * Whether an order line for this variant is acceptable.
     *
     * <p>Checked here rather than in a caller so the minimum, the maximum and the active flag are
     * enforced together. A caller that checked only the maximum would let a deactivated variant be
     * ordered at the minimum quantity.
     */
    public void assertOrderable(int quantity) {
        if (!active) {
            throw new BusinessRuleException(ErrorCode.PRODUCT_UNAVAILABLE,
                    "This option is no longer available: " + label);
        }
        if (quantity < minOrderQuantity) {
            throw new BusinessRuleException(ErrorCode.QUANTITY_LIMIT_EXCEEDED,
                    "The minimum order for " + label + " is " + minOrderQuantity);
        }
        if (quantity > maxOrderQuantity) {
            throw new BusinessRuleException(ErrorCode.QUANTITY_LIMIT_EXCEEDED,
                    "The maximum order for " + label + " is " + maxOrderQuantity);
        }
    }

    private static String requireLabel(String value) {
        String trimmed = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Variant label is required");
        }
        if (trimmed.length() > MAX_LABEL_LENGTH) {
            throw new IllegalArgumentException("Variant label must be at most " + MAX_LABEL_LENGTH + " characters");
        }
        return trimmed;
    }

    private static String normaliseSku(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim().toUpperCase(java.util.Locale.ROOT);
        if (trimmed.length() > MAX_SKU_LENGTH) {
            throw new IllegalArgumentException("SKU must be at most " + MAX_SKU_LENGTH + " characters");
        }
        return trimmed;
    }

    private static Money requirePrice(Money price) {
        if (price == null) {
            throw new IllegalArgumentException("Variant price is required");
        }
        if (price.isNegative() || price.isZero()) {
            throw new IllegalArgumentException("Variant price must be greater than zero");
        }
        return price;
    }

    private static int requireMin(int min) {
        if (min < 1) {
            throw new IllegalArgumentException("Minimum order quantity must be at least 1");
        }
        return min;
    }

    private static int requireMax(int max, int min) {
        if (max < min) {
            throw new IllegalArgumentException("Maximum order quantity must not be less than the minimum");
        }
        if (max > MAX_ORDER_QUANTITY) {
            throw new IllegalArgumentException("Maximum order quantity must not exceed " + MAX_ORDER_QUANTITY);
        }
        return max;
    }

    public UUID id() {
        return id;
    }

    public UUID productId() {
        return productId;
    }

    public String label() {
        return label;
    }

    public String sku() {
        return sku;
    }

    public Money price() {
        return price;
    }

    public int minOrderQuantity() {
        return minOrderQuantity;
    }

    public int maxOrderQuantity() {
        return maxOrderQuantity;
    }

    public boolean isActive() {
        return active;
    }

    public int displayOrder() {
        return displayOrder;
    }
}
