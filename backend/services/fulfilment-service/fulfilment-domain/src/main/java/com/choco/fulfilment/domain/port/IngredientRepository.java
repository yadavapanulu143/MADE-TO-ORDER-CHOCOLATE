package com.choco.fulfilment.domain.port;

import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.fulfilment.domain.inventory.Ingredient;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for ingredients. */
public interface IngredientRepository {

    Ingredient save(Ingredient ingredient);

    Optional<Ingredient> findById(UUID id);

    /** A page of ingredients, optionally including retired ones, for the seller's stock list. */
    PageResult<Ingredient> search(boolean includeInactive, String search, PageQuery page);

    /** Every active ingredient whose level is at or below its minimum, for the alerts view. */
    java.util.List<Ingredient> findLowStock();

    long count();

    boolean existsByNameIgnoreCase(String name);
}
