package com.choco.catalog.domain.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.domain.Money;
import com.choco.core.error.BusinessRuleException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests the product aggregate.
 *
 * <p>The emphasis is on the invariants a made-to-order catalogue must hold: a product is created as a
 * draft and is not orderable until it has a price and is published, a variant's price can never be
 * zero or negative, and the customer-facing visibility rule never exposes a draft.
 */
@DisplayName("Product aggregate")
class ProductTest {

    private static final Instant NOW = Instant.parse("2026-06-01T09:00:00Z");
    private static final Instant LATER = Instant.parse("2026-06-02T09:00:00Z");

    private static Product draft() {
        return Product.create(UUID.randomUUID(), "Dark Chocolate Bar", "70% cocoa", "Rich and dark",
                "Made fresh to order; ready in 2 days", "Delivered within 3 days", NOW);
    }

    private static ProductVariant variant(Product product, Money price) {
        // A unique SKU per call: the aggregate refuses two variants with the same SKU, and this helper
        // is used to build multi-variant products.
        return product.addVariant(UUID.randomUUID(), "200 g", "SKU-" + UUID.randomUUID(), price, 1, 10, NOW);
    }

    @Nested
    @DisplayName("creation")
    class Creation {

        @Test
        void a_new_product_is_a_draft_and_not_orderable() {
            Product product = draft();

            assertThat(product.status()).isEqualTo(ProductStatus.DRAFT);
            assertThat(product.isOrderable()).isFalse();
            assertThat(product.isVisibleToCustomers()).isFalse();
        }

        @Test
        void the_slug_is_derived_from_the_name() {
            Product product = draft();

            assertThat(product.slug().value()).isEqualTo("dark-chocolate-bar");
        }

        @Test
        void a_blank_name_is_rejected() {
            assertThatThrownBy(() -> Product.create(UUID.randomUUID(), "   ", null, null, null, null, NOW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("name");
        }

        @Test
        void a_creation_event_is_recorded() {
            Product product = draft();

            assertThat(product.pullDomainEvents()).hasSize(1);
            assertThat(product.peekDomainEvents()).isEmpty();
        }
    }

    @Nested
    @DisplayName("variants")
    class Variants {

        @Test
        void a_zero_price_variant_is_rejected() {
            Product product = draft();

            assertThatThrownBy(() -> product.addVariant(UUID.randomUUID(), "200 g", null,
                    Money.inr(0), 1, 10, NOW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("greater than zero");
        }

        @Test
        void a_minimum_greater_than_the_maximum_is_rejected() {
            Product product = draft();

            assertThatThrownBy(() -> product.addVariant(UUID.randomUUID(), "200 g", null,
                    Money.inr(50000), 10, 5, NOW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not be less than the minimum");
        }

        @Test
        void changing_a_price_records_a_price_event() {
            Product product = draft();
            ProductVariant created = variant(product, Money.inr(50000));
            product.pullDomainEvents();

            product.updateVariant(created.id(), "200 g", created.sku(), Money.inr(60000), 1, 10, LATER);

            assertThat(product.pullDomainEvents())
                    .singleElement()
                    .satisfies(event -> assertThat(event.eventType()).isEqualTo("catalog.variant-price-changed"));
        }

        @Test
        void the_from_price_is_the_cheapest_active_variant() {
            Product product = draft();
            variant(product, Money.inr(60000));
            variant(product, Money.inr(45000));

            assertThat(product.fromPrice()).contains(Money.inr(45000));
        }
    }

    @Nested
    @DisplayName("images")
    class Images {

        @Test
        void the_first_image_becomes_primary() {
            Product product = draft();

            ProductImage image = product.addImage(UUID.randomUUID(), "p/1.jpg", "image/jpeg", 1024,
                    "Bar in wrapper", NOW);

            assertThat(image.isPrimary()).isTrue();
            assertThat(product.primaryImage()).contains(image);
        }

        @Test
        void a_non_image_content_type_is_rejected() {
            Product product = draft();

            assertThatThrownBy(() -> product.addImage(UUID.randomUUID(), "p/1.html", "text/html", 10,
                    null, NOW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported image type");
        }

        @Test
        void making_another_image_primary_clears_the_first() {
            Product product = draft();
            ProductImage first = product.addImage(UUID.randomUUID(), "p/1.jpg", "image/jpeg", 1024, null, NOW);
            ProductImage second = product.addImage(UUID.randomUUID(), "p/2.jpg", "image/jpeg", 2048, null, NOW);

            product.makeImagePrimary(second.id(), LATER);

            assertThat(product.primaryImage()).contains(second);
            assertThat(product.images()).filteredOn(ProductImage::isPrimary).containsExactly(second);
            assertThat(first.isPrimary()).isFalse();
        }

        @Test
        void removing_a_missing_image_is_not_found() {
            Product product = draft();

            assertThatThrownBy(() -> product.removeImage(UUID.randomUUID(), LATER))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }

    @Nested
    @DisplayName("publishing and visibility")
    class Publishing {

        @Test
        void a_product_without_a_variant_cannot_be_published() {
            Product product = draft();

            assertThatThrownBy(() -> product.publish(LATER))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("variant");
        }

        @Test
        void a_published_product_with_an_active_variant_is_orderable() {
            Product product = draft();
            variant(product, Money.inr(50000));

            product.publish(LATER);

            assertThat(product.status()).isEqualTo(ProductStatus.PUBLISHED);
            assertThat(product.isOrderable()).isTrue();
            assertThat(product.isVisibleToCustomers()).isTrue();
        }

        @Test
        void an_unpublished_product_is_not_visible_to_customers() {
            Product product = draft();
            variant(product, Money.inr(50000));
            product.publish(NOW);

            product.unpublish(LATER);

            assertThat(product.isVisibleToCustomers()).isFalse();
        }

        @Test
        void an_archived_product_cannot_be_published_again() {
            Product product = draft();
            variant(product, Money.inr(50000));
            product.archive(NOW);

            assertThatThrownBy(() -> product.publish(LATER))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }

    @Nested
    @DisplayName("order quantity validation")
    class OrderQuantity {

        @Test
        void a_quantity_below_the_minimum_is_refused() {
            Product product = draft();
            ProductVariant variant = product.addVariant(UUID.randomUUID(), "500 g", null, Money.inr(90000), 2, 5, NOW);
            product.publish(NOW);

            assertThatThrownBy(() -> product.assertOrderable(variant.id(), 1))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("minimum order");
        }

        @Test
        void a_quantity_above_the_maximum_is_refused() {
            Product product = draft();
            ProductVariant variant = product.addVariant(UUID.randomUUID(), "500 g", null, Money.inr(90000), 1, 5, NOW);
            product.publish(NOW);

            assertThatThrownBy(() -> product.assertOrderable(variant.id(), 6))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("maximum order");
        }

        @Test
        void an_orderable_quantity_is_accepted() {
            Product product = draft();
            ProductVariant variant = variant(product, Money.inr(50000));
            product.publish(NOW);

            product.assertOrderable(variant.id(), 3);

            assertThat(product.variant(variant.id())).isPresent();
        }
    }
}
