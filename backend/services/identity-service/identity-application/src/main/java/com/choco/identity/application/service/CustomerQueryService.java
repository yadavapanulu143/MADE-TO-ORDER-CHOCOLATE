package com.choco.identity.application.service;

import com.choco.core.error.ErrorCode;
import com.choco.core.error.NotFoundException;
import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.identity.application.dto.AddressView;
import com.choco.identity.application.dto.CustomerSummaryView;
import com.choco.identity.application.dto.CustomerView;
import com.choco.identity.domain.customer.Customer;
import com.choco.identity.domain.port.AddressRepository;
import com.choco.identity.domain.port.CustomerRepository;
import java.util.List;
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
    private final AddressRepository addressRepository;

    public CustomerQueryService(CustomerRepository customerRepository,
                                AddressRepository addressRepository) {
        this.customerRepository = customerRepository;
        this.addressRepository = addressRepository;
    }

    /** The customer's own view of themselves, including their saved addresses. */
    public CustomerView findOwnProfile(UUID customerId) {
        Customer customer = require(customerId);
        List<AddressView> addresses = addressRepository.findByCustomerId(customerId).stream()
                .map(AddressService::toView)
                .toList();
        return CustomerViews.withAddresses(customer, addresses);
    }

    /**
     * The administrative view.
     *
     * <p>Currently the same shape as the customer's own view, which is intentional: it is the
     * narrowest thing that satisfies the requirements. If an admin-only field such as an internal
     * note is added later, it belongs in a separate {@code AdminCustomerView}, not in this record.
     */
    public CustomerView findForAdmin(UUID customerId) {
        return findOwnProfile(customerId);
    }

    /**
     * A page of customers for the seller, newest first, optionally narrowed by a search term.
     *
     * <p>Returns a summary rather than the full profile: the list can hold every customer, and the
     * addresses and any future admin-only note belong to the single-customer read, not to a paged
     * listing. The mapping here is the only place a row becomes a response, so no field can leak into
     * the list by accident.
     */
    public PageResult<CustomerSummaryView> listForAdmin(String search, PageQuery page) {
        return customerRepository.search(search, page)
                .map(CustomerViews::summary);
    }

    private Customer require(UUID customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CUSTOMER_NOT_FOUND, "Customer not found"));
    }
}
