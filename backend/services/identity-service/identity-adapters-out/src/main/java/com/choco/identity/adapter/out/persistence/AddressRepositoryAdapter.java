package com.choco.identity.adapter.out.persistence;

import com.choco.identity.domain.address.Address;
import com.choco.identity.domain.port.AddressRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** JPA implementation of {@link AddressRepository}. */
@Repository
class AddressRepositoryAdapter implements AddressRepository {

    private final SpringDataAddressRepository delegate;

    AddressRepositoryAdapter(SpringDataAddressRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Address save(Address address) {
        delegate.save(PersistenceMapper.toEntity(address));
        return address;
    }

    @Override
    public List<Address> findByCustomerId(UUID customerId) {
        return delegate.findByCustomerIdOrderByIsDefaultDescUpdatedAtDesc(customerId).stream()
                .map(PersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Address> findByIdAndCustomerId(UUID addressId, UUID customerId) {
        return delegate.findByIdAndCustomerId(addressId, customerId).map(PersistenceMapper::toDomain);
    }

    @Override
    public Optional<Address> findDefaultByCustomerId(UUID customerId) {
        return delegate.findFirstByCustomerIdAndIsDefaultTrue(customerId).map(PersistenceMapper::toDomain);
    }

    @Override
    public long countByCustomerId(UUID customerId) {
        return delegate.countByCustomerId(customerId);
    }

    @Override
    public void delete(Address address) {
        delegate.deleteById(address.id());
    }

    @Override
    public void clearDefaultForCustomer(UUID customerId) {
        delegate.clearDefaultForCustomer(customerId);
    }
}
