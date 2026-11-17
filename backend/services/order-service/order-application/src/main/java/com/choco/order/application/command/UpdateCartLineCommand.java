package com.choco.order.application.command;

import java.util.UUID;

public record UpdateCartLineCommand(UUID variantId, int quantity) {
}
