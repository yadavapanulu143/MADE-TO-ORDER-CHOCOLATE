package com.choco.payment.adapter.in.web;

import jakarta.validation.constraints.NotBlank;

/**
 * The provider's checkout response, as posted by the storefront.
 *
 * <p>None of it is trusted. It is enough to locate the payment and to check the signature; the
 * authoritative state is fetched from the provider afterwards.
 */
record VerifyPaymentRequest(
        @NotBlank String providerOrderId,
        @NotBlank String providerPaymentId,
        @NotBlank String signature) {
}
