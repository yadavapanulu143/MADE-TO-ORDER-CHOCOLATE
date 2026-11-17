package com.choco.catalog.adapter.out.persistence;

import com.choco.catalog.domain.product.Product;
import com.choco.catalog.domain.product.ProductAttribute;
import com.choco.catalog.domain.product.ProductImage;
import com.choco.catalog.domain.product.ProductStatus;
import com.choco.catalog.domain.product.ProductVariant;
import com.choco.catalog.domain.product.Slug;
import com.choco.catalog.domain.settings.WebsiteSettings;
import com.choco.core.domain.Money;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

/**
 * Translates between the JPA entities and the domain aggregates.
 *
 * <p>Deliberately explicit rather than reflection-based. A mapping library would infer the translation
 * from field names, and a column rename could then change the domain silently. Writing it out means the
 * compiler catches a mismatch and a reviewer can see, in one file, exactly which column feeds which
 * business field.
 *
 * <p>Every load path uses {@code rehydrate}, never {@code create}, so reading a product cannot re-run
 * creation rules or re-raise a creation event. That separation is what makes a read side-effect free.
 */
final class CatalogPersistenceMapper {

    private CatalogPersistenceMapper() {
    }

    // ---------------------------------------------------------------------------------------------
    // Product
    // ---------------------------------------------------------------------------------------------

    static ProductJpaEntity toEntity(Product product) {
        ProductJpaEntity entity = new ProductJpaEntity();
        entity.setId(product.id());
        entity.setSlug(product.slug().value());
        entity.setName(product.name());
        entity.setShortDescription(product.shortDescription());
        entity.setDescription(product.description());
        entity.setPreparationInfo(product.preparationInfo());
        entity.setDeliveryInfo(product.deliveryInfo());
        entity.setStatus(product.status().name());
        entity.setCreatedAt(product.createdAt());
        entity.setUpdatedAt(product.updatedAt());

        List<ProductVariantJpaEntity> variants = new ArrayList<>();
        for (ProductVariant variant : product.variants()) {
            variants.add(toEntity(variant, entity));
        }
        entity.setVariants(variants);

        List<ProductImageJpaEntity> images = new ArrayList<>();
        for (ProductImage image : product.images()) {
            images.add(toEntity(image, entity));
        }
        entity.setImages(images);

        List<ProductAttributeJpaEntity> attributes = new ArrayList<>();
        for (ProductAttribute attribute : product.attributes()) {
            attributes.add(toEntity(attribute, entity));
        }
        entity.setAttributes(attributes);

        return entity;
    }

    static Product toDomain(ProductJpaEntity entity) {
        Product product = Product.rehydrate(entity.getId(), Slug.of(entity.getSlug()), entity.getName(),
                entity.getShortDescription(), entity.getDescription(), entity.getPreparationInfo(),
                entity.getDeliveryInfo(), ProductStatus.valueOf(entity.getStatus()), entity.getCreatedAt(),
                entity.getUpdatedAt());

        // Children are mapped from the entity's own collections, which are already ordered by the
        // @OrderBy on each association, so display order survives a round trip.
        product.attachVariants(entity.getVariants().stream().map(CatalogPersistenceMapper::toDomain).toList());
        product.attachImages(entity.getImages().stream().map(CatalogPersistenceMapper::toDomain).toList());
        product.attachAttributes(entity.getAttributes().stream().map(CatalogPersistenceMapper::toDomain).toList());
        return product;
    }

    private static ProductVariantJpaEntity toEntity(ProductVariant variant, ProductJpaEntity product) {
        ProductVariantJpaEntity entity = new ProductVariantJpaEntity();
        entity.setId(variant.id());
        entity.setProduct(product);
        entity.setLabel(variant.label());
        entity.setSku(variant.sku());
        entity.setPriceMinorUnits(variant.price().minorUnits());
        entity.setCurrency(variant.price().currency().getCurrencyCode());
        entity.setMinOrderQuantity(variant.minOrderQuantity());
        entity.setMaxOrderQuantity(variant.maxOrderQuantity());
        entity.setActive(variant.isActive());
        entity.setDisplayOrder(variant.displayOrder());
        return entity;
    }

