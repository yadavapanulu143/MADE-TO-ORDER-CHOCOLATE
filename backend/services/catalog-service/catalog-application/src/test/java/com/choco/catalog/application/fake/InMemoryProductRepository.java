package com.choco.catalog.application.fake;

import com.choco.catalog.domain.port.ProductRepository;
import com.choco.catalog.domain.product.Product;
import com.choco.catalog.domain.product.ProductStatus;
import com.choco.catalog.domain.product.Slug;
import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory {@link ProductRepository} for use-case tests.
 *
 * <p>A hand-written fake rather than a Mockito stub: it keeps real behaviour — a store keyed by id, a
 * real slug uniqueness check, real filtering and paging — so a test asserts on the outcome of a use
 * case instead of on the calls it made. That is what lets the tests survive a refactor of the service
 * internals.
 *
 * <p>It deliberately mimics the visibility rule of the real adapter: {@code findVisibleBySlug} returns
 * only a published product, while {@code findById} returns anything. A test that proves a draft is not
 * exposed through the customer read is therefore testing the rule, not the fake.
 */
public class InMemoryProductRepository implements ProductRepository {

    private final Map<UUID, Product> store = new LinkedHashMap<>();

    @Override
    public Product save(Product product) {
        store.put(product.id(), product);
        return product;
    }

    @Override
    public Optional<Product> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Product> findVisibleBySlug(Slug slug) {
        return store.values().stream()
                .filter(product -> product.slug().equals(slug))
                .filter(product -> product.status() == ProductStatus.PUBLISHED)
                .findFirst();
    }

    @Override
    public Optional<Product> findBySlug(Slug slug) {
        return store.values().stream().filter(product -> product.slug().equals(slug)).findFirst();
    }

    @Override
    public boolean existsBySlug(Slug slug) {
        return findBySlug(slug).isPresent();
    }

    @Override
    public PageResult<Product> findByStatuses(Collection<ProductStatus> statuses, String search, PageQuery page) {
        List<Product> matching = store.values().stream()
                .filter(product -> statuses.contains(product.status()))
                .filter(product -> matchesSearch(product, search))
                .toList();
        return page(matching, page);
    }

    @Override
    public PageResult<Product> findPublished(String search, PageQuery page) {
        List<Product> matching = store.values().stream()
                .filter(product -> product.status() == ProductStatus.PUBLISHED)
                .filter(product -> matchesSearch(product, search))
                .toList();
        return page(matching, page);
    }

    @Override
    public long countByStatus(ProductStatus status) {
        return store.values().stream().filter(product -> product.status() == status).count();
    }

    private static boolean matchesSearch(Product product, String search) {
        return search == null || product.name().toLowerCase().contains(search.toLowerCase());
    }

    private static PageResult<Product> page(List<Product> all, PageQuery query) {
        int from = Math.min(query.offset(), all.size());
        int to = Math.min(from + query.size(), all.size());
        return PageResult.of(new ArrayList<>(all.subList(from, to)), query.page(), query.size(), all.size());
    }
}
