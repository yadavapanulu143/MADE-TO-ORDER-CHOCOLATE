package com.choco.order.application.command;

import java.util.UUID;

public record AddOrderNoteCommand(UUID orderId, String text) {
}
