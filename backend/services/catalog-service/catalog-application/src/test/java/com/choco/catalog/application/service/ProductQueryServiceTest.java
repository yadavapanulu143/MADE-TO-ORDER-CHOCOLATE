package com.choco.catalog.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.catalog.application.command.AddVariantCommand;
import com.choco.catalog.application.command.CreateProductCommand;
import com.choco.catalog.application.fake.InMemoryImageStorage;
import com.choco.catalog.application.fake.InMemoryProductRepository;
import com.choco.catalog.application.port.AuditLogPort;
import com.choco.core.error.NotFoundException;
import com.choco.core.port.PageQuery;
import com.choco.test.Fakes;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests the read side, with the emphasis on visibility.
 *
 * <p>The security-relevant rule is that the storefront read never returns a product the seller has not
 * published. That is asserted directly here, because a service that ignored the rule would pass every
 * other test in the class.
 */
@DisplayName("ProductQueryService")
class ProductQueryServiceTest {

    private InMemoryProductRepository products;
    private ProductCatalogService catalogService;
    private ProductQueryService queryService;
    private final UUID admin = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        products = new InMemoryProductRepository();
        catalogService = new ProductCatalogService(products, new Fakes.RecordingEventPublisher(),
                new InMemoryImageStorage(), noopAudit(), new Fakes.DirectTransactionRunner(),
                new Fakes.SequentialIdGenerator(), Fakes.FixedClock.startingAt("2026-06-01T09:00:00Z"));
        queryService = new ProductQueryService(products);
    }

    private static AuditLogPort noopAudit() {
        return (actorType, actorId, action, targetType, targetId, outcome, detail, meta) -> {
        };
    }

    private UUID publishedProduct(String name) {
        UUID id = catalogService.create(admin, new CreateProductCommand(name, "70% cocoa", null, null, null)).id();
        catalogService.addVariant(admin, id,
                new AddVariantCommand("200 g", "SKU-" + UUID.randomUUID(), new BigDecimal("450.00"), 1, 10));
        catalogService.publish(admin, id);
        return id;
    }

    @Nested
    @DisplayName("customer reads")
    class CustomerReads {

        @Test
        void a_published_product_is_returned_by_slug() {
            publishedProduct("Dark Chocolate Bar");

            assertThat(queryService.visibleBySlug("dark-chocolate-bar").name())
                    .isEqualTo("Dark Chocolate Bar");
        }

        @Test
        void a_draft_product_is_not_visible_to_a_customer() {
            catalogService.create(admin, new CreateProductCommand("Secret Bar", null, null, null, null));

            assertThatThrownBy(() -> queryService.visibleBySlug("secret-bar"))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        void an_unpublished_product_is_not_visible_to_a_customer() {
            UUID id = publishedProduct("Dark Chocolate Bar");
            catalogService.unpublish(admin, id);

            assertThatThrownBy(() -> queryService.visibleBySlug("dark-chocolate-bar"))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        void the_storefront_listing_contains_only_published_products() {
            publishedProduct("Dark Chocolate Bar");
            catalogService.create(admin, new CreateProductCommand("Draft Bar", null, null, null, null));

            var page = queryService.listVisible(null, PageQuery.of(0, 20, null, null));

            assertThat(page.content()).hasSize(1);
            assertThat(page.content().getFirst().name()).isEqualTo("Dark Chocolate Bar");
        }

        @Test
        void the_storefront_listing_can_be_searched_by_name() {
            publishedProduct("Dark Chocolate Bar");
            publishedProduct("Milk Chocolate Bar");

            var page = queryService.listVisible("milk", PageQuery.of(0, 20, null, null));

            assertThat(page.content()).hasSize(1);
            assertThat(page.content().getFirst().name()).isEqualTo("Milk Chocolate Bar");
        }
    }

    @Nested
    @DisplayName("administrative reads")
    class AdminReads {

        @Test
        void the_dashboard_listing_includes_every_status_by_default() {
            publishedProduct("Dark Chocolate Bar");
            catalogService.create(admin, new CreateProductCommand("Draft Bar", null, null, null, null));

            var page = queryService.listForAdmin(List.of(), null, PageQuery.of(0, 20, null, null));

            assertThat(page.content()).hasSize(2);
        }

        @Test
        void the_dashboard_listing_can_be_filtered_by_status() {
            publishedProduct("Dark Chocolate Bar");
            catalogService.create(admin, new CreateProductCommand("Draft Bar", null, null, null, null));

            var page = queryService.listForAdmin(List.of("DRAFT"), null, PageQuery.of(0, 20, null, null));

            assertThat(page.content()).hasSize(1);
            assertThat(page.content().getFirst().status()).isEqualTo("DRAFT");
        }

        @Test
        void a_draft_product_is_visible_to_an_administrator() {
            UUID id = catalogService.create(admin,
                    new CreateProductCommand("Secret Bar", null, null, null, null)).id();

            assertThat(queryService.forAdmin(id).status()).isEqualTo("DRAFT");
        }

        @Test
        void counting_by_status_reports_the_number_in_that_status() {
            publishedProduct("Dark Chocolate Bar");
            publishedProduct("Milk Chocolate Bar");

            assertThat(queryService.countByStatus("PUBLISHED")).isEqualTo(2);
            assertThat(queryService.countByStatus("draft")).isZero();
        }
    }
}
