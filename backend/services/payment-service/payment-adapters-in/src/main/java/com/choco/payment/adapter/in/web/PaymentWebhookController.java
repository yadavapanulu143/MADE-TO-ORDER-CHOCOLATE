package com.choco.payment.adapter.in.web;

import com.choco.payment.application.service.PaymentWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives the payment provider's webhooks.
 *
 * <p>This path is reachable without a token — the provider has none — so it is listed as a public
 * path in this service's configuration. Its only protection is the signature over the raw body, which
 * is why the body is taken as a {@code String} and passed on unchanged: re-serialising the parsed JSON
 * would alter whitespace and key order and the signature would never match, so every event would be
 * rejected. The signature is checked before anything is applied.
 *
 * <p>Always answers 2xx once the signature is verified, even for an event this service does not act
 * on. The provider retries any non-2xx indefinitely, so returning an error for an event about an
 * unknown payment would produce a permanent retry storm for something that will never resolve.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
@Tag(name = "Payment webhooks", description = "Provider callbacks, verified by signature")
class PaymentWebhookController {

    /** The provider's signature header. */
    private static final String SIGNATURE_HEADER = "X-Razorpay-Signature";

    private final PaymentWebhookService webhookService;

    PaymentWebhookController(PaymentWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/razorpay")
    @Operation(summary = "Verify and apply a provider webhook")
    ResponseEntity<Void> receive(@RequestBody String rawBody,
                                 @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature) {
        webhookService.handle(rawBody, signature);
        return ResponseEntity.ok().build();
    }
}
