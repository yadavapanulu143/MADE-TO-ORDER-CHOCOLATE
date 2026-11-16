package com.choco.identity.domain.port;

import com.choco.identity.domain.address.Address;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for address persistence.
 *
 * <p>Every read is scoped by customer id. There is deliberately no {@code findById} without one: a
 * lookup that returns an address by its own id alone invites a caller to forget the ownership check,
 * and the mistake is invisible in review because the method looks harmless.
 */
public interface AddressRepository {

    Address save(Address address);

    /** All addresses for a customer, default first then most recently updated. */
    List<Address> findByCustomerId(UUID customerId);

    /** An address, but only if it belongs to the given customer. */
    Optional<Address> findByIdAndCustomerId(UUID addressId, UUID customerId);

    /** The customer's default address, if they have chosen one. */
    Optional<Address> findDefaultByCustomerId(UUID customerId);

    long countByCustomerId(UUID customerId);

    void delete(Address address);

    /** Clears the default flag on every address of a customer, before promoting a new one. */
    void clearDefaultForCustomer(UUID customerId);
}
