package com.choco.fulfilment.adapter.in.web;

import com.choco.core.domain.Pincode;
import com.choco.fulfilment.application.dto.ServiceabilityQuoteView;
import com.choco.fulfilment.application.service.DeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The serviceability answer the order service's checkout depends on.
 *
 * <p>Called by the order service while the customer is choosing an address, forwarding the customer's
 * own token. It is not admin-only: a signed-in customer may ask whether their PIN code is covered, and
 * the checkout needs the same answer. It exposes only whether the seller delivers and the charge —
 * never another customer's data, so it needs no ownership scoping.
 *
 * <p>A PIN code the seller does not cover is answered with {@code serviceable: false} and a reason,
 * not an error. The checkout turns that into a message for the customer; a failure status would instead
 * look like the platform being broken.
 */
@RestController
@RequestMapping("/api/v1/serviceability")
@Tag(name = "Serviceability", description = "Whether the seller delivers to a PIN code")
class ServiceabilityController {

    private final DeliveryService deliveryService;

    ServiceabilityController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @GetMapping("/{pincode}")
    @Operation(summary = "Whether the seller delivers to a PIN code, and the delivery charge")
    ResponseEntity<ServiceabilityQuoteView> quote(@PathVariable String pincode) {
        return ResponseEntity.ok(deliveryService.quote(Pincode.of(pincode)));
    }
}
