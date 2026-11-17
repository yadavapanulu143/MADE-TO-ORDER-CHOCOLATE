package com.choco.order.application.command;

import java.util.UUID;

public record RequestRefundCommand(UUID orderId, String reason) {
}
