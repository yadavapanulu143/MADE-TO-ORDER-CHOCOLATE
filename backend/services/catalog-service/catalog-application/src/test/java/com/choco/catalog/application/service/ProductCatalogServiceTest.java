package com.choco.catalog.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.catalog.application.command.AddAttributeCommand;
import com.choco.catalog.application.command.AddVariantCommand;
import com.choco.catalog.application.command.CreateProductCommand;
import com.choco.catalog.application.command.UpdateProductCommand;
import com.choco.catalog.application.command.UpdateVariantCommand;
import com.choco.catalog.application.dto.ProductView;
import com.choco.catalog.application.fake.InMemoryImageStorage;
import com.choco.catalog.application.fake.InMemoryProductRepository;
import com.choco.catalog.application.port.AuditLogPort;
import com.choco.core.error.ConflictException;
import com.choco.core.error.NotFoundException;
import com.choco.core.error.ValidationException;
import com.choco.test.Fakes;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests the administrative catalogue use cases.
 *
 * <p>Each test drives the service through its public methods and asserts on the resulting view and on
 * what was published and audited — the outcomes a caller can observe — rather than on internal calls.
 * The audit assertion is deliberately explicit: a change that is applied but not audited would satisfy
 * every other test in this class while failing the requirement.
 */
@DisplayName("ProductCatalogService")
class ProductCatalogServiceTest {

    private InMemoryProductRepository products;
    private InMemoryImageStorage imageStorage;
    private Fakes.RecordingEventPublisher events;
    private List<String> auditActions;
    private ProductCatalogService service;
    private final UUID admin = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        products = new InMemoryProductRepository();
        imageStorage = new InMemoryImageStorage();
        events = new Fakes.RecordingEventPublisher();
        auditActions = new ArrayList<>();
        AuditLogPort auditLog = (actorType, actorId, action, targetType, targetId, outcome, detail, meta) ->
                auditActions.add(action);

