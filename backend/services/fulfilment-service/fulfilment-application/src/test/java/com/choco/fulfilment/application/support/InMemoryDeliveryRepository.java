package com.choco.fulfilment.application.support;

import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.fulfilment.domain.delivery.Delivery;
import com.choco.fulfilment.domain.delivery.DeliveryStatus;
import com.choco.fulfilment.domain.port.DeliveryRepository;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** In-memory {@link DeliveryRepository}: one delivery per order, as the database enforces. */
public class InMemoryDeliveryRepository implements DeliveryRepository {

    private final Map<UUID, Delivery> deliveries = new LinkedHashMap<>();

    @Override
    public Delivery save(Delivery delivery) {
        deliveries.put(delivery.id(), delivery);
        return delivery;
    }

    @Override
    public Optional<Delivery> findById(UUID id) {
        return Optional.ofNullable(deliveries.get(id));
    }

    @Override
    public Optional<Delivery> findByOrderId(UUID orderId) {
        return deliveries.values().stream().filter(d -> d.orderId().equals(orderId)).findFirst();
    }

    @Override
    public PageResult<Delivery> findByStatuses(Collection<DeliveryStatus> statuses, String search,
                                               PageQuery page) {
        List<Delivery> matches = deliveries.values().stream()
                .filter(d -> statuses == null || statuses.isEmpty() || statuses.contains(d.status()))
                .filter(d -> search == null || search.isBlank()
                        || d.orderNumber().toLowerCase().contains(search.trim().toLowerCase())
                        || d.address().recipientName().toLowerCase().contains(search.trim().toLowerCase()))
                .sorted(Comparator.comparing(Delivery::createdAt))
                .toList();
        return InMemoryPage.of(matches, page);
    }

    @Override
    public PageResult<Delivery> findByCustomer(UUID customerId, PageQuery page) {
        List<Delivery> matches = deliveries.values().stream()
                .filter(d -> d.customerId().equals(customerId))
                .sorted(Comparator.comparing(Delivery::createdAt))
                .toList();
        return InMemoryPage.of(matches, page);
    }

    @Override
    public long countByStatus(DeliveryStatus status) {
        return deliveries.values().stream().filter(d -> d.status() == status).count();
    }

    @Override
    public long count() {
        return deliveries.size();
    }

    @Override
    public boolean existsForOrder(UUID orderId) {
        return deliveries.values().stream().anyMatch(d -> d.orderId().equals(orderId));
    }
}
