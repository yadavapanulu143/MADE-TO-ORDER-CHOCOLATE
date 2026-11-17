package com.choco.fulfilment.application.support;

import com.choco.fulfilment.domain.inventory.RecipeLine;
import com.choco.fulfilment.domain.port.RecipeRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** In-memory {@link RecipeRepository}: a variant's recipe replaced as a whole, as the database does. */
public class InMemoryRecipeRepository implements RecipeRepository {

    private final Map<UUID, List<RecipeLine>> recipes = new LinkedHashMap<>();

    @Override
    public List<RecipeLine> findByVariantId(UUID variantId) {
        return recipes.getOrDefault(variantId, List.of()).stream()
                .sorted(Comparator.comparing(RecipeLine::ingredientId))
                .toList();
    }

    @Override
    public void replaceForVariant(UUID variantId, List<RecipeLine> lines) {
        recipes.put(variantId, new ArrayList<>(lines));
    }

    @Override
    public boolean existsForVariant(UUID variantId) {
        return !recipes.getOrDefault(variantId, List.of()).isEmpty();
    }
}
