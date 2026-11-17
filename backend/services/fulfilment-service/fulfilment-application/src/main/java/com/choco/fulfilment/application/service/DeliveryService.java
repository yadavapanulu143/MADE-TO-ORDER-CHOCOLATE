package com.choco.fulfilment.application.service;

import com.choco.core.domain.Money;
import com.choco.core.domain.Pincode;
import com.choco.core.error.ConflictException;
import com.choco.core.error.ErrorCode;
import com.choco.core.error.NotFoundException;
import com.choco.core.page.PageResult;
import com.choco.core.port.Clock;
import com.choco.core.port.EventPublisher;
import com.choco.core.port.IdGenerator;
import com.choco.core.port.PageQuery;
import com.choco.core.port.TransactionRunner;
import com.choco.fulfilment.application.command.DeliveryAssignCommand;
import com.choco.fulfilment.application.command.DeliveryProgressCommand;
import com.choco.fulfilment.application.command.UpsertServiceableAreaCommand;
import com.choco.fulfilment.application.dto.DeliveryCounters;
import com.choco.fulfilment.application.dto.DeliveryView;
import com.choco.fulfilment.application.dto.ServiceableAreaView;
import com.choco.fulfilment.application.dto.ServiceabilityQuoteView;
import com.choco.fulfilment.application.port.AuditLogPort;
import com.choco.fulfilment.domain.delivery.Delivery;
import com.choco.fulfilment.domain.delivery.DeliveryStatus;
import com.choco.fulfilment.domain.delivery.ServiceableArea;
import com.choco.fulfilment.domain.port.DeliveryRepository;
import com.choco.fulfilment.domain.port.ServiceableAreaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Seller-managed delivery, and the coverage rules checkout depends on.
 *
 * <p><b>No courier is integrated.</b> The seller decides who delivers, records each step, and the
 * customer sees exactly what was recorded — nothing is invented. There is no tracking number and no
 * live position, because none exists.
 *
 * <p><b>Serviceability is a business setting, not a lookup.</b> The seller maintains the PIN codes they
 * cover and the charge for each. The longest matching prefix wins, so a seller who covers a whole city
 * can enter it once and still override one PIN code with a different charge. A PIN code the seller does
 * not cover is a definite "no" with a reason the checkout can show the customer.
 *
 * <p>Proof of delivery is configurable. When the seller turns it on, marking a delivery delivered
 * requires a reference — the point of the setting is that "delivered" is not taken on trust.
 */
public class DeliveryService {

    private static final String ACTOR_ADMIN = "ADMIN";
    private static final String TARGET_AREA = "SERVICEABLE_AREA";
    private static final String TARGET_DELIVERY = "DELIVERY";

    private final DeliveryRepository deliveryRepository;
    private final ServiceableAreaRepository areaRepository;
    private final EventPublisher eventPublisher;
    private final AuditLogPort auditLog;
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final TransactionRunner transactions;
    private final boolean proofOfDeliveryRequired;

    public DeliveryService(DeliveryRepository deliveryRepository, ServiceableAreaRepository areaRepository,
                           EventPublisher eventPublisher, AuditLogPort auditLog, Clock clock,
                           IdGenerator idGenerator, TransactionRunner transactions,
                           boolean proofOfDeliveryRequired) {
        this.deliveryRepository = deliveryRepository;
        this.areaRepository = areaRepository;
        this.eventPublisher = eventPublisher;
        this.auditLog = auditLog;
        this.clock = clock;
        this.idGenerator = idGenerator;
        this.transactions = transactions;
        this.proofOfDeliveryRequired = proofOfDeliveryRequired;
    }

    // -------------------------------------------------------------------------------------------
    // Coverage and quoting
    // -------------------------------------------------------------------------------------------

