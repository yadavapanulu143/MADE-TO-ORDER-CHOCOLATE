package com.choco.catalog.application.dto;

import java.util.UUID;

/** A descriptive attribute as presented to a caller. */
public record AttributeView(UUID id, String name, String value, int displayOrder) {
}
