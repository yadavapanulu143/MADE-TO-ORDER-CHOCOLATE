package com.choco.identity.application.service;

import com.choco.identity.application.dto.AddressView;
import com.choco.identity.application.dto.CustomerSummaryView;
import com.choco.identity.application.dto.CustomerView;
import com.choco.identity.domain.customer.Customer;
import com.choco.identity.domain.customer.VerificationChannel;
import java.util.List;

/**
 * Maps a {@link Customer} aggregate to its read model.
 *
 * <p>Kept in one place so the mapping is written once. Building the view explicitly, rather than
 * serialising the aggregate, means a field added to {@code Customer} later cannot leak to a client
 * by default.
 */
final class CustomerViews {

    private CustomerViews() {
    }

    static CustomerView from(Customer customer) {
        return new CustomerView(
                customer.id(),
                customer.contact().fullName(),
                customer.contact().email().value(),
                customer.contact().mobile().value(),
                customer.isVerified(VerificationChannel.EMAIL),
                customer.isVerified(VerificationChannel.MOBILE),
                customer.fullyVerified(),
                customer.isActive(),
                customer.registeredAt(),
                List.<AddressView>of());
    }

    static CustomerView withAddresses(Customer customer, List<AddressView> addresses) {
        CustomerView base = from(customer);
        return new CustomerView(base.id(), base.fullName(), base.email(), base.mobile(),
                base.emailVerified(), base.mobileVerified(), base.fullyVerified(), base.active(),
                base.registeredAt(), List.copyOf(addresses));
    }

    /** The paged list row: the same recognition fields, without the addresses. */
    static CustomerSummaryView summary(Customer customer) {
        return new CustomerSummaryView(
                customer.id(),
                customer.contact().fullName(),
                customer.contact().email().value(),
                customer.contact().mobile().value(),
                customer.isVerified(VerificationChannel.EMAIL),
                customer.isVerified(VerificationChannel.MOBILE),
                customer.fullyVerified(),
                customer.isActive(),
                customer.registeredAt());
    }
}
