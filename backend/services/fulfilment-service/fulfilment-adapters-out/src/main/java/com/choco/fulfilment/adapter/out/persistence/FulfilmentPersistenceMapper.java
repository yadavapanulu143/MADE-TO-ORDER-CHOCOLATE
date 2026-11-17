package com.choco.fulfilment.adapter.out.persistence;

import com.choco.core.domain.Money;
import com.choco.core.domain.PhoneNumber;
import com.choco.core.domain.Pincode;
import com.choco.core.domain.Quantity;
import com.choco.fulfilment.domain.delivery.Delivery;
import com.choco.fulfilment.domain.delivery.DeliveryAddress;
import com.choco.fulfilment.domain.delivery.DeliveryAttempt;
import com.choco.fulfilment.domain.delivery.DeliveryStatus;
import com.choco.fulfilment.domain.delivery.ServiceableArea;
import com.choco.fulfilment.domain.inventory.Ingredient;
import com.choco.fulfilment.domain.inventory.RecipeLine;
import com.choco.fulfilment.domain.inventory.StockLedgerEntry;
import com.choco.fulfilment.domain.inventory.StockLedgerReason;
import com.choco.fulfilment.domain.inventory.StockLevel;
import com.choco.fulfilment.domain.inventory.UnitOfMeasure;
import com.choco.fulfilment.domain.production.ProductionJob;
import com.choco.fulfilment.domain.production.ProductionNote;
import com.choco.fulfilment.domain.production.ProductionStatus;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

/**
 * Translates between the JPA entities and the domain aggregates.
 *
 * <p>Explicit rather than reflective, so the compiler catches a mismatch and a reviewer can see, in
 * one file, exactly which column feeds which business field. A mapping library would infer the
 * translation from field names, and a column rename could then change the domain silently.
 *
 * <p>Every load path uses {@code rehydrate}, never the creation factory, so reading a job cannot
 * re-run creation rules or re-raise an event.
 */
final class FulfilmentPersistenceMapper {

    private FulfilmentPersistenceMapper() {
    }

    // ---------------------------------------------------------------------------------------------
    // Production job
    // ---------------------------------------------------------------------------------------------

    static ProductionJobJpaEntity toEntity(ProductionJob job) {
        ProductionJobJpaEntity entity = new ProductionJobJpaEntity();
        entity.setId(job.id());
        entity.setOrderId(job.orderId());
        entity.setOrderNumber(job.orderNumber());
        entity.setCustomerId(job.customerId());
        entity.setProductId(job.productId());
        entity.setVariantId(job.variantId());
        entity.setVariantLabel(job.variantLabel());
        entity.setQuantity(job.quantity().value());
        writeAddress(entity, job.deliveryAddress());
        entity.setDeliveryChargeMinorUnits(job.deliveryCharge() == null ? 0
                : job.deliveryCharge().minorUnits());
        entity.setCurrency(job.deliveryCharge() == null ? "INR"
                : job.deliveryCharge().currency().getCurrencyCode());
        entity.setStatus(job.status().name());
        entity.setHoldReason(job.holdReason());
        entity.setIngredientsConsumed(job.ingredientsConsumed());
        entity.setReceivedAt(job.receivedAt());
        entity.setUpdatedAt(job.updatedAt());

        List<ProductionNoteJpaEntity> notes = job.notes().stream().map(note -> {
            ProductionNoteJpaEntity row = new ProductionNoteJpaEntity();
            row.setId(note.id());
            row.setJob(entity);
            row.setText(note.text());
            row.setAuthorId(note.authorId());
            row.setCreatedAt(note.createdAt());
            row.setDisplayOrder(entity.getNotes().size());
            return row;
        }).toList();
        entity.getNotes().clear();
        entity.getNotes().addAll(notes);
        return entity;
    }

    static ProductionJob toDomain(ProductionJobJpaEntity entity) {
        ProductionJob job = ProductionJob.rehydrate(
                entity.getId(),
                entity.getOrderId(),
                entity.getOrderNumber(),
                entity.getCustomerId(),
                entity.getProductId(),
                entity.getVariantId(),
                entity.getVariantLabel(),
                Quantity.of(entity.getQuantity()),
                readAddress(entity.getAddressRecipientName(), entity.getAddressMobile(),
                        entity.getAddressLine1(), entity.getAddressLine2(), entity.getAddressCity(),
                        entity.getAddressState(), entity.getAddressPincode(),
                        entity.getAddressInstructions()),
                Money.ofMinorUnits(entity.getDeliveryChargeMinorUnits(),
                        Currency.getInstance(entity.getCurrency())),
                ProductionStatus.valueOf(entity.getStatus()),
                entity.getHoldReason(),
                entity.isIngredientsConsumed(),
                entity.getReceivedAt(),
                entity.getUpdatedAt());
        List<ProductionNote> notes = entity.getNotes().stream()
                .map(row -> ProductionNote.of(row.getId(), row.getText(), row.getAuthorId(),
                        row.getCreatedAt()))
                .toList();
        job.attachNotes(notes);
        return job;
    }

