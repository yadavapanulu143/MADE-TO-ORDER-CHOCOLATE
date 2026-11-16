package com.choco.identity.application.service;

import com.choco.core.error.ErrorCode;
import com.choco.core.error.NotFoundException;
import com.choco.identity.application.dto.CustomerView;
import com.choco.identity.domain.customer.Customer;
import com.choco.identity.domain.port.CustomerRepository;
import java.util.UUID;

/**
 * Read side for customer profiles.
 *
 * <p>Two methods rather than one with a flag, because the two callers need different data and the
 * difference should be visible in the signature. A single method that returns everything and relies
 * on the caller to hide fields is how a hash or an internal note eventually reaches a response.
 */
public class CustomerQueryService {

    private final CustomerRepository customerRepository;

    public CustomerQueryService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    /** The customer's own view of themselves. */
    public CustomerView findOwnProfile(UUID customerId) {
        return CustomerViews.from(require(customerId));
    }

    /**
     * The administrative view.
     *
     * <p>Currently the same shape as the customer's own view, which is intentional: it is the
     * narrowest thing that satisfies the requirements. If an admin-only field such as an internal
     * note is added later, it belongs in a separate {@code AdminCustomerView}, not in this record.
     */
    public CustomerView findForAdmin(UUID customerId) {
        return CustomerViews.from(require(customerId));
    }

    private Customer require(UUID customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CUSTOMER_NOT_FOUND, "Customer not found"));
    }
}
