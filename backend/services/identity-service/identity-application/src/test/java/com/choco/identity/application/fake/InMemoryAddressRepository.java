package com.choco.identity.application.fake;

import com.choco.identity.domain.address.Address;
import com.choco.identity.domain.port.AddressRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory address store with the same ordering and scoping rules as the JPA adapter.
 *
 * <p>Every read is filtered by customer id here too, so a test that tries to reach another
 * customer's address fails for the same reason it would against Postgres rather than passing because
 * the fake happens to be more permissive than the real thing.
 */
public class InMemoryAddressRepository implements AddressRepository {

    private final Map<UUID, Address> byId = new LinkedHashMap<>();

    @Override
    public Address save(Address address) {
        byId.put(address.id(), address);
        return address;
    }

    @Override
    public List<Address> findByCustomerId(UUID customerId) {
        return byId.values().stream()
                .filter(a -> a.customerId().equals(customerId))
                .sorted(Comparator.comparing(Address::isDefault).reversed()
                        .thenComparing(Address::updatedAt, Comparator.reverseOrder()))
                .toList();
    }

    @Override
    public Optional<Address> findByIdAndCustomerId(UUID addressId, UUID customerId) {
        return Optional.ofNullable(byId.get(addressId))
                .filter(a -> a.customerId().equals(customerId));
    }

    @Override
    public Optional<Address> findDefaultByCustomerId(UUID customerId) {
        return byId.values().stream()
                .filter(a -> a.customerId().equals(customerId) && a.isDefault())
                .findFirst();
    }

    @Override
    public long countByCustomerId(UUID customerId) {
        return byId.values().stream().filter(a -> a.customerId().equals(customerId)).count();
    }

    @Override
    public void delete(Address address) {
        byId.remove(address.id());
    }

    @Override
    public void clearDefaultForCustomer(UUID customerId) {
        byId.values().stream()
                .filter(a -> a.customerId().equals(customerId) && a.isDefault())
                .forEach(Address::clearDefault);
    }

    /** Snapshot for transaction tests: the caller restores it to simulate a rollback. */
    public Map<UUID, Address> snapshot() {
        return new LinkedHashMap<>(byId);
    }

    public void restore(Map<UUID, Address> snapshot) {
        byId.clear();
        byId.putAll(snapshot);
    }

    public List<Address> all() {
        return new ArrayList<>(byId.values());
    }
}
