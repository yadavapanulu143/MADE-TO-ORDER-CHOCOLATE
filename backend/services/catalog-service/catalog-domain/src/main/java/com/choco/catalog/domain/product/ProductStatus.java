package com.choco.catalog.domain.product;

/**
 * Publication state of a product.
 *
 * <p>Three states rather than a boolean {@code active} flag, because "not yet shown" and "no longer
 * shown" are different facts. A draft is work in progress the seller is still preparing; an archived
 * product was once on sale and may still be referenced by historical orders. Collapsing both into
 * {@code active = false} would lose the distinction an administrator needs when deciding whether a
 * product can be deleted or only hidden.
 */
public enum ProductStatus {

    /** Visible only to administrators. The default for a new product, so nothing is published by accident. */
    DRAFT,

    /** Visible in the customer catalogue and orderable. */
    PUBLISHED,

    /** Withdrawn from sale. Kept for history; never returned to the customer catalogue. */
    ARCHIVED
}
