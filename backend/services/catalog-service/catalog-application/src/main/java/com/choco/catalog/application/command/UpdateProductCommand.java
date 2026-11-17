package com.choco.catalog.application.command;

/** Input for editing a product's descriptive content. */
public record UpdateProductCommand(
        String name,
        String shortDescription,
        String description,
        String preparationInfo,
        String deliveryInfo) {
}
