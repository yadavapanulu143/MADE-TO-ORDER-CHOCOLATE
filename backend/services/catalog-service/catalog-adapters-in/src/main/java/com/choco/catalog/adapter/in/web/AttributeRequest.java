package com.choco.catalog.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload for adding a descriptive attribute. */
public record AttributeRequest(
        @NotBlank(message = "Attribute name is required")
        @Size(max = 60, message = "Attribute name must be at most 60 characters")
        String name,

        @NotBlank(message = "Attribute value is required")
        @Size(max = 300, message = "Attribute value must be at most 300 characters")
        String value) {
}
