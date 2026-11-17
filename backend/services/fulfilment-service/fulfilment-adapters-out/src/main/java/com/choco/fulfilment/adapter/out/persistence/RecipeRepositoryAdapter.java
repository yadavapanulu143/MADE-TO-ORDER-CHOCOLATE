package com.choco.fulfilment.adapter.out.persistence;

import com.choco.fulfilment.domain.inventory.RecipeLine;
import com.choco.fulfilment.domain.port.RecipeRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of {@link RecipeRepository}.
 *
 * <p>Replacement is delete-then-insert rather than a diff: a recipe is a small set that is edited as a
 * whole, and the database's unique constraint on (variant, ingredient) would otherwise turn a reorder
 * into a conflict.
 */
@Repository
class RecipeRepositoryAdapter implements RecipeRepository {

    private final SpringDataRecipeLineRepository delegate;

    RecipeRepositoryAdapter(SpringDataRecipeLineRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecipeLine> findByVariantId(UUID variantId) {
        return delegate.findByVariantIdOrderByIngredientIdAsc(variantId).stream()
                .map(FulfilmentPersistenceMapper::toDomain).toList();
    }

    @Override
    public void replaceForVariant(UUID variantId, List<RecipeLine> lines) {
        delegate.deleteByVariantId(variantId);
        delegate.flush();
        delegate.saveAll(lines.stream().map(FulfilmentPersistenceMapper::toEntity).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsForVariant(UUID variantId) {
        return delegate.existsByVariantId(variantId);
    }
}
