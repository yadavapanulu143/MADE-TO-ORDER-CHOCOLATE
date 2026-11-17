package com.choco.order.application.command;

import java.util.UUID;

public record AssignDeliveryCommand(UUID orderId, String deliveryPersonName, String deliveryPersonPhone) {
}
