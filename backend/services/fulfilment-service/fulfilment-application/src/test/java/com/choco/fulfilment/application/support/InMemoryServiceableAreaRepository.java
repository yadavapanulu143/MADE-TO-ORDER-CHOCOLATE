package com.choco.fulfilment.application.support;

import com.choco.core.domain.Money;
import com.choco.core.domain.Pincode;
import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.fulfilment.domain.delivery.ServiceableArea;
import com.choco.fulfilment.domain.port.ServiceableAreaRepository;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory {@link ServiceableAreaRepository}.
 *
 * <p>Applies the same longest-prefix rule as the database, so a test of the quoting use case exercises
 * the real rule rather than a simplified stand-in.
 */
public class InMemoryServiceableAreaRepository implements ServiceableAreaRepository {

    private final Map<UUID, ServiceableArea> areas = new LinkedHashMap<>();

    @Override
    public ServiceableArea save(ServiceableArea area) {
        areas.put(area.id(), area);
        return area;
    }

    @Override
    public Optional<ServiceableArea> findById(UUID id) {
        return Optional.ofNullable(areas.get(id));
    }

    @Override
    public Optional<ServiceableArea> findBestMatch(Pincode pincode) {
        return areas.values().stream()
                .filter(ServiceableArea::active)
                .filter(area -> pincode.value().startsWith(area.pincodePrefix()))
                .max(Comparator.comparingInt(area -> area.pincodePrefix().length()));
    }

    @Override
    public PageResult<ServiceableArea> search(boolean includeInactive, String search, PageQuery page) {
        List<ServiceableArea> matches = areas.values().stream()
                .filter(a -> includeInactive || a.active())
                .filter(a -> search == null || search.isBlank()
                        || a.areaName().toLowerCase().contains(search.trim().toLowerCase())
                        || a.pincodePrefix().startsWith(search.trim()))
                .sorted(Comparator.comparing(ServiceableArea::areaName))
                .toList();
        return InMemoryPage.of(matches, page);
    }

    @Override
    public Optional<Money> quote(Pincode pincode) {
        return findBestMatch(pincode).map(ServiceableArea::deliveryCharge);
    }

    @Override
    public long count() {
        return areas.size();
    }

    @Override
    public boolean existsByPrefix(String prefix) {
        return areas.values().stream().anyMatch(a -> a.pincodePrefix().equals(prefix));
    }
}
