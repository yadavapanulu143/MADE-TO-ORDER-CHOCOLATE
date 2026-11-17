package com.choco.fulfilment.domain.port;

import com.choco.core.domain.Money;
import com.choco.core.domain.Pincode;
import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.fulfilment.domain.delivery.ServiceableArea;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for the seller's delivery coverage.
 *
 * <p>The quote lookup is the hot path: the order service calls it synchronously at checkout. It is
 * expressed as "the best match for this PIN" rather than "all areas", so the longest-prefix rule is a
 * query decision and not something every caller has to remember to apply.
 */
public interface ServiceableAreaRepository {

    ServiceableArea save(ServiceableArea area);

    Optional<ServiceableArea> findById(UUID id);

    /** The most specific active area covering this PIN code, if the seller delivers there at all. */
    Optional<ServiceableArea> findBestMatch(Pincode pincode);

    PageResult<ServiceableArea> search(boolean includeInactive, String search, PageQuery page);

    /** The charge for a PIN code, or empty when the seller does not deliver there. */
    Optional<Money> quote(Pincode pincode);

    long count();

    boolean existsByPrefix(String prefix);
}
