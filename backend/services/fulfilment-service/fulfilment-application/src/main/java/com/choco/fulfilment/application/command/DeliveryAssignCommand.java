package com.choco.fulfilment.application.command;

import java.util.UUID;

/** Assigns delivery responsibility for an order to a named person the seller manages. */
public record DeliveryAssignCommand(UUID orderId, String deliveryPersonName, String deliveryPersonPhone) {
}