    /**
     * The answer the order service's checkout needs: whether the seller delivers here, and the charge.
     *
     * <p>Not serviceable is a normal answer, not an error: the checkout turns it into a message for the
     * customer. The reason is written for the customer to read.
     */
    public ServiceabilityQuoteView quote(Pincode pincode) {
        Optional<ServiceableArea> match = areaRepository.findBestMatch(pincode);
        if (match.isEmpty()) {
            return new ServiceabilityQuoteView(false, pincode.value(), 0, null, "INR",
                    "We do not deliver to PIN code " + pincode.value() + " yet");
        }
        Money charge = match.get().deliveryCharge();
        return new ServiceabilityQuoteView(true, pincode.value(), charge.minorUnits(),
                FulfilmentViewMapper.toView(match.get()).deliveryCharge(),
                charge.currency().getCurrencyCode(), null);
    }

    /** Creates or updates a delivery area the seller covers. */
    public ServiceableAreaView upsertArea(String adminId, UpsertServiceableAreaCommand command) {
        return transactions.execute(() -> {
            Money charge = Money.ofMinorUnits(command.deliveryChargeMinorUnits(),
                    java.util.Currency.getInstance("INR"));
            if (command.areaId() == null) {
                if (areaRepository.existsByPrefix(command.pincodePrefix().trim())) {
                    throw new ConflictException(ErrorCode.CONFLICT,
                            "A delivery area for " + command.pincodePrefix() + " already exists");
                }
                ServiceableArea area = ServiceableArea.create(idGenerator.nextId(), command.pincodePrefix(),
                        command.areaName(), charge, clock.now());
                if (Boolean.FALSE.equals(command.active())) {
                    area.deactivate(clock.now());
                }
                ServiceableArea saved = areaRepository.save(area);
                audit(adminId, "delivery.area.create", TARGET_AREA, saved.id().toString(), "SUCCESS",
                        saved.pincodePrefix());
                return FulfilmentViewMapper.toView(saved);
            }
            ServiceableArea area = requireArea(command.areaId());
            if (command.areaName() != null && !command.areaName().isBlank()) {
                area.rename(command.areaName(), clock.now());
            }
            if (command.deliveryChargeMinorUnits() > 0) {
                area.updateCharge(charge, clock.now());
            }
            if (Boolean.TRUE.equals(command.active())) {
                area.activate(clock.now());
            } else if (Boolean.FALSE.equals(command.active())) {
                area.deactivate(clock.now());
            }
            ServiceableArea saved = areaRepository.save(area);
            audit(adminId, "delivery.area.update", TARGET_AREA, saved.id().toString(), "SUCCESS",
                    saved.pincodePrefix());
            return FulfilmentViewMapper.toView(saved);
        });
    }

    public PageResult<ServiceableAreaView> listAreas(boolean includeInactive, String search, PageQuery page) {
        return areaRepository.search(includeInactive, search, page).map(FulfilmentViewMapper::toView);
    }

    // -------------------------------------------------------------------------------------------
    // Delivery steps
    // -------------------------------------------------------------------------------------------

    /** Records who will deliver an order. */
    public DeliveryView assign(String adminId, DeliveryAssignCommand command) {
        return transactions.execute(() -> {
            Delivery delivery = requireByOrder(command.orderId());
            delivery.assign(command.deliveryPersonName(), command.deliveryPersonPhone(), adminId, clock.now());
            Delivery saved = deliveryRepository.save(delivery);
            // The first assignment raises DeliveryAssigned; without draining it here the customer is
            // never told a delivery has been arranged, because this is the only place the event can
            // leave the aggregate.
            eventPublisher.publishAll(delivery.pullDomainEvents());
            audit(adminId, "delivery.assign", TARGET_DELIVERY, saved.id().toString(), "SUCCESS",
                    command.deliveryPersonName());
            return FulfilmentViewMapper.toView(saved);
        });
    }

