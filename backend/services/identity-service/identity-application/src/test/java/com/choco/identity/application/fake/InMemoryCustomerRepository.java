package com.choco.identity.application.fake;

import com.choco.core.domain.EmailAddress;
import com.choco.core.domain.PhoneNumber;
import com.choco.identity.domain.customer.Customer;
import com.choco.identity.domain.port.CustomerRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** In-memory customer store. Real behaviour, no database, so tests stay fast and deterministic. */
public class InMemoryCustomerRepository implements CustomerRepository {

    private final Map<UUID, Customer> byId = new LinkedHashMap<>();

    @Override
    public Customer save(Customer customer) {
        byId.put(customer.id(), customer);
        return customer;
    }

    @Override
    public Optional<Customer> findById(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<Customer> findByEmail(EmailAddress email) {
        return byId.values().stream()
                .filter(c -> c.contact().email().equals(email))
                .findFirst();
    }

    @Override
    public Optional<Customer> findByMobile(PhoneNumber mobile) {
        return byId.values().stream()
                .filter(c -> c.contact().mobile().equals(mobile))
                .findFirst();
    }

    @Override
    public boolean existsByEmail(EmailAddress email) {
        return findByEmail(email).isPresent();
    }

    @Override
    public boolean existsByMobile(PhoneNumber mobile) {
        return findByMobile(mobile).isPresent();
    }

    public int count() {
        return byId.size();
    }

    /** Snapshot for transaction tests: the caller restores it to simulate a rollback. */
    public Map<UUID, Customer> snapshot() {
        return new LinkedHashMap<>(byId);
    }

    public void restore(Map<UUID, Customer> snapshot) {
        byId.clear();
        byId.putAll(snapshot);
    }
}
