package com.choco.catalog.adapter.in.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Payload for adding or editing a variant.
 *
 * <p>The price is a {@link BigDecimal} with an explicit scale and a positive minimum, so a malformed
 * or negative amount is rejected at the boundary. Quantity bounds are checked here for shape; the
 * domain additionally enforces that the minimum does not exceed the maximum.
 */
public record VariantRequest(
        @NotBlank(message = "Variant label is required")
        @Size(max = 80, message = "Variant label must be at most 80 characters")
        String label,

        @Size(max = 64, message = "SKU must be at most 64 characters")
        String sku,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.01", message = "Price must be greater than zero")
        @Digits(integer = 7, fraction = 2, message = "Price must have at most two decimal places")
        BigDecimal price,

        @Min(value = 1, message = "Minimum order quantity must be at least 1")
        int minOrderQuantity,

        @Min(value = 1, message = "Maximum order quantity must be at least 1")
        int maxOrderQuantity) {
}
