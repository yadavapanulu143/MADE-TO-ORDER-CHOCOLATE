package com.choco.order.application.port;

import com.choco.core.domain.EmailAddress;
import com.choco.core.domain.PhoneNumber;
import com.choco.core.domain.Pincode;
import java.util.Optional;
import java.util.UUID;

/**
 * Read access to a customer's own account and addresses.
 *
 * <p>The order service needs three things from identity: whether the account is fully verified, the
 * contact details to freeze onto the order, and one of the customer's saved addresses. It never
 * changes any of them. The lookup is always scoped to the authenticated customer id, so the order
 * service cannot read another account's data even if it is asked to.
 */
public interface CustomerPort {

    /** The contact facts an order snapshot needs, plus the verification flags. */
    record CustomerSnapshot(
            UUID customerId,
            String fullName,
            EmailAddress email,
            PhoneNumber mobile,
            boolean emailVerified,
            boolean mobileVerified) {

        public boolean fullyVerified() {
            return emailVerified && mobileVerified;
        }
    }

    record AddressSnapshot(
            UUID addressId,
            UUID customerId,
            String recipientName,
            PhoneNumber mobile,
            String line1,
            String line2,
            String city,
            String state,
            Pincode pincode,
            String deliveryInstructions) {
    }

    Optional<CustomerSnapshot> findCustomer(UUID customerId);

    /** One of the customer's own addresses. Empty when the id does not belong to them. */
    Optional<AddressSnapshot> findAddress(UUID customerId, UUID addressId);
}
