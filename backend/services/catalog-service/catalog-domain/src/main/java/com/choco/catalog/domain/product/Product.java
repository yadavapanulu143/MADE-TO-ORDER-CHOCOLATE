package com.choco.catalog.domain.product;

import com.choco.catalog.domain.event.ProductCreated;
import com.choco.catalog.domain.event.ProductPublished;
import com.choco.catalog.domain.event.ProductUnpublished;
import com.choco.catalog.domain.event.ProductUpdated;
import com.choco.catalog.domain.event.VariantPriceChanged;
import com.choco.core.domain.AggregateRoot;
import com.choco.core.domain.Money;
import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A chocolate product, with its variants, images and descriptive attributes.
 *
 * <p>One aggregate rather than four. Variants, images and attributes have no independent life: they
 * are created with the product, edited through the product, and never loaded on their own. Splitting
 * them into separate aggregates would mean four repositories and a way to keep them consistent across
 * transactions, for a product the seller edits as one thing. The child collections are loaded with the
 * root and saved with it.
 *
 * <p>The aggregate owns no price of its own. Price belongs to a {@link ProductVariant}, because the
 * seller charges by configuration, and a "from" price shown on the storefront is derived at read time
 * rather than stored, so it can never disagree with the variants it summarises.
 *
 * <p>Publication is guarded rather than a free flag. A product cannot be published with no active
 * variant, because a customer who opens such a page has nothing to buy; the invariant is enforced
 * here so no administration screen can bypass it.
 */
public class Product extends AggregateRoot {

    public static final int MAX_NAME_LENGTH = 150;
    public static final int MAX_SHORT_DESCRIPTION = 500;
    public static final int MAX_DESCRIPTION = 5_000;
    public static final int MAX_PREPARATION_INFO = 1_000;
    public static final int MAX_DELIVERY_INFO = 1_000;
    public static final int MAX_IMAGES = 12;
    public static final int MAX_VARIANTS = 50;
    public static final int MAX_ATTRIBUTES = 30;

    private final UUID id;
    private final Slug slug;
    private String name;
    private String shortDescription;
    private String description;
    private String preparationInfo;
    private String deliveryInfo;
    private ProductStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private final List<ProductVariant> variants = new ArrayList<>();
    private final List<ProductImage> images = new ArrayList<>();
    private final List<ProductAttribute> attributes = new ArrayList<>();

