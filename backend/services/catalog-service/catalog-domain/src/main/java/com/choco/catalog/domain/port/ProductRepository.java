package com.choco.catalog.domain.port;

import com.choco.catalog.domain.product.Product;
import com.choco.catalog.domain.product.ProductStatus;
import com.choco.catalog.domain.product.Slug;
import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for product persistence.
 *
 * <p>The domain depends on this interface, never on JPA, so storage stays an implementation detail
 * and the aggregate can be exercised without a database.
 *
 * <p>Lookups are separated by visibility on purpose. {@code findVisibleBySlug} is the customer-facing
 * read and only ever returns a published product; {@code findById} is the administrative read and
 * returns anything. Having one method serve both would mean a customer request could be answered with
 * a draft product if a caller forgot to filter, and that omission is invisible in review.
 */
public interface ProductRepository {

    Product save(Product product);

    /** Administrative read: any status. */
    Optional<Product> findById(UUID id);

    /** Customer read: only a published product is returned. */
    Optional<Product> findVisibleBySlug(Slug slug);

    /** Administrative read by slug, for uniqueness checks across every status. */
    Optional<Product> findBySlug(Slug slug);

    boolean existsBySlug(Slug slug);

    /** A page of products in any of the given statuses, for the administrative list. */
    PageResult<Product> findByStatuses(java.util.Collection<ProductStatus> statuses, String search, PageQuery page);

    /** A page of published products matching a search term, for the storefront. */
    PageResult<Product> findPublished(String search, PageQuery page);

    /**
     * The published products that own any of the given variants.
     *
     * <p>Lets a caller resolve a set of variant ids to their current price and availability in one
     * call, without knowing the products' slugs. Only published products are returned, so a variant of
     * a withdrawn product resolves to nothing and the caller treats it as unavailable rather than
     * pricing it from stale data.
     */
    java.util.List<Product> findPublishedByVariantIds(java.util.Collection<UUID> variantIds);

    long countByStatus(ProductStatus status);
}
