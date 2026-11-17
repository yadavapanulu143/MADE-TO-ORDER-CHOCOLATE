package com.choco.catalog.adapter.in.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Payload for editing an image's presentation metadata. */
public record ImageUpdateRequest(
        @Size(max = 200, message = "Alt text must be at most 200 characters")
        String altText,

        @Min(value = 0, message = "Display order must not be negative")
        int displayOrder) {
}