        service = new ProductCatalogService(products, events, imageStorage, auditLog,
                new Fakes.DirectTransactionRunner(), new Fakes.SequentialIdGenerator(),
                Fakes.FixedClock.startingAt("2026-06-01T09:00:00Z"));
    }

    private CreateProductCommand createCommand(String name) {
        return new CreateProductCommand(name, "70% cocoa", "Rich and dark",
                "Made fresh to order", "Delivered in 3 days");
    }

    private AddVariantCommand variantCommand(String price) {
        return new AddVariantCommand("200 g", "SKU-" + UUID.randomUUID(), new BigDecimal(price), 1, 10);
    }

    @Nested
    @DisplayName("creating")
    class Creating {

        @Test
        void a_product_is_created_as_a_draft() {
            ProductView created = service.create(admin, createCommand("Dark Chocolate Bar"));

            assertThat(created.name()).isEqualTo("Dark Chocolate Bar");
            assertThat(created.status()).isEqualTo("DRAFT");
            assertThat(created.available()).isFalse();
            assertThat(created.slug()).isEqualTo("dark-chocolate-bar");
        }

        @Test
        void the_creation_is_audited() {
            service.create(admin, createCommand("Dark Chocolate Bar"));

            assertThat(auditActions).containsExactly("PRODUCT_CREATED");
        }

        @Test
        void a_duplicate_name_is_refused() {
            service.create(admin, createCommand("Dark Chocolate Bar"));

            assertThatThrownBy(() -> service.create(admin, createCommand("Dark Chocolate Bar")))
                    .isInstanceOf(ConflictException.class);
        }
    }

    @Nested
    @DisplayName("variants and pricing")
    class Variants {

        private UUID productId;

        @BeforeEach
        void createProduct() {
            productId = service.create(admin, createCommand("Dark Chocolate Bar")).id();
        }

        @Test
        void a_price_is_stored_as_exact_minor_units() {
            ProductView view = service.addVariant(admin, productId, variantCommand("450.50"));

            assertThat(view.variants()).singleElement().satisfies(variant -> {
                assertThat(variant.priceMinorUnits()).isEqualTo(45_050L);
                assertThat(variant.price()).isEqualTo("450.50");
                assertThat(variant.currency()).isEqualTo("INR");
            });
        }

        @Test
        void a_zero_price_is_refused() {
            assertThatThrownBy(() -> service.addVariant(admin, productId,
                    new AddVariantCommand("200 g", null, BigDecimal.ZERO, 1, 10)))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void changing_a_price_publishes_a_price_event() {
            ProductView created = service.addVariant(admin, productId, variantCommand("450.00"));
            UUID variantId = created.variants().getFirst().id();
            events.clear();

            service.updateVariant(admin, productId, variantId,
                    new UpdateVariantCommand("200 g", created.variants().getFirst().sku(),
                            new BigDecimal("500.00"), 1, 10));

            assertThat(events.eventsOfType("catalog.variant-price-changed")).hasSize(1);
        }

        @Test
        void a_variant_of_an_unknown_product_is_not_found() {
            assertThatThrownBy(() -> service.addVariant(admin, UUID.randomUUID(), variantCommand("100.00")))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    @DisplayName("images")
    class Images {

        private UUID productId;

        @BeforeEach
        void createProduct() {
            productId = service.create(admin, createCommand("Dark Chocolate Bar")).id();
        }

        @Test
        void an_uploaded_image_is_attached_with_a_generated_key() {
            ProductView view = service.uploadImage(admin, productId, "image/jpeg", 4, new byte[] {1, 2, 3, 4},
                    "Bar in wrapper");

            assertThat(view.images()).singleElement().satisfies(image -> {
                assertThat(image.url()).contains("/api/v1/catalog/images/");
                assertThat(image.altText()).isEqualTo("Bar in wrapper");
                assertThat(image.primary()).isTrue();
            });
            assertThat(imageStorage.size()).isEqualTo(1);
        }

        @Test
        void a_non_image_upload_is_refused_and_nothing_is_stored() {
            assertThatThrownBy(() -> service.uploadImage(admin, productId, "application/pdf", 4,
                    new byte[] {1, 2, 3, 4}, null))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThat(imageStorage.size()).isZero();
        }

        @Test
        void removing_an_image_deletes_the_stored_bytes() {
            ProductView created = service.uploadImage(admin, productId, "image/jpeg", 4,
                    new byte[] {1, 2, 3, 4}, null);
            UUID imageId = created.images().getFirst().id();
            assertThat(imageStorage.size()).isEqualTo(1);

            service.removeImage(admin, productId, imageId);

            assertThat(imageStorage.size()).isZero();
        }
    }

    @Nested
    @DisplayName("publishing")
    class Publishing {

        @Test
        void publishing_without_a_variant_is_refused() {
            UUID productId = service.create(admin, createCommand("Dark Chocolate Bar")).id();

            assertThatThrownBy(() -> service.publish(admin, productId))
                    .isInstanceOf(com.choco.core.error.BusinessRuleException.class);
        }

        @Test
        void publishing_with_a_variant_makes_the_product_orderable_and_audited() {
            UUID productId = service.create(admin, createCommand("Dark Chocolate Bar")).id();
            service.addVariant(admin, productId, variantCommand("450.00"));

            ProductView published = service.publish(admin, productId);

            assertThat(published.status()).isEqualTo("PUBLISHED");
            assertThat(published.available()).isTrue();
            assertThat(auditActions).contains("PRODUCT_PUBLISHED");
        }

        @Test
        void archiving_withdraws_the_product_without_deleting_it() {
            UUID productId = service.create(admin, createCommand("Dark Chocolate Bar")).id();
            service.addVariant(admin, productId, variantCommand("450.00"));
            service.publish(admin, productId);

            ProductView archived = service.archive(admin, productId);

            assertThat(archived.status()).isEqualTo("ARCHIVED");
            assertThat(products.findById(productId)).isPresent();
        }
    }

    @Nested
    @DisplayName("editing")
    class Editing {

        @Test
        void an_edit_updates_the_description_and_is_audited() {
            UUID productId = service.create(admin, createCommand("Dark Chocolate Bar")).id();

            ProductView updated = service.update(admin, productId,
                    new UpdateProductCommand("Dark Chocolate Bar", "75% cocoa", "Even richer",
                            "Made fresh", "Delivered in 3 days"));

            assertThat(updated.shortDescription()).isEqualTo("75% cocoa");
            assertThat(auditActions).contains("PRODUCT_UPDATED");
        }

        @Test
        void an_attribute_can_be_added_and_removed() {
            UUID productId = service.create(admin, createCommand("Dark Chocolate Bar")).id();

            ProductView withAttribute = service.addAttribute(admin, productId,
                    new AddAttributeCommand("Ingredients", "Cocoa, sugar, cocoa butter"));
            assertThat(withAttribute.attributes()).singleElement()
                    .satisfies(attribute -> assertThat(attribute.name()).isEqualTo("Ingredients"));

            UUID attributeId = withAttribute.attributes().getFirst().id();
            ProductView without = service.removeAttribute(admin, productId, attributeId);
            assertThat(without.attributes()).isEmpty();
        }
    }
}
