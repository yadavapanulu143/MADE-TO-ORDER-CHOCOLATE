package com.choco.order.adapter.out.client;

import com.choco.core.domain.EmailAddress;
import com.choco.core.domain.PhoneNumber;
import com.choco.core.domain.Pincode;
import com.choco.order.application.port.CustomerPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Reads a customer's own account and addresses over HTTP.
 *
 * <p>The customer's own token is forwarded, so the identity service applies its ownership rule to the
 * call: this client can only ever read the account the token belongs to. That is why the profile
 * endpoint is addressed by the caller's own id — a bug here cannot be turned into reading another
 * customer's data, because identity would refuse it.
 *
 * <p>The profile response already carries the saved addresses, so one call answers both "who is this
 * customer" and "what is this address", and the address is checked to belong to the customer before it
 * is returned.
 */
@Component
class CustomerClient implements CustomerPort {

    private final RestClient client;

    CustomerClient(DownstreamProperties properties, DownstreamTokenProvider tokens) {
        this.client = DownstreamClients.build(properties.getIdentity(), properties.getConnectTimeout(),
                properties.getReadTimeout(), tokens);
    }

    @Override
    public Optional<CustomerSnapshot> findCustomer(UUID customerId) {
        return fetchProfile(customerId).map(view -> new CustomerSnapshot(
                view.id(),
                view.fullName(),
                EmailAddress.of(view.email()),
                PhoneNumber.of(view.mobile()),
                view.emailVerified(),
                view.mobileVerified()));
    }

    @Override
    public Optional<AddressSnapshot> findAddress(UUID customerId, UUID addressId) {
        if (addressId == null) {
            return Optional.empty();
        }
        return fetchProfile(customerId)
                .flatMap(profile -> profile.addresses().stream()
                        .filter(address -> addressId.equals(address.id()))
                        .findFirst())
                .map(address -> new AddressSnapshot(
                        address.id(),
                        customerId,
                        address.recipientName(),
                        PhoneNumber.of(address.mobile()),
                        address.line1(),
                        address.line2(),
                        address.city(),
                        address.state(),
                        Pincode.of(address.pincode()),
                        address.deliveryInstructions()));
    }

    /**
     * Fetches the profile, treating a {@code 404} as "no such customer" and any other failure as a
     * dependency outage.
     */
    private Optional<CustomerProfileResponse> fetchProfile(UUID customerId) {
        try {
            CustomerProfileResponse body = client.get()
                    .uri("/api/v1/customers/{customerId}", customerId)
                    .retrieve()
                    .body(CustomerProfileResponse.class);
            return Optional.ofNullable(body);
        } catch (RestClientResponseException e) {
            if (DownstreamClients.isNotFound(e)) {
                return Optional.empty();
            }
            throw DownstreamClients.unwrap(e, "identity");
        } catch (org.springframework.web.client.ResourceAccessException e) {
            throw DownstreamClients.unwrap(e, "identity");
        }
    }

    /**
     * The wire shape of a customer profile.
     *
     * <p>A local record, not the identity service's own class: the order service depends on the
     * published JSON contract, not on another service's Java types. Unknown fields are ignored, so
     * identity can add a field without breaking this client.
     */
    record CustomerProfileResponse(
            UUID id,
            String fullName,
            String email,
            String mobile,
            boolean emailVerified,
            boolean mobileVerified,
            List<AddressResponse> addresses) {
    }

    record AddressResponse(
            UUID id,
            String recipientName,
            String mobile,
            String line1,
            String line2,
            String city,
            String state,
            String pincode,
            String deliveryInstructions) {
    }
}
