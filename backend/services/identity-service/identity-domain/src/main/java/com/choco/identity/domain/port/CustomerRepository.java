package com.choco.identity.domain.port;

import com.choco.core.domain.EmailAddress;
import com.choco.core.domain.PhoneNumber;
import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.identity.domain.customer.Customer;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for customer persistence. The domain depends on this interface, never on JPA, so the
 * storage technology stays an implementation detail.
 */
public interface CustomerRepository {

    Customer save(Customer customer);

    Optional<Customer> findById(UUID id);

    Optional<Customer> findByEmail(EmailAddress email);

    Optional<Customer> findByMobile(PhoneNumber mobile);

    boolean existsByEmail(EmailAddress email);

    boolean existsByMobile(PhoneNumber mobile);

    /**
     * A page of customers for the seller, newest first, optionally narrowed by a search term matched
     * against the name, email or mobile. The search is part of the query rather than a filter applied
     * after loading, so the page size still bounds the work done.
     */
    PageResult<Customer> search(String search, PageQuery page);
}