    // ---------------------------------------------------------------------------------------------
    // Ingredient and stock
    // ---------------------------------------------------------------------------------------------

    static IngredientJpaEntity toEntity(Ingredient ingredient) {
        IngredientJpaEntity entity = new IngredientJpaEntity();
        entity.setId(ingredient.id());
        entity.setName(ingredient.name());
        entity.setUnit(ingredient.unit().name());
        entity.setOnHandMilliUnits(ingredient.onHand().milliUnits());
        entity.setMinimumMilliUnits(ingredient.minimumLevel().milliUnits());
        entity.setActive(ingredient.active());
        entity.setCreatedAt(ingredient.createdAt());
        entity.setUpdatedAt(ingredient.updatedAt());
        return entity;
    }

    static Ingredient toDomain(IngredientJpaEntity entity) {
        return Ingredient.rehydrate(
                entity.getId(),
                entity.getName(),
                UnitOfMeasure.valueOf(entity.getUnit()),
                StockLevel.ofMilliUnits(entity.getOnHandMilliUnits()),
                StockLevel.ofMilliUnits(entity.getMinimumMilliUnits()),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    static RecipeLineJpaEntity toEntity(RecipeLine line) {
        RecipeLineJpaEntity entity = new RecipeLineJpaEntity();
        entity.setId(line.id());
        entity.setVariantId(line.variantId());
        entity.setIngredientId(line.ingredientId());
        entity.setPerUnitMilliUnits(line.perUnit().milliUnits());
        return entity;
    }

    static RecipeLine toDomain(RecipeLineJpaEntity entity) {
        return RecipeLine.of(entity.getId(), entity.getVariantId(), entity.getIngredientId(),
                StockLevel.ofMilliUnits(entity.getPerUnitMilliUnits()));
    }

    static StockLedgerEntryJpaEntity toEntity(StockLedgerEntry entry) {
        StockLedgerEntryJpaEntity entity = new StockLedgerEntryJpaEntity();
        entity.setId(entry.id());
        entity.setIngredientId(entry.ingredientId());
        entity.setReason(entry.reason().name());
        entity.setDeltaMilliUnits(entry.delta().milliUnits());
        entity.setBalanceAfterMilliUnits(entry.balanceAfter().milliUnits());
        entity.setProductionJobId(entry.productionJobId());
        entity.setNote(entry.note());
        entity.setOccurredAt(entry.occurredAt());
        return entity;
    }

    static StockLedgerEntry toDomain(StockLedgerEntryJpaEntity entity) {
        return StockLedgerEntry.of(entity.getId(), entity.getIngredientId(),
                StockLedgerReason.valueOf(entity.getReason()),
                StockLevel.ofMilliUnits(entity.getDeltaMilliUnits()),
                StockLevel.ofMilliUnits(entity.getBalanceAfterMilliUnits()),
                entity.getProductionJobId(), entity.getNote(), entity.getOccurredAt());
    }

    // ---------------------------------------------------------------------------------------------
    // Delivery
    // ---------------------------------------------------------------------------------------------

    static ServiceableAreaJpaEntity toEntity(ServiceableArea area) {
        ServiceableAreaJpaEntity entity = new ServiceableAreaJpaEntity();
        entity.setId(area.id());
        entity.setPincodePrefix(area.pincodePrefix());
        entity.setAreaName(area.areaName());
        entity.setDeliveryChargeMinorUnits(area.deliveryCharge().minorUnits());
        entity.setCurrency(area.deliveryCharge().currency().getCurrencyCode());
        entity.setActive(area.active());
        entity.setCreatedAt(area.createdAt());
        entity.setUpdatedAt(area.updatedAt());
        return entity;
    }

    static ServiceableArea toDomain(ServiceableAreaJpaEntity entity) {
        return ServiceableArea.rehydrate(entity.getId(), entity.getPincodePrefix(), entity.getAreaName(),
                Money.ofMinorUnits(entity.getDeliveryChargeMinorUnits(),
                        Currency.getInstance(entity.getCurrency())),
                entity.isActive(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    static DeliveryJpaEntity toEntity(Delivery delivery) {
        DeliveryJpaEntity entity = new DeliveryJpaEntity();
        entity.setId(delivery.id());
        entity.setOrderId(delivery.orderId());
        entity.setOrderNumber(delivery.orderNumber());
        entity.setCustomerId(delivery.customerId());
        DeliveryAddress address = delivery.address();
        entity.setAddressRecipientName(address.recipientName());
        entity.setAddressMobile(address.mobile().value());
        entity.setAddressLine1(address.line1());
        entity.setAddressLine2(address.line2());
        entity.setAddressCity(address.city());
        entity.setAddressState(address.state());
        entity.setAddressPincode(address.pincode().value());
        entity.setAddressInstructions(address.deliveryInstructions());
        entity.setDeliveryChargeMinorUnits(delivery.deliveryCharge().minorUnits());
        entity.setCurrency(delivery.deliveryCharge().currency().getCurrencyCode());
        entity.setStatus(delivery.status().name());
        entity.setDeliveryPersonName(delivery.deliveryPersonName());
        entity.setDeliveryPersonPhone(delivery.deliveryPersonPhone());
        entity.setDispatchedAt(delivery.dispatchedAt());
        entity.setDeliveredAt(delivery.deliveredAt());
        entity.setProofReference(delivery.proofReference());
        entity.setCreatedAt(delivery.createdAt());
        entity.setUpdatedAt(delivery.updatedAt());

        List<DeliveryAttemptJpaEntity> attempts = delivery.attempts().stream().map(attempt -> {
            DeliveryAttemptJpaEntity row = new DeliveryAttemptJpaEntity();
            row.setId(attempt.id());
            row.setDelivery(entity);
            row.setAttemptNumber(attempt.attemptNumber());
            row.setOutcome(attempt.outcome().name());
            row.setFailureReason(attempt.failureReason());
            row.setNotes(attempt.notes());
            row.setAttemptedAt(attempt.attemptedAt());
            return row;
        }).toList();
        entity.getAttempts().clear();
        entity.getAttempts().addAll(attempts);
        return entity;
    }

    static Delivery toDomain(DeliveryJpaEntity entity) {
        Delivery delivery = Delivery.rehydrate(
                entity.getId(),
                entity.getOrderId(),
                entity.getOrderNumber(),
                entity.getCustomerId(),
                readAddress(entity.getAddressRecipientName(), entity.getAddressMobile(),
                        entity.getAddressLine1(), entity.getAddressLine2(), entity.getAddressCity(),
                        entity.getAddressState(), entity.getAddressPincode(),
                        entity.getAddressInstructions()),
                Money.ofMinorUnits(entity.getDeliveryChargeMinorUnits(),
                        Currency.getInstance(entity.getCurrency())),
                DeliveryStatus.valueOf(entity.getStatus()),
                entity.getDeliveryPersonName(),
                entity.getDeliveryPersonPhone(),
                entity.getDispatchedAt(),
                entity.getDeliveredAt(),
                entity.getProofReference(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
        List<DeliveryAttempt> attempts = entity.getAttempts().stream()
                .map(row -> DeliveryAttempt.of(row.getId(), row.getAttemptNumber(),
                        DeliveryStatus.valueOf(row.getOutcome()), row.getFailureReason(), row.getNotes(),
                        row.getAttemptedAt()))
                .toList();
        delivery.attachAttempts(attempts);
        return delivery;
    }

    // ---------------------------------------------------------------------------------------------

    private static void writeAddress(ProductionJobJpaEntity entity, DeliveryAddress address) {
        if (address == null) {
            return;
        }
        entity.setAddressRecipientName(address.recipientName());
        entity.setAddressMobile(address.mobile().value());
        entity.setAddressLine1(address.line1());
        entity.setAddressLine2(address.line2());
        entity.setAddressCity(address.city());
        entity.setAddressState(address.state());
        entity.setAddressPincode(address.pincode().value());
        entity.setAddressInstructions(address.deliveryInstructions());
    }

    /**
     * Rebuilds the address, or {@code null} when the row carries none.
     *
     * <p>A job opened before the address was known has no address columns; that is a legitimate state
     * the kitchen works in, so it must round-trip as {@code null} rather than as an empty address.
     */
    private static DeliveryAddress readAddress(String recipientName, String mobile, String line1,
                                               String line2, String city, String state, String pincode,
                                               String instructions) {
        if (recipientName == null || pincode == null || mobile == null) {
            return null;
        }
        return new DeliveryAddress(recipientName, PhoneNumber.of(mobile), line1, line2, city, state,
                Pincode.of(pincode), instructions);
    }
}
