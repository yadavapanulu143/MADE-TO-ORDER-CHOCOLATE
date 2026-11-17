package com.choco.order.application.command;

import java.util.UUID;

public record AddToCartCommand(UUID variantId, int quantity) {
}
