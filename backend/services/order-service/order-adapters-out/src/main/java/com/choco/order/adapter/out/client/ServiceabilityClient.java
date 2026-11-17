package com.choco.order.adapter.out.client;

import com.choco.core.domain.Money;
import com.choco.core.domain.Pincode;
import com.choco.order.application.port.ServiceabilityPort;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Asks the fulfilment service whether the seller delivers to a PIN code, and at what charge.
 *
 * <p>Delivery is seller-managed, so serviceability is a business setting the seller maintains in the
 * fulfilment service, not a courier lookup. Keeping it behind this call means the checkout does not
 * care how the rule is expressed — a list of PIN codes today, a distance band tomorrow.
 *
 * <p>A PIN code the fulfilment service does not know is reported as not serviceable, which is the safe
 * default: the checkout refuses rather than accepting an order the seller cannot deliver. A failure to
 * reach the service is treated differently — as a dependency outage — because "we could not check" must
 * not be silently turned into "we do not deliver", which would look like a policy decision.
 */
@Component
class ServiceabilityClient implements ServiceabilityPort {

    private final RestClient client;

    ServiceabilityClient(DownstreamProperties properties, DownstreamTokenProvider tokens) {
        this.client = DownstreamClients.build(properties.getFulfilment(),
                properties.getConnectTimeout(), properties.getReadTimeout(), tokens);
    }

    @Override
    public Quote quoteFor(Pincode pincode) {
        ServiceabilityResponse body = DownstreamClients.call("delivery", () ->
                client.get()
                        .uri("/api/v1/serviceability/{pincode}", pincode.value())
                        .retrieve()
                        .body(ServiceabilityResponse.class));

        if (body == null || !body.serviceable()) {
            String reason = body == null || body.reason() == null
                    ? "We do not deliver to PIN code " + pincode.value() + " yet"
                    : body.reason();
            return Quote.notServiceable(reason);
        }
        return new Quote(true, Money.ofMinorUnits(body.deliveryChargeMinorUnits(),
                java.util.Currency.getInstance(body.currency())), null);
    }

    /**
     * The wire shape of a serviceability answer.
     *
     * <p>A local record, not the fulfilment service's own class, so the order service depends on the
     * published JSON contract rather than another service's Java types.
     */
    record ServiceabilityResponse(
            boolean serviceable,
            long deliveryChargeMinorUnits,
            String currency,
            String reason) {
    }
}