    /**
     * Records a delivery step.
     *
     * <p>One entry point dispatching on the requested action. The aggregate decides whether the step is
     * legal from the current status, so the seller cannot mark an undelivered parcel delivered or a
     * cancelled delivery out for delivery.
     */
    public DeliveryView advance(String adminId, DeliveryProgressCommand command) {
        return transactions.execute(() -> {
            Delivery delivery = requireByOrder(command.orderId());
            switch (command.action()) {
                case OUT_FOR_DELIVERY -> delivery.markOutForDelivery(adminId, clock.now());
                case DELIVERED -> delivery.markDelivered(command.proofReference(), command.notes(),
                        proofOfDeliveryRequired, adminId, clock.now());
                case ATTEMPT_FAILED -> delivery.recordFailedAttempt(command.reason(), command.notes(),
                        adminId, clock.now());
                case RESCHEDULED -> delivery.reschedule(command.notes(), adminId, clock.now());
                case RETURNED -> delivery.markReturned(command.reason(), adminId, clock.now());
                case CANCEL -> delivery.cancel(command.reason(), adminId, clock.now());
            }
            Delivery saved = deliveryRepository.save(delivery);
            eventPublisher.publishAll(delivery.pullDomainEvents());
            audit(adminId, "delivery." + command.action().name().toLowerCase(), TARGET_DELIVERY,
                    saved.id().toString(), "SUCCESS", command.reason());
            return FulfilmentViewMapper.toView(saved);
        });
    }

    public DeliveryView forOrder(UUID orderId) {
        return FulfilmentViewMapper.toView(requireByOrder(orderId));
    }

    /**
     * One of the caller's own orders' deliveries.
     *
     * <p>Scoped by customer id, so a guessed order id belonging to somebody else is not found rather
     * than returned. This is the customer's delivery-tracking view: it shows exactly the steps the
     * seller recorded, and nothing that was not recorded.
     */
    public DeliveryView forCustomerOrder(UUID customerId, UUID orderId) {
        Delivery delivery = requireByOrder(orderId);
        if (!delivery.customerId().equals(customerId)) {
            throw new NotFoundException(ErrorCode.NOT_FOUND, "No delivery exists for this order yet");
        }
        return FulfilmentViewMapper.toView(delivery);
    }

    public PageResult<DeliveryView> search(List<DeliveryStatus> statuses, String search, PageQuery page) {
        return deliveryRepository.findByStatuses(statuses, search, page)
                .map(FulfilmentViewMapper::toView);
    }

    /** A customer's own deliveries, so they see only theirs. */
    public PageResult<DeliveryView> forCustomer(UUID customerId, PageQuery page) {
        return deliveryRepository.findByCustomer(customerId, page).map(FulfilmentViewMapper::toView);
    }

    public DeliveryCounters counters() {
        return new DeliveryCounters(
                deliveryRepository.countByStatus(DeliveryStatus.PENDING),
                deliveryRepository.countByStatus(DeliveryStatus.ASSIGNED),
                deliveryRepository.countByStatus(DeliveryStatus.OUT_FOR_DELIVERY),
                deliveryRepository.countByStatus(DeliveryStatus.DELIVERED),
                deliveryRepository.countByStatus(DeliveryStatus.ATTEMPT_FAILED),
                deliveryRepository.countByStatus(DeliveryStatus.RESCHEDULED),
                deliveryRepository.countByStatus(DeliveryStatus.CANCELLED),
                deliveryRepository.countByStatus(DeliveryStatus.RETURNED),
                deliveryRepository.count());
    }

    private Delivery requireByOrder(UUID orderId) {
        return deliveryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND,
                        "No delivery exists for this order yet"));
    }

    private ServiceableArea requireArea(UUID areaId) {
        return areaRepository.findById(areaId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "Delivery area not found"));
    }

    private void audit(String actorId, String action, String targetType, String targetId, String outcome,
                       String detail) {
        auditLog.record(ACTOR_ADMIN, actorId, action, targetType, targetId, outcome, detail, null);
    }
}
