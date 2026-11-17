package com.choco.catalog.application.service;

import com.choco.catalog.application.dto.ProductSummaryView;
import com.choco.catalog.application.dto.ProductView;
import com.choco.catalog.domain.product.Product;
import com.choco.catalog.domain.product.ProductStatus;
import com.choco.catalog.domain.product.Slug;
import com.choco.catalog.domain.port.ProductRepository;
import com.choco.core.error.ErrorCode;
import com.choco.core.error.NotFoundException;
import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import java.util.EnumSet;
import java.util.UUID;

/**
 * The read side of the catalogue.
 *
 * <p>Separated from {@link ProductCatalogService} because reads and writes have different callers and
 * different risks. The storefront calls the read side anonymously and must only ever see published
 * products; the dashboard calls both. Keeping the two apart means the visibility rule for the
 * storefront is written once, in {@link #visibleBySlug}, instead of being re-checked at every call
 * site where it could be forgotten.
 *
 * <p>No caching is applied here. The catalogue is small and changes rarely, so a cache would add an
 * invalidation problem for little gain; if it becomes a bottleneck, a short-TTL read cache can be
 * introduced behind this class without any caller noticing.
 */
public class ProductQueryService {

    private final ProductRepository productRepository;

    public ProductQueryService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * A published product by its public address.
     *
     * <p>Reports "not found" for a draft or archived product rather than "forbidden": a customer must
     * not be able to tell a product that does not exist from one that exists but is not on sale.
     */
    public ProductView visibleBySlug(String slug) {
        Product product = productRepository.findVisibleBySlug(Slug.of(slug))
                .filter(Product::isVisibleToCustomers)
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "Product not found"));
        return ProductViewMapper.toView(product);
    }

    /** The storefront listing: published products only, optionally filtered by a search term. */
    public PageResult<ProductSummaryView> listVisible(String search, PageQuery page) {
        return productRepository.findPublished(normaliseSearch(search), page).map(ProductViewMapper::toSummary);
    }

    /** A product by id, in any status, for the dashboard. */
    public ProductView forAdmin(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "Product not found"));
        return ProductViewMapper.toView(product);
    }

    /**
     * The dashboard listing.
     *
     * <p>When no status filter is supplied every status is searched, so an administrator sees drafts
     * and archived products as well as live ones; the default of "only published" would hide exactly
     * the products an administrator most often needs to work on.
     */
    public PageResult<ProductSummaryView> listForAdmin(java.util.Collection<String> statuses, String search,
                                                       PageQuery page) {
        var resolved = resolveStatuses(statuses);
        return productRepository.findByStatuses(resolved, normaliseSearch(search), page)
                .map(ProductViewMapper::toSummary);
    }

    public long countByStatus(String status) {
        return productRepository.countByStatus(ProductStatus.valueOf(status.trim().toUpperCase(java.util.Locale.ROOT)));
    }

    private static java.util.Collection<ProductStatus> resolveStatuses(java.util.Collection<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return EnumSet.allOf(ProductStatus.class);
        }
        EnumSet<ProductStatus> resolved = EnumSet.noneOf(ProductStatus.class);
        for (String status : statuses) {
            if (status == null || status.isBlank()) {
                continue;
            }
            resolved.add(ProductStatus.valueOf(status.trim().toUpperCase(java.util.Locale.ROOT)));
        }
        return resolved.isEmpty() ? EnumSet.allOf(ProductStatus.class) : resolved;
    }

    /** Blank and absent are treated identically, so a cleared search box does not filter on "null". */
    private static String normaliseSearch(String search) {
        return search == null || search.isBlank() ? null : search.trim();
    }
}
