package com.choco.catalog.application.command;

/** Input for editing an image's presentation metadata. */
public record UpdateImageCommand(String altText, int displayOrder) {
}
