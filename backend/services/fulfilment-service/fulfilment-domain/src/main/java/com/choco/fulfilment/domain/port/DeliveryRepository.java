package com.choco.fulfilment.domain.port;

import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.fulfilment.domain.delivery.Delivery;
import com.choco.fulfilment.domain.delivery.DeliveryStatus;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for deliveries. */
public interface DeliveryRepository {

    Delivery save(Delivery delivery);

    Optional<Delivery> findById(UUID id);

    Optional<Delivery> findByOrderId(UUID orderId);

    /** A page of deliveries in any of the given statuses, for the seller's delivery board. */
    PageResult<Delivery> findByStatuses(Collection<DeliveryStatus> statuses, String search, PageQuery page);

    /** A page of one customer's deliveries, so a customer sees only their own. */
    PageResult<Delivery> findByCustomer(UUID customerId, PageQuery page);

    long countByStatus(DeliveryStatus status);

    long count();

    boolean existsForOrder(UUID orderId);
}
