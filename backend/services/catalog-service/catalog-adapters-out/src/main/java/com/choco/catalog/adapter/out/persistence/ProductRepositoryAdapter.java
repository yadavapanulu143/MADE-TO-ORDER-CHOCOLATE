package com.choco.catalog.adapter.out.persistence;

import com.choco.catalog.domain.port.ProductRepository;
import com.choco.catalog.domain.product.Product;
import com.choco.catalog.domain.product.ProductStatus;
import com.choco.catalog.domain.product.Slug;
import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of {@link ProductRepository}.
 *
 * <p>Each save re-saves the whole aggregate, children included, because the child collections are
 * cascade-all with orphan removal. That is what makes an edit that removes a variant actually delete
 * the row rather than leave it behind.
 *
 * <p>Sorting is applied through Spring Data rather than by hand, and only on the columns the caller is
 * allowed to sort by. An arbitrary sort field from a request would otherwise reach the query as a
 * property name and fail at runtime — or, worse, on some setups become an injection point.
 *
 * <p>Every read method is {@code @Transactional(readOnly = true)}. The child collections are lazy, and
 * the mapper touches them to build the aggregate; without a transaction the session is already closed
 * by the time the mapping runs, and the read fails with a lazy-initialisation error. Opening the
 * transaction here — inside the adapter, where the mapping actually happens — keeps the session alive
 * for exactly as long as it is needed, and avoids the alternative of open-session-in-view, which would
 * hold a database connection for the whole request including view rendering.
 */
@Repository
class ProductRepositoryAdapter implements ProductRepository {

    /** Columns a caller may sort the catalogue by, mapped to the persisted property names. */
    private static final List<String> SORTABLE = List.of("name", "createdAt", "updatedAt", "status");

    private final SpringDataProductRepository delegate;

    ProductRepositoryAdapter(SpringDataProductRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Product save(Product product) {
        ProductJpaEntity entity = CatalogPersistenceMapper.toEntity(product);
        // The aggregate carries no version, so the mapped entity starts at zero. Carrying the current
        // version across is what makes optimistic locking work: the update then carries "where version
        // = N" and a concurrent edit fails the check instead of silently overwriting the other change.
        // Without this, the second edit of any product sends version zero against a row already at
        // one and every subsequent write fails as a stale-object error.
        delegate.findVersionById(product.id()).ifPresent(entity::setVersion);
        delegate.save(entity);
        return product;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> findById(UUID id) {
        return delegate.findById(id).map(CatalogPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> findVisibleBySlug(Slug slug) {
        return delegate.findBySlugAndStatus(slug.value(), ProductStatus.PUBLISHED.name())
                .map(CatalogPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> findBySlug(Slug slug) {
        return delegate.findBySlug(slug.value()).map(CatalogPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsBySlug(Slug slug) {
        return delegate.existsBySlug(slug.value());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Product> findByStatuses(Collection<ProductStatus> statuses, String search, PageQuery page) {
        List<String> names = statuses.stream().map(Enum::name).toList();
        Page<ProductJpaEntity> result = delegate.searchByStatuses(names, likePattern(search), toPageable(page));
        return toPageResult(result, page);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Product> findPublished(String search, PageQuery page) {
        Page<ProductJpaEntity> result = delegate.searchPublished(likePattern(search), toPageable(page));
        return toPageResult(result, page);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> findPublishedByVariantIds(Collection<UUID> variantIds) {
        if (variantIds == null || variantIds.isEmpty()) {
            return List.of();
        }
        return delegate.findPublishedByVariantIds(variantIds).stream()
                .map(CatalogPersistenceMapper::toDomain)
                .toList();
    }

    /**
     * Builds a case-insensitive contains-pattern for the search term.
     *
     * <p>The LIKE wildcards inside the user's own term are escaped with {@code !} — the escape
     * character declared on the queries — so a term that happens to contain {@code %} or {@code _} is
     * matched literally instead of turning into a wildcard. An empty or absent term becomes {@code %}
     * and matches everything, which is how "no filter" is expressed without a nullable parameter.
     */
    private static String likePattern(String search) {
        if (search == null || search.isBlank()) {
            return "%";
        }
        String escaped = search.trim().toLowerCase(java.util.Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }

    @Override
    @Transactional(readOnly = true)
    public long countByStatus(ProductStatus status) {
        return delegate.countByStatus(status.name());
    }

    private static PageResult<Product> toPageResult(Page<ProductJpaEntity> result, PageQuery query) {
        List<Product> products = result.getContent().stream().map(CatalogPersistenceMapper::toDomain).toList();
        return PageResult.of(products, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    private static PageRequest toPageable(PageQuery query) {
        String sortField = query.sort() == null || !SORTABLE.contains(query.sort()) ? "createdAt" : query.sort();
        Sort.Direction direction = query.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(query.page(), query.size(), Sort.by(direction, sortField));
    }
}
