package com.choco.order.application.support;

import com.choco.core.domain.EmailAddress;
import com.choco.core.domain.PhoneNumber;
import com.choco.core.domain.Pincode;
import com.choco.order.application.port.CustomerPort;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Identity stand-in holding a customer and their addresses. */
public class StubCustomers implements CustomerPort {

    private final Map<UUID, CustomerSnapshot> customers = new HashMap<>();
    private final Map<UUID, Map<UUID, AddressSnapshot>> addresses = new HashMap<>();

    public StubCustomers withCustomer(UUID customerId, String name, String email, String mobile,
                                      boolean emailVerified, boolean mobileVerified) {
        customers.put(customerId, new CustomerSnapshot(customerId, name, EmailAddress.of(email),
                PhoneNumber.of(mobile), emailVerified, mobileVerified));
        return this;
    }

    public StubCustomers withAddress(UUID customerId, UUID addressId, String recipient, String mobile,
                                     String pincode) {
        addresses.computeIfAbsent(customerId, k -> new HashMap<>())
                .put(addressId, new AddressSnapshot(addressId, customerId, recipient,
                        PhoneNumber.of(mobile), "12 Cocoa Lane", null, "Bengaluru", "Karnataka",
                        Pincode.of(pincode), "Ring the bell"));
        return this;
    }

    public StubCustomers withAddressInPincode(UUID customerId, UUID addressId, String pincode) {
        return withAddress(customerId, addressId, "Asha Rao", "9876543210", pincode);
    }

    @Override
    public Optional<CustomerSnapshot> findCustomer(UUID customerId) {
        return Optional.ofNullable(customers.get(customerId));
    }

    @Override
    public Optional<AddressSnapshot> findAddress(UUID customerId, UUID addressId) {
        return Optional.ofNullable(addresses.getOrDefault(customerId, Map.of()).get(addressId));
    }
}
