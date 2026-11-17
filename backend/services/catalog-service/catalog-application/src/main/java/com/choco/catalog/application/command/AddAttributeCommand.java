package com.choco.catalog.application.command;

/** Input for adding a descriptive attribute such as an ingredient or a specification. */
public record AddAttributeCommand(String name, String value) {
}
