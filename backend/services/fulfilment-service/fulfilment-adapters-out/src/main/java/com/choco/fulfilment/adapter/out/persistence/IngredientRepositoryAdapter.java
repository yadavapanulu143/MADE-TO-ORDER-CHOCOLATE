package com.choco.fulfilment.adapter.out.persistence;

import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.fulfilment.domain.inventory.Ingredient;
import com.choco.fulfilment.domain.port.IngredientRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JPA implementation of {@link IngredientRepository}. */
@Repository
class IngredientRepositoryAdapter implements IngredientRepository {

    private static final List<String> SORTABLE = List.of("name", "createdAt", "updatedAt");

    private final SpringDataIngredientRepository delegate;

    IngredientRepositoryAdapter(SpringDataIngredientRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Ingredient save(Ingredient ingredient) {
        IngredientJpaEntity entity = FulfilmentPersistenceMapper.toEntity(ingredient);
        delegate.findVersionById(ingredient.id()).ifPresent(entity::setVersion);
        delegate.save(entity);
        return ingredient;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Ingredient> findById(UUID id) {
        return delegate.findById(id).map(FulfilmentPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Ingredient> search(boolean includeInactive, String search, PageQuery page) {
        Page<IngredientJpaEntity> result = delegate.search(includeInactive, likePattern(search),
                toPageable(page));
        List<Ingredient> ingredients = result.getContent().stream()
                .map(FulfilmentPersistenceMapper::toDomain).toList();
        return PageResult.of(ingredients, result.getNumber(), result.getSize(),
                result.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Ingredient> findLowStock() {
        return delegate.findLowStock().stream().map(FulfilmentPersistenceMapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return delegate.count();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByNameIgnoreCase(String name) {
        return delegate.existsByNameIgnoreCase(name);
    }

    private static String likePattern(String search) {
        if (search == null || search.isBlank()) {
            return "%";
        }
        String escaped = search.trim().toLowerCase(java.util.Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }

    private static PageRequest toPageable(PageQuery query) {
        String sortField = query.sort() == null || !SORTABLE.contains(query.sort()) ? "name"
                : query.sort();
        Sort.Direction direction = query.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(query.page(), query.size(), Sort.by(direction, sortField));
    }
}