    private Product(UUID id, Slug slug, String name, String shortDescription, String description,
                    String preparationInfo, String deliveryInfo, ProductStatus status, Instant createdAt) {
        this.id = id;
        this.slug = slug;
        this.name = requireName(name);
        this.shortDescription = normaliseOptional(shortDescription, MAX_SHORT_DESCRIPTION, "Short description");
        this.description = normaliseOptional(description, MAX_DESCRIPTION, "Description");
        this.preparationInfo = normaliseOptional(preparationInfo, MAX_PREPARATION_INFO, "Preparation information");
        this.deliveryInfo = normaliseOptional(deliveryInfo, MAX_DELIVERY_INFO, "Delivery information");
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    /**
     * Creates a product in {@link ProductStatus#DRAFT}.
     *
     * <p>Draft by default so a product is never visible to customers before the seller has added a
     * variant, a price and a photograph. Publishing is a separate, deliberate act.
     */
    public static Product create(UUID id, String name, String shortDescription, String description,
                                 String preparationInfo, String deliveryInfo, Instant now) {
        Product product = new Product(id, Slug.fromName(name), name, shortDescription, description,
                preparationInfo, deliveryInfo, ProductStatus.DRAFT, now);
        product.registerEvent(new ProductCreated(UUID.randomUUID(), now, id.toString(),
                product.name, product.slug.value()));
        return product;
    }

    /** Rehydrates from persistence without re-running creation rules or re-raising events. */
    public static Product rehydrate(UUID id, Slug slug, String name, String shortDescription,
                                    String description, String preparationInfo, String deliveryInfo,
                                    ProductStatus status, Instant createdAt, Instant updatedAt) {
        Product product = new Product(id, slug, name, shortDescription, description, preparationInfo,
                deliveryInfo, status, createdAt);
        product.updatedAt = updatedAt;
        return product;
    }

    /** Reattaches persisted children after the root has been loaded. Called only by the persistence mapper. */
    public void attachVariants(List<ProductVariant> loaded) {
        variants.clear();
        variants.addAll(loaded);
    }

    public void attachImages(List<ProductImage> loaded) {
        images.clear();
        images.addAll(loaded);
    }

    public void attachAttributes(List<ProductAttribute> loaded) {
        attributes.clear();
        attributes.addAll(loaded);
    }

    /**
     * Edits the descriptive content.
     *
     * <p>The slug is deliberately not regenerated. It is the product's public address, and rewriting
     * it when the name changes would break every link customers have already shared.
     */
    public void updateDetails(String name, String shortDescription, String description,
                              String preparationInfo, String deliveryInfo, Instant now) {
        String newName = requireName(name);
        String newShort = normaliseOptional(shortDescription, MAX_SHORT_DESCRIPTION, "Short description");
        String newDescription = normaliseOptional(description, MAX_DESCRIPTION, "Description");
        String newPreparation = normaliseOptional(preparationInfo, MAX_PREPARATION_INFO, "Preparation information");
        String newDelivery = normaliseOptional(deliveryInfo, MAX_DELIVERY_INFO, "Delivery information");

        this.name = newName;
        this.shortDescription = newShort;
        this.description = newDescription;
        this.preparationInfo = newPreparation;
        this.deliveryInfo = newDelivery;
        this.updatedAt = now;

        registerEvent(new ProductUpdated(UUID.randomUUID(), now, id.toString(), newName));
    }

    // -------------------------------------------------------------------------------------------
    // Variants
    // -------------------------------------------------------------------------------------------

    public ProductVariant addVariant(UUID variantId, String label, String sku, Money price,
                                     int minOrderQuantity, int maxOrderQuantity, Instant now) {
        if (variants.size() >= MAX_VARIANTS) {
            throw new BusinessRuleException(ErrorCode.CONFLICT,
                    "A product may have at most " + MAX_VARIANTS + " variants");
        }
        String normalisedSku = sku == null ? null : sku.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalisedSku != null && variants.stream()
                .anyMatch(v -> normalisedSku.equals(v.sku()))) {
            throw new BusinessRuleException(ErrorCode.CONFLICT, "Another variant already uses SKU " + normalisedSku);
        }

        ProductVariant variant = ProductVariant.create(variantId, id, label, sku, price,
                minOrderQuantity, maxOrderQuantity, variants.size());
        variants.add(variant);
        this.updatedAt = now;
        registerEvent(new VariantPriceChanged(UUID.randomUUID(), now, id.toString(),
                variantId.toString(), price.minorUnits(), price.currency().getCurrencyCode()));
        return variant;
    }

    public ProductVariant updateVariant(UUID variantId, String label, String sku, Money price,
                                        int minOrderQuantity, int maxOrderQuantity, Instant now) {
        ProductVariant variant = requireVariant(variantId);
        Money previousPrice = variant.price();
        variant.update(label, sku, price, minOrderQuantity, maxOrderQuantity);
        this.updatedAt = now;
        if (!previousPrice.equals(price)) {
            registerEvent(new VariantPriceChanged(UUID.randomUUID(), now, id.toString(),
                    variantId.toString(), price.minorUnits(), price.currency().getCurrencyCode()));
        }
        return variant;
    }

