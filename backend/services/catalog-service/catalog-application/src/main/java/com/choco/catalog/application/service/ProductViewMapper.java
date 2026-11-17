package com.choco.catalog.application.service;

import com.choco.catalog.application.dto.AttributeView;
import com.choco.catalog.application.dto.ImageView;
import com.choco.catalog.application.dto.ProductSummaryView;
import com.choco.catalog.application.dto.ProductView;
import com.choco.catalog.application.dto.SettingsView;
import com.choco.catalog.application.dto.VariantView;
import com.choco.catalog.domain.product.Product;
import com.choco.catalog.domain.product.ProductAttribute;
import com.choco.catalog.domain.product.ProductImage;
import com.choco.catalog.domain.product.ProductVariant;
import com.choco.catalog.domain.settings.WebsiteSettings;
import com.choco.core.domain.Money;
import java.util.List;
import java.util.Optional;

/**
 * Turns aggregates into the views the API returns.
 *
 * <p>Kept in one place so a change to how a price is formatted, or to how an image URL is built, is a
 * single edit rather than a hunt through every use case. It also keeps the money-to-string conversion
 * out of the domain: the domain deals in {@link Money}, and only the boundary decides that a price is
 * rendered as a fixed-scale decimal string.
 *
 * <p>Price is exposed twice. {@code priceMinorUnits} is the exact value another service should use for
 * arithmetic; the decimal string is for display. The decimal is a string rather than a JSON number so
 * a JavaScript client cannot silently turn it into a binary floating-point value.
 */
final class ProductViewMapper {

    /** Base path under which stored images are served by the image controller. */
    static final String IMAGE_PATH_PREFIX = "/api/v1/catalog/images/";

    private ProductViewMapper() {
    }

    static ProductView toView(Product product) {
        Optional<Money> fromPrice = product.fromPrice();
        return new ProductView(
                product.id(),
                product.slug().value(),
                product.name(),
                product.shortDescription(),
                product.description(),
                product.preparationInfo(),
                product.deliveryInfo(),
                product.status().name(),
                fromPrice.map(Money::minorUnits).orElse(0L),
                fromPrice.map(ProductViewMapper::decimal).orElse(null),
                fromPrice.map(money -> money.currency().getCurrencyCode()).orElse(Money.INR.getCurrencyCode()),
                product.isOrderable(),
                product.variants().stream().map(ProductViewMapper::toView).toList(),
                product.images().stream().map(ProductViewMapper::toView).toList(),
                product.attributes().stream().map(ProductViewMapper::toView).toList(),
                product.createdAt(),
                product.updatedAt());
    }

    static ProductSummaryView toSummary(Product product) {
        Optional<Money> fromPrice = product.fromPrice();
        return new ProductSummaryView(
                product.id(),
                product.slug().value(),
                product.name(),
                product.shortDescription(),
                fromPrice.map(Money::minorUnits).orElse(0L),
                fromPrice.map(ProductViewMapper::decimal).orElse(null),
                fromPrice.map(money -> money.currency().getCurrencyCode()).orElse(Money.INR.getCurrencyCode()),
                product.primaryImage().map(image -> imageUrl(image.storageKey())).orElse(null),
                product.variants().size(),
                product.status().name(),
                product.isOrderable());
    }

    static VariantView toView(ProductVariant variant) {
        Money price = variant.price();
        return new VariantView(
                variant.id(),
                variant.label(),
                variant.sku(),
                price.minorUnits(),
                decimal(price),
                price.currency().getCurrencyCode(),
                variant.minOrderQuantity(),
                variant.maxOrderQuantity(),
                variant.isActive());
    }

    static ImageView toView(ProductImage image) {
        return new ImageView(
                image.id(),
                imageUrl(image.storageKey()),
                image.altText(),
                image.displayOrder(),
                image.isPrimary());
    }

    static AttributeView toView(ProductAttribute attribute) {
        return new AttributeView(attribute.id(), attribute.name(), attribute.value(), attribute.displayOrder());
    }

    static SettingsView toView(WebsiteSettings settings) {
        return new SettingsView(
                settings.sellerName(),
                settings.contactEmail(),
                settings.contactPhone(),
                settings.contactAddress(),
                settings.privacyPolicy(),
                settings.termsAndConditions(),
                settings.shippingPolicy(),
                settings.refundPolicy(),
                settings.updatedAt());
    }

    static List<ProductSummaryView> toSummaries(List<Product> products) {
        return products.stream().map(ProductViewMapper::toSummary).toList();
    }

    /** Public path for a stored image, derived from its key. The key itself is never exposed. */
    static String imageUrl(String storageKey) {
        return IMAGE_PATH_PREFIX + storageKey;
    }

    private static String decimal(Money money) {
        return money.toMajorUnits().toPlainString();
    }
}
