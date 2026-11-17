package com.choco.fulfilment.application.support;

import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.fulfilment.domain.inventory.Ingredient;
import com.choco.fulfilment.domain.port.IngredientRepository;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** In-memory {@link IngredientRepository}: names unique case-insensitively, as the database enforces. */
public class InMemoryIngredientRepository implements IngredientRepository {

    private final Map<UUID, Ingredient> ingredients = new LinkedHashMap<>();

    @Override
    public Ingredient save(Ingredient ingredient) {
        ingredients.put(ingredient.id(), ingredient);
        return ingredient;
    }

    @Override
    public Optional<Ingredient> findById(UUID id) {
        return Optional.ofNullable(ingredients.get(id));
    }

    @Override
    public PageResult<Ingredient> search(boolean includeInactive, String search, PageQuery page) {
        List<Ingredient> matches = ingredients.values().stream()
                .filter(i -> includeInactive || i.active())
                .filter(i -> search == null || search.isBlank()
                        || i.name().toLowerCase().contains(search.trim().toLowerCase()))
                .sorted(Comparator.comparing(Ingredient::name))
                .toList();
        return InMemoryPage.of(matches, page);
    }

    @Override
    public List<Ingredient> findLowStock() {
        return ingredients.values().stream()
                .filter(Ingredient::active)
                .filter(Ingredient::isBelowMinimum)
                .sorted(Comparator.comparing(Ingredient::name))
                .toList();
    }

    @Override
    public long count() {
        return ingredients.size();
    }

    @Override
    public boolean existsByNameIgnoreCase(String name) {
        return ingredients.values().stream().anyMatch(i -> i.name().equalsIgnoreCase(name));
    }
}