    public void removeVariant(UUID variantId, Instant now) {
        ProductVariant variant = requireVariant(variantId);
        // Removing the last active variant from a published product would leave a page with nothing to
        // buy. The seller must unpublish first, which is an explicit decision rather than a side effect.
        if (status == ProductStatus.PUBLISHED && activeVariantCountExcluding(variantId) == 0) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Unpublish the product before removing its last available variant");
        }
        variants.remove(variant);
        this.updatedAt = now;
    }

    private long activeVariantCountExcluding(UUID variantId) {
        return variants.stream().filter(v -> v.isActive() && !v.id().equals(variantId)).count();
    }

    // -------------------------------------------------------------------------------------------
    // Images
    // -------------------------------------------------------------------------------------------

    public ProductImage addImage(UUID imageId, String storageKey, String contentType, long sizeBytes,
                                 String altText, Instant now) {
        if (images.size() >= MAX_IMAGES) {
            throw new BusinessRuleException(ErrorCode.CONFLICT,
                    "A product may have at most " + MAX_IMAGES + " images");
        }
        ProductImage image = ProductImage.create(imageId, id, storageKey, contentType, sizeBytes, altText,
                images.size());
        // The first image is the primary one, so a product is never left without a representative
        // picture simply because nobody nominated one.
        if (images.isEmpty()) {
            image.markPrimary();
        }
        images.add(image);
        this.updatedAt = now;
        return image;
    }

    public ProductImage updateImage(UUID imageId, String altText, int displayOrder, Instant now) {
        ProductImage image = requireImage(imageId);
        image.describe(altText, displayOrder);
        this.updatedAt = now;
        return image;
    }

    /** Promotes one image to primary and demotes the rest, so exactly one is ever primary. */
    public void makeImagePrimary(UUID imageId, Instant now) {
        ProductImage chosen = requireImage(imageId);
        images.forEach(ProductImage::clearPrimary);
        chosen.markPrimary();
        this.updatedAt = now;
    }

    public void removeImage(UUID imageId, Instant now) {
        ProductImage image = requireImage(imageId);
        boolean wasPrimary = image.isPrimary();
        images.remove(image);
        if (wasPrimary && !images.isEmpty()) {
            images.get(0).markPrimary();
        }
        this.updatedAt = now;
    }

    // -------------------------------------------------------------------------------------------
    // Attributes
    // -------------------------------------------------------------------------------------------

    public ProductAttribute addAttribute(UUID attributeId, String name, String value, Instant now) {
        if (attributes.size() >= MAX_ATTRIBUTES) {
            throw new BusinessRuleException(ErrorCode.CONFLICT,
                    "A product may have at most " + MAX_ATTRIBUTES + " attributes");
        }
        String normalised = ProductAttribute.normaliseName(name);
        if (attributes.stream().anyMatch(a -> ProductAttribute.normaliseName(a.name()).equals(normalised))) {
            throw new BusinessRuleException(ErrorCode.CONFLICT, "An attribute named '" + name + "' already exists");
        }
        ProductAttribute attribute = ProductAttribute.create(attributeId, id, name, value, attributes.size());
        attributes.add(attribute);
        this.updatedAt = now;
        return attribute;
    }

    public void removeAttribute(UUID attributeId, Instant now) {
        boolean removed = attributes.removeIf(a -> a.id().equals(attributeId));
        if (!removed) {
            throw new BusinessRuleException(ErrorCode.NOT_FOUND, "Attribute not found on this product");
        }
        this.updatedAt = now;
    }

    // -------------------------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------------------------

    /**
     * Publishes the product so it appears in the customer catalogue.
     *
     * <p>Refused unless at least one active variant exists: a published product with nothing orderable
     * is a broken page, and the seller would not discover it until a customer complained.
     */
    public void publish(Instant now) {
        if (status == ProductStatus.ARCHIVED) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "An archived product cannot be published; create a new product instead");
        }
        if (activeVariantCountExcluding(null) == 0) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Add at least one available variant before publishing");
        }
        if (status == ProductStatus.PUBLISHED) {
            return;
        }
        this.status = ProductStatus.PUBLISHED;
        this.updatedAt = now;
        registerEvent(new ProductPublished(UUID.randomUUID(), now, id.toString(), slug.value()));
    }

    /** Returns the product to draft, hiding it without discarding it. */
    public void unpublish(Instant now) {
        if (status != ProductStatus.PUBLISHED) {
            return;
        }
        this.status = ProductStatus.DRAFT;
        this.updatedAt = now;
        registerEvent(new ProductUnpublished(UUID.randomUUID(), now, id.toString(), status.name()));
    }

    /**
     * Withdraws the product from sale permanently.
     *
     * <p>Archived rather than deleted. Historical orders reference the product and its variant prices;
     * a hard delete would either break those references or cascade into order data the catalog service
     * does not own. Archiving keeps history intact and simply removes the product from the storefront.
     */
    public void archive(Instant now) {
        if (status == ProductStatus.ARCHIVED) {
            return;
        }
        this.status = ProductStatus.ARCHIVED;
        this.updatedAt = now;
        variants.forEach(ProductVariant::deactivate);
        registerEvent(new ProductUnpublished(UUID.randomUUID(), now, id.toString(), status.name()));
    }

    public boolean isOrderable() {
        return status == ProductStatus.PUBLISHED && activeVariantCountExcluding(null) > 0;
    }

    public boolean isVisibleToCustomers() {
        return status == ProductStatus.PUBLISHED;
    }

    // -------------------------------------------------------------------------------------------
    // Derived reads
    // -------------------------------------------------------------------------------------------

    /** Lowest active price, or empty when nothing is on sale. Derived, never stored. */
    public Optional<Money> fromPrice() {
        return variants.stream()
                .filter(ProductVariant::isActive)
                .map(ProductVariant::price)
                .min(Comparator.naturalOrder());
    }

    public Optional<ProductImage> primaryImage() {
        return images.stream().filter(ProductImage::isPrimary).findFirst()
                .or(() -> images.stream().findFirst());
    }

    public Optional<ProductVariant> variant(UUID variantId) {
        return variants.stream().filter(v -> v.id().equals(variantId)).findFirst();
    }

    /** Variants in display order, so the storefront renders sizes in the order the seller chose. */
    public List<ProductVariant> variants() {
        return variants.stream()
                .sorted(Comparator.comparingInt(ProductVariant::displayOrder))
                .toList();
    }

    public List<ProductImage> images() {
        return images.stream()
                .sorted(Comparator.comparingInt(ProductImage::displayOrder))
                .toList();
    }

    public List<ProductAttribute> attributes() {
        return attributes.stream()
                .sorted(Comparator.comparingInt(ProductAttribute::displayOrder))
                .toList();
    }

    /** Validates an order line for a named variant in one place, including the product's own state. */
    public void assertOrderable(UUID variantId, int quantity) {
        if (status != ProductStatus.PUBLISHED) {
            throw new BusinessRuleException(ErrorCode.PRODUCT_UNAVAILABLE,
                    "This product is not currently available");
        }
        ProductVariant variant = requireVariant(variantId);
        variant.assertOrderable(quantity);
    }

    private ProductVariant requireVariant(UUID variantId) {
        return variant(variantId).orElseThrow(() -> new BusinessRuleException(ErrorCode.NOT_FOUND,
                "Variant not found on this product: " + variantId));
    }

    private ProductImage requireImage(UUID imageId) {
        return images.stream().filter(i -> i.id().equals(imageId)).findFirst()
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.NOT_FOUND,
                        "Image not found on this product: " + imageId));
    }

    private static String requireName(String value) {
        String trimmed = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Product name is required");
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Product name must be at most " + MAX_NAME_LENGTH + " characters");
        }
        return trimmed;
    }

    private static String normaliseOptional(String value, int maxLength, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(field + " must be at most " + maxLength + " characters");
        }
        return trimmed;
    }

    public UUID id() {
        return id;
    }

    public Slug slug() {
        return slug;
    }

    public String name() {
        return name;
    }

    public String shortDescription() {
        return shortDescription;
    }

    public String description() {
        return description;
    }

    public String preparationInfo() {
        return preparationInfo;
    }

    public String deliveryInfo() {
        return deliveryInfo;
    }

    public ProductStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
