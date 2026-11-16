package com.choco.identity.adapter.out.persistence;

import com.choco.core.domain.PhoneNumber;
import com.choco.core.domain.Pincode;
import com.choco.identity.domain.address.Address;
import com.choco.identity.domain.customer.ContactDetails;
import com.choco.identity.domain.customer.Customer;
import com.choco.identity.domain.customer.VerificationChannel;
import com.choco.identity.domain.otp.OtpChallenge;
import java.util.HashSet;
import java.util.Set;

/** Translates between the persistence entities and the domain aggregates. */
final class PersistenceMapper {

    private PersistenceMapper() {
    }

    static CustomerJpaEntity toEntity(Customer customer) {
        CustomerJpaEntity entity = new CustomerJpaEntity();
        entity.setId(customer.id());
        entity.setFullName(customer.contact().fullName());
        entity.setEmail(customer.contact().email().value());
        entity.setMobile(customer.contact().mobile().value());
        entity.setPasswordHash(customer.passwordHash());
        entity.setVerifiedChannels(channelNames(customer));
        entity.setRegisteredAt(customer.registeredAt());
        entity.setUpdatedAt(customer.updatedAt());
        entity.setFailedLoginAttempts(customer.failedLoginAttempts());
        entity.setLockedUntil(customer.lockedUntil());
        entity.setActive(customer.isActive());
        return entity;
    }

    /**
     * Rebuilds the aggregate from a row.
     *
     * <p>Uses {@code rehydrate}, never {@code register}, so loading a customer cannot re-raise a
     * registration event or reset the verification state.
     */
    static Customer toDomain(CustomerJpaEntity entity) {
        Set<VerificationChannel> channels = new HashSet<>();
        for (String name : entity.getVerifiedChannels()) {
            channels.add(VerificationChannel.valueOf(name));
        }
        ContactDetails contact = ContactDetails.of(entity.getFullName(), entity.getEmail(), entity.getMobile());
        return Customer.rehydrate(entity.getId(), contact, entity.getPasswordHash(), channels,
                entity.getRegisteredAt(), entity.getUpdatedAt(), entity.getFailedLoginAttempts(),
                entity.getLockedUntil(), entity.isActive());
    }

    static OtpChallengeJpaEntity toEntity(OtpChallenge challenge) {
        OtpChallengeJpaEntity entity = new OtpChallengeJpaEntity();
        entity.setId(challenge.id());
        entity.setCustomerId(challenge.customerId());
        entity.setChannel(challenge.channel());
        entity.setDestination(challenge.destination());
        entity.setCodeHash(challenge.codeHash());
        entity.setCreatedAt(challenge.createdAt());
        entity.setExpiresAt(challenge.expiresAt());
        entity.setAttempts(challenge.attempts());
        entity.setResendCount(challenge.resendCount());
        entity.setConsumedAt(challenge.consumedAt());
        return entity;
    }

    static OtpChallenge toDomain(OtpChallengeJpaEntity entity) {
        return OtpChallenge.rehydrate(entity.getId(), entity.getCustomerId(), entity.getChannel(),
                entity.getDestination(), entity.getCodeHash(), entity.getCreatedAt(), entity.getExpiresAt(),
                entity.getAttempts(), entity.getResendCount(), entity.getConsumedAt());
    }

    static AddressJpaEntity toEntity(Address address) {
        AddressJpaEntity entity = new AddressJpaEntity();
        entity.setId(address.id());
        entity.setCustomerId(address.customerId());
        entity.setRecipientName(address.recipientName());
        entity.setMobile(address.mobile().value());
        entity.setLine1(address.line1());
        entity.setLine2(address.line2());
        entity.setCity(address.city());
        entity.setState(address.state());
        entity.setPincode(address.pincode().value());
        entity.setDeliveryInstructions(address.deliveryInstructions());
        entity.setDefault(address.isDefault());
        entity.setCreatedAt(address.createdAt());
        entity.setUpdatedAt(address.updatedAt());
        return entity;
    }

    static Address toDomain(AddressJpaEntity entity) {
        return Address.rehydrate(entity.getId(), entity.getCustomerId(), entity.getRecipientName(),
                PhoneNumber.of(entity.getMobile()), entity.getLine1(), entity.getLine2(),
                entity.getCity(), entity.getState(), Pincode.of(entity.getPincode()),
                entity.getDeliveryInstructions(), entity.isDefault(), entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private static Set<String> channelNames(Customer customer) {
        Set<String> names = new HashSet<>();
        for (VerificationChannel channel : customer.verifiedChannels()) {
            names.add(channel.name());
        }
        return names;
    }
}