    private static ProductVariant toDomain(ProductVariantJpaEntity entity) {
        return ProductVariant.rehydrate(entity.getId(), entity.getProduct().getId(), entity.getLabel(),
                entity.getSku(), money(entity.getPriceMinorUnits(), entity.getCurrency()),
                entity.getMinOrderQuantity(), entity.getMaxOrderQuantity(), entity.isActive(),
                entity.getDisplayOrder());
    }

    private static ProductImageJpaEntity toEntity(ProductImage image, ProductJpaEntity product) {
        ProductImageJpaEntity entity = new ProductImageJpaEntity();
        entity.setId(image.id());
        entity.setProduct(product);
        entity.setStorageKey(image.storageKey());
        entity.setContentType(image.contentType());
        entity.setSizeBytes(image.sizeBytes());
        entity.setAltText(image.altText());
        entity.setDisplayOrder(image.displayOrder());
        entity.setPrimaryImage(image.isPrimary());
        return entity;
    }

    private static ProductImage toDomain(ProductImageJpaEntity entity) {
        return ProductImage.rehydrate(entity.getId(), entity.getProduct().getId(), entity.getStorageKey(),
                entity.getContentType(), entity.getSizeBytes(), entity.getAltText(),
                entity.getDisplayOrder(), entity.isPrimaryImage());
    }

    private static ProductAttributeJpaEntity toEntity(ProductAttribute attribute, ProductJpaEntity product) {
        ProductAttributeJpaEntity entity = new ProductAttributeJpaEntity();
        entity.setId(attribute.id());
        entity.setProduct(product);
        entity.setName(attribute.name());
        entity.setValue(attribute.value());
        entity.setDisplayOrder(attribute.displayOrder());
        return entity;
    }

    private static ProductAttribute toDomain(ProductAttributeJpaEntity entity) {
        return ProductAttribute.rehydrate(entity.getId(), entity.getProduct().getId(), entity.getName(),
                entity.getValue(), entity.getDisplayOrder());
    }

    // ---------------------------------------------------------------------------------------------
    // Settings
    // ---------------------------------------------------------------------------------------------

    static WebsiteSettingsJpaEntity toEntity(WebsiteSettings settings) {
        WebsiteSettingsJpaEntity entity = new WebsiteSettingsJpaEntity();
        entity.setId(settings.id());
        entity.setSellerName(settings.sellerName());
        entity.setContactEmail(settings.contactEmail());
        entity.setContactPhone(settings.contactPhone());
        entity.setContactAddress(settings.contactAddress());
        entity.setPrivacyPolicy(settings.privacyPolicy());
        entity.setTermsAndConditions(settings.termsAndConditions());
        entity.setShippingPolicy(settings.shippingPolicy());
        entity.setRefundPolicy(settings.refundPolicy());
        entity.setUpdatedAt(settings.updatedAt());
        return entity;
    }

    static WebsiteSettings toDomain(WebsiteSettingsJpaEntity entity) {
        return WebsiteSettings.rehydrate(entity.getId(), entity.getSellerName(), entity.getContactEmail(),
                entity.getContactPhone(), entity.getContactAddress(), entity.getPrivacyPolicy(),
                entity.getTermsAndConditions(), entity.getShippingPolicy(), entity.getRefundPolicy(),
                entity.getUpdatedAt());
    }

    /**
     * Rebuilds {@link Money} from its stored parts.
     *
     * <p>The currency is looked up by code rather than assumed to be INR, so a future multi-currency
     * product is a data change rather than a code change, and a row with an unexpected currency fails
     * loudly at load instead of being silently relabelled.
     */
    private static Money money(long minorUnits, String currencyCode) {
        return Money.ofMinorUnits(minorUnits, Currency.getInstance(currencyCode));
    }
}
