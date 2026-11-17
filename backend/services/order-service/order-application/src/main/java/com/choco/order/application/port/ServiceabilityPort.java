package com.choco.order.application.port;

import com.choco.core.domain.Money;
import com.choco.core.domain.Pincode;

/**
 * Whether the seller delivers to a PIN code, and what that costs.
 *
 * <p>Delivery is managed by the seller, so serviceability and the charge are business settings the
 * seller maintains, not a courier lookup. The port is separate from the catalogue and identity ports
 * because it is a different question with a different owner, and because a future change from a static
 * PIN-code list to a distance-based rule should not ripple into checkout.
 */
public interface ServiceabilityPort {

    record Quote(boolean serviceable, Money deliveryCharge, String reason) {

        public static Quote notServiceable(String reason) {
            return new Quote(false, Money.inr(0), reason);
        }
    }

    /** Resolves whether the PIN code is deliverable and, if so, the charge to quote. */
    Quote quoteFor(Pincode pincode);
}
