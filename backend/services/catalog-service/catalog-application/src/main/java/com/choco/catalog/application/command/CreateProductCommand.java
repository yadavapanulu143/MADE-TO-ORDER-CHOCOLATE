package com.choco.catalog.application.command;

/**
 * Input for creating a product.
 *
 * <p>A record rather than a mutable request object: the use case receives a complete, immutable
 * instruction, so it cannot observe a partially-populated command mid-flight.
 */
public record CreateProductCommand(
        String name,
        String shortDescription,
        String description,
        String preparationInfo,
        String deliveryInfo) {
}
