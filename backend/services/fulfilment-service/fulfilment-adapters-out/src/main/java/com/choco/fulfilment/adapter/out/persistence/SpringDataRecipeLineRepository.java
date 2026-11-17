package com.choco.fulfilment.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataRecipeLineRepository extends JpaRepository<RecipeLineJpaEntity, UUID> {

    List<RecipeLineJpaEntity> findByVariantIdOrderByIngredientIdAsc(UUID variantId);

    boolean existsByVariantId(UUID variantId);

    void deleteByVariantId(UUID variantId);
}
