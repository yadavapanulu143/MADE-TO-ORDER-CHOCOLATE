package com.choco.identity.application.fake;

import com.choco.core.domain.EmailAddress;
import com.choco.core.domain.PhoneNumber;
import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.identity.domain.customer.Customer;
import com.choco.identity.domain.port.CustomerRepository;
import java.util.LinkedHashMap;
import java.util.List;
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

    @Override
    public PageResult<Customer> search(String search, PageQuery page) {
        String term = search == null ? "" : search.trim().toLowerCase(java.util.Locale.ROOT);
        List<Customer> matches = byId.values().stream()
                .filter(c -> term.isEmpty()
                        || c.contact().fullName().toLowerCase(java.util.Locale.ROOT).contains(term)
                        || c.contact().email().value().toLowerCase(java.util.Locale.ROOT).contains(term)
                        || c.contact().mobile().value().contains(term))
                .sorted(java.util.Comparator.comparing(Customer::registeredAt).reversed()
                        .thenComparing(Customer::id))
                .toList();
        int from = Math.min(page.offset(), matches.size());
        int to = Math.min(from + page.size(), matches.size());
        return PageResult.of(matches.subList(from, to), page.page(), page.size(), matches.size());
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
