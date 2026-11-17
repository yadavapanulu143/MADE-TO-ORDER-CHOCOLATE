package com.choco.fulfilment.application.service;

import com.choco.core.domain.Money;
import com.choco.fulfilment.application.dto.DeliveryAddressView;
import com.choco.fulfilment.application.dto.DeliveryAttemptView;
import com.choco.fulfilment.application.dto.DeliveryView;
import com.choco.fulfilment.application.dto.IngredientView;
import com.choco.fulfilment.application.dto.ProductionJobView;
import com.choco.fulfilment.application.dto.ProductionNoteView;
import com.choco.fulfilment.application.dto.ServiceableAreaView;
import com.choco.fulfilment.application.dto.StockLedgerEntryView;
import com.choco.fulfilment.domain.delivery.Delivery;
import com.choco.fulfilment.domain.delivery.DeliveryAddress;
import com.choco.fulfilment.domain.delivery.DeliveryAttempt;
import com.choco.fulfilment.domain.delivery.ServiceableArea;
import com.choco.fulfilment.domain.inventory.Ingredient;
import com.choco.fulfilment.domain.inventory.StockLedgerEntry;
import com.choco.fulfilment.domain.production.ProductionJob;
import com.choco.fulfilment.domain.production.ProductionNote;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Turns domain aggregates into the records the API returns.
 *
 * <p>Written by hand rather than by reflecting over the aggregate, so a new field is not exposed until
 * someone deliberately adds it here — the safe default is to leak nothing.
 *
 * <p>Every money value is emitted twice: exact minor units for a programmatic caller and a decimal
 * string for display. The decimal is a {@code String}, never a {@code double}, so a client cannot
 * reintroduce a floating-point rounding error. Stock amounts follow the same rule in thousandths.
 */
public final class FulfilmentViewMapper {

    private FulfilmentViewMapper() {
    }

    public static ProductionJobView toView(ProductionJob job) {
        return new ProductionJobView(
                job.id(),
                job.orderId(),
                job.orderNumber(),
                job.customerId(),
                job.productId(),
                job.variantId(),
                job.variantLabel(),
                job.quantity().value(),
                job.status().name(),
                job.holdReason(),
                job.ingredientsConsumed(),
                job.receivedAt(),
                job.updatedAt(),
                job.notes().stream().map(FulfilmentViewMapper::toView).toList());
    }

    public static ProductionNoteView toView(ProductionNote note) {
        return new ProductionNoteView(note.id(), note.text(), note.authorId(), note.createdAt());
    }

    public static IngredientView toView(Ingredient ingredient) {
        return new IngredientView(
                ingredient.id(),
                ingredient.name(),
                ingredient.unit().symbol(),
                stock(ingredient.onHand().milliUnits()),
                ingredient.onHand().milliUnits(),
                stock(ingredient.minimumLevel().milliUnits()),
                ingredient.minimumLevel().milliUnits(),
                ingredient.isBelowMinimum(),
                ingredient.active(),
                ingredient.createdAt(),
                ingredient.updatedAt());
    }

    public static StockLedgerEntryView toView(StockLedgerEntry entry) {
        return new StockLedgerEntryView(
                entry.id(),
                entry.ingredientId(),
                entry.reason().name(),
                stock(entry.delta().milliUnits()),
                entry.delta().milliUnits(),
                stock(entry.balanceAfter().milliUnits()),
                entry.balanceAfter().milliUnits(),
                entry.productionJobId(),
                entry.note(),
                entry.occurredAt());
    }

    public static DeliveryView toView(Delivery delivery) {
        return new DeliveryView(
                delivery.id(),
                delivery.orderId(),
                delivery.orderNumber(),
                delivery.customerId(),
                toView(delivery.address()),
                delivery.pincode().value(),
                delivery.deliveryCharge().minorUnits(),
                decimal(delivery.deliveryCharge()),
                delivery.deliveryCharge().currency().getCurrencyCode(),
                delivery.status().name(),
                delivery.deliveryPersonName(),
                delivery.deliveryPersonPhone(),
                delivery.dispatchedAt(),
                delivery.deliveredAt(),
                delivery.proofReference(),
                delivery.attemptCount(),
                delivery.createdAt(),
                delivery.updatedAt(),
                delivery.attempts().stream().map(FulfilmentViewMapper::toView).toList());
    }

    public static DeliveryAddressView toView(DeliveryAddress address) {
        return new DeliveryAddressView(
                address.recipientName(),
                address.mobile().value(),
                address.line1(),
                address.line2(),
                address.city(),
                address.state(),
                address.pincode().value(),
                address.deliveryInstructions(),
                address.formatted());
    }

    public static DeliveryAttemptView toView(DeliveryAttempt attempt) {
        return new DeliveryAttemptView(
                attempt.id(),
                attempt.attemptNumber(),
                attempt.outcome().name(),
                attempt.failureReason(),
                attempt.notes(),
                attempt.attemptedAt());
    }

    public static ServiceableAreaView toView(ServiceableArea area) {
        return new ServiceableAreaView(
                area.id(),
                area.pincodePrefix(),
                area.areaName(),
                area.deliveryCharge().minorUnits(),
                decimal(area.deliveryCharge()),
                area.deliveryCharge().currency().getCurrencyCode(),
                area.active(),
                area.createdAt(),
                area.updatedAt());
    }

    private static String decimal(Money money) {
        return money.toMajorUnits().setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    /** Thousandths to a three-decimal amount, e.g. 1500 -> 1.500. */
    private static BigDecimal stock(long milliUnits) {
        return BigDecimal.valueOf(milliUnits, 3);
    }
}
