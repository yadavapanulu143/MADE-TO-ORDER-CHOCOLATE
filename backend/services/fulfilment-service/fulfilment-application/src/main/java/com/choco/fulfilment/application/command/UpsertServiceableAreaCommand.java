package com.choco.fulfilment.application.command;

import java.util.UUID;

/**
 * Creates or updates a delivery area the seller covers.
 *
 * <p>The prefix is either an exact six-digit PIN code or a shorter prefix of one, so a seller who
 * covers a whole city can enter it once. The charge is in paise.
 */
public record UpsertServiceableAreaCommand(UUID areaId, String pincodePrefix, String areaName,
                                           long deliveryChargeMinorUnits, Boolean active) {
}
