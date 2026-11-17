package com.choco.fulfilment.adapter.out.persistence;

import com.choco.core.domain.Money;
import com.choco.core.domain.Pincode;
import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.fulfilment.domain.delivery.ServiceableArea;
import com.choco.fulfilment.domain.port.ServiceableAreaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of {@link ServiceableAreaRepository}.
 *
 * <p>The quote lookup runs the longest-prefix rule in the database: the matching areas are ordered by
 * prefix length descending and the first is taken. Doing it here means every caller gets the same
 * answer without having to remember the rule.
 */
@Repository
class ServiceableAreaRepositoryAdapter implements ServiceableAreaRepository {

    private static final List<String> SORTABLE = List.of("areaName", "pincodePrefix", "createdAt");

    private final SpringDataServiceableAreaRepository delegate;

    ServiceableAreaRepositoryAdapter(SpringDataServiceableAreaRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public ServiceableArea save(ServiceableArea area) {
        ServiceableAreaJpaEntity entity = FulfilmentPersistenceMapper.toEntity(area);
        delegate.findVersionById(area.id()).ifPresent(entity::setVersion);
        delegate.save(entity);
        return area;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ServiceableArea> findById(UUID id) {
        return delegate.findById(id).map(FulfilmentPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ServiceableArea> findBestMatch(Pincode pincode) {
        return delegate.findBestMatch(pincode.value(), PageRequest.of(0, 1)).stream().findFirst()
                .map(FulfilmentPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<ServiceableArea> search(boolean includeInactive, String search, PageQuery page) {
        Page<ServiceableAreaJpaEntity> result = delegate.search(includeInactive, likePattern(search),
                toPageable(page));
        List<ServiceableArea> areas = result.getContent().stream()
                .map(FulfilmentPersistenceMapper::toDomain).toList();
        return PageResult.of(areas, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Money> quote(Pincode pincode) {
        return findBestMatch(pincode).map(ServiceableArea::deliveryCharge);
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return delegate.count();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByPrefix(String prefix) {
        return delegate.existsByPincodePrefix(prefix);
    }

    private static String likePattern(String search) {
        if (search == null || search.isBlank()) {
            return "%";
        }
        String escaped = search.trim().toLowerCase(java.util.Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }

    private static PageRequest toPageable(PageQuery query) {
        String sortField = query.sort() == null || !SORTABLE.contains(query.sort()) ? "areaName"
                : query.sort();
        Sort.Direction direction = query.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(query.page(), query.size(), Sort.by(direction, sortField));
    }
}
