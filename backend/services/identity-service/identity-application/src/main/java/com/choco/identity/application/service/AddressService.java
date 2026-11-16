package com.choco.identity.application.service;

import com.choco.core.domain.PhoneNumber;
import com.choco.core.domain.Pincode;
import com.choco.core.error.ConflictException;
import com.choco.core.error.ErrorCode;
import com.choco.core.error.NotFoundException;
import com.choco.core.error.ValidationException;
import com.choco.core.port.Clock;
import com.choco.core.port.IdGenerator;
import com.choco.core.port.TransactionRunner;
import com.choco.identity.application.command.CreateAddressCommand;
import com.choco.identity.application.command.UpdateAddressCommand;
import com.choco.identity.application.dto.AddressView;
import com.choco.identity.application.port.AuditLogPort;
import com.choco.identity.domain.address.Address;
import com.choco.identity.domain.port.AddressRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Manages a customer's delivery addresses.
 *
 * <p>Every method takes the owning {@code customerId} from the caller — which the web adapter reads
 * from the authenticated token, never from the request — and passes it to the repository lookup.
 * Combined with {@link Address#assertOwnedBy}, that means both the query and the mutation are scoped
 * to the caller. A cross-customer attempt fails at the lookup as "not found" rather than reaching the
 * address at all, so the API does not confirm that someone else's address id exists.
 *
 * <p>Default-address handling is transactional: promoting one address clears the flag on the others
 * inside the same unit of work, so a customer can never be left with two defaults or none after a
 * partial failure.
 */
public class AddressService {

    private final AddressRepository addressRepository;
    private final AuditLogPort auditLog;
    private final TransactionRunner transactionRunner;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public AddressService(AddressRepository addressRepository,
                          AuditLogPort auditLog,
                          TransactionRunner transactionRunner,
                          IdGenerator idGenerator,
                          Clock clock) {
        this.addressRepository = addressRepository;
        this.auditLog = auditLog;
        this.transactionRunner = transactionRunner;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    public AddressView add(UUID customerId, CreateAddressCommand command) {
        Instant now = clock.now();

        long existing = addressRepository.countByCustomerId(customerId);
        if (existing >= Address.MAX_PER_CUSTOMER) {
            throw new ConflictException(ErrorCode.ADDRESS_LIMIT_REACHED,
                    "You can save at most " + Address.MAX_PER_CUSTOMER + " addresses");
        }

        Address address = Address.create(idGenerator.nextId(), customerId,
                command.recipientName(), parseMobile(command.mobile()), command.line1(), command.line2(),
                command.city(), command.state(), parsePincode(command.pincode()),
                command.deliveryInstructions(), command.makeDefault(), now);

        // The first address is always the default: a customer with exactly one saved address should
        // not have to nominate it before checkout will accept it.
        if (existing == 0) {
            address.markDefault();
        }

        transactionRunner.execute(() -> {
            if (address.isDefault()) {
                addressRepository.clearDefaultForCustomer(customerId);
            }
            addressRepository.save(address);
        });

        auditLog.record("CUSTOMER", customerId.toString(), "ADDRESS_ADDED", "ADDRESS",
                address.id().toString(), "SUCCESS", null, null);

        return toView(address);
    }

    public List<AddressView> list(UUID customerId) {
        return addressRepository.findByCustomerId(customerId).stream()
                .map(AddressService::toView)
                .toList();
    }

    public AddressView get(UUID customerId, UUID addressId) {
        return toView(requireOwned(customerId, addressId));
    }

    public AddressView update(UUID customerId, UUID addressId, UpdateAddressCommand command) {
        Address address = requireOwned(customerId, addressId);
        Instant now = clock.now();

        transactionRunner.execute(() -> {
            address.update(command.recipientName(), parseMobile(command.mobile()), command.line1(),
                    command.line2(), command.city(), command.state(), parsePincode(command.pincode()),
                    command.deliveryInstructions(), now);
            addressRepository.save(address);
        });

        auditLog.record("CUSTOMER", customerId.toString(), "ADDRESS_UPDATED", "ADDRESS",
                address.id().toString(), "SUCCESS", null, null);

        return toView(address);
    }

    public AddressView makeDefault(UUID customerId, UUID addressId) {
        Address address = requireOwned(customerId, addressId);

        transactionRunner.execute(() -> {
            addressRepository.clearDefaultForCustomer(customerId);
            address.markDefault();
            addressRepository.save(address);
        });

        auditLog.record("CUSTOMER", customerId.toString(), "ADDRESS_DEFAULT_CHANGED", "ADDRESS",
                address.id().toString(), "SUCCESS", null, null);

        return toView(address);
    }

    public void delete(UUID customerId, UUID addressId) {
        Address address = requireOwned(customerId, addressId);
        boolean wasDefault = address.isDefault();

        transactionRunner.execute(() -> {
            addressRepository.delete(address);
            // Deleting the default promotes another address, so the customer keeps a usable default
            // instead of silently having none.
            if (wasDefault) {
                addressRepository.findByCustomerId(customerId).stream().findFirst()
                        .ifPresent(next -> {
                            next.markDefault();
                            addressRepository.save(next);
                        });
            }
        });

        auditLog.record("CUSTOMER", customerId.toString(), "ADDRESS_DELETED", "ADDRESS",
                addressId.toString(), "SUCCESS", null, null);
    }

    /**
     * Loads an address only if the caller owns it.
     *
     * <p>Reports "not found" rather than "forbidden" for a mismatched owner, so the response does not
     * reveal whether the id exists on another account.
     */
    private Address requireOwned(UUID customerId, UUID addressId) {
        return addressRepository.findByIdAndCustomerId(addressId, customerId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ADDRESS_NOT_FOUND, "Address not found"));
    }

    private static PhoneNumber parseMobile(String raw) {
        try {
            return PhoneNumber.of(raw);
        } catch (IllegalArgumentException e) {
            throw new ValidationException(ErrorCode.VALIDATION_FAILED, "Enter a valid 10-digit Indian mobile number");
        }
    }

    private static Pincode parsePincode(String raw) {
        try {
            return Pincode.of(raw);
        } catch (IllegalArgumentException e) {
            throw new ValidationException(ErrorCode.VALIDATION_FAILED, "Enter a valid 6-digit PIN code");
        }
    }

    static AddressView toView(Address address) {
        return new AddressView(
                address.id(),
                address.recipientName(),
                address.mobile().value(),
                address.line1(),
                address.line2(),
                address.city(),
                address.state(),
                address.pincode().value(),
                address.deliveryInstructions(),
                address.isDefault());
    }
}
