package com.choco.catalog.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for creating or editing a product's descriptive content.
 *
 * <p>Lengths are bounded here as well as in the domain. Bean Validation rejects an oversized field
 * before any business logic runs, which gives the caller a field-specific message instead of a
 * database truncation error, and keeps the request from reaching the use case at all.
 */
public record ProductRequest(
        @NotBlank(message = "Product name is required")
        @Size(min = 2, max = 150, message = "Product name must be between 2 and 150 characters")
        String name,

        @Size(max = 500, message = "Short description must be at most 500 characters")
        String shortDescription,

        @Size(max = 5000, message = "Description must be at most 5000 characters")
        String description,

        @Size(max = 1000, message = "Preparation information must be at most 1000 characters")
        String preparationInfo,

        @Size(max = 1000, message = "Delivery information must be at most 1000 characters")
        String deliveryInfo) {
}
