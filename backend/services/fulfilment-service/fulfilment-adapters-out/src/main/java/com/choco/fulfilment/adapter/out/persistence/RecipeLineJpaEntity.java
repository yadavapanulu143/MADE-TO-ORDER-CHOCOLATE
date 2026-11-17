package com.choco.fulfilment.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Persistence model for one recipe line: how much of an ingredient one unit of a variant consumes. */
@Entity
@Table(name = "recipe_lines")
public class RecipeLineJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "variant_id", nullable = false, updatable = false)
    private UUID variantId;

    @Column(name = "ingredient_id", nullable = false, updatable = false)
    private UUID ingredientId;

    @Column(name = "per_unit_milli_units", nullable = false)
    private long perUnitMilliUnits;

    protected RecipeLineJpaEntity() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getVariantId() { return variantId; }
    public void setVariantId(UUID variantId) { this.variantId = variantId; }
    public UUID getIngredientId() { return ingredientId; }
    public void setIngredientId(UUID ingredientId) { this.ingredientId = ingredientId; }
    public long getPerUnitMilliUnits() { return perUnitMilliUnits; }
    public void setPerUnitMilliUnits(long v) { this.perUnitMilliUnits = v; }
}
