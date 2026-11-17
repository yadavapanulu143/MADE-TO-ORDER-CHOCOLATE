package com.choco.order.application.support;

import com.choco.core.domain.Money;
import com.choco.core.domain.Pincode;
import com.choco.order.application.port.ServiceabilityPort;
import java.util.HashMap;
import java.util.Map;

/** Seller serviceability settings stand-in: a set of deliverable PIN codes with one charge each. */
public class StubServiceability implements ServiceabilityPort {

    private final Map<String, Money> charges = new HashMap<>();

    public StubServiceability deliverable(String pincode, Money charge) {
        charges.put(pincode, charge);
        return this;
    }

    @Override
    public Quote quoteFor(Pincode pincode) {
        Money charge = charges.get(pincode.value());
        if (charge == null) {
            return Quote.notServiceable("We do not deliver to PIN code " + pincode.value() + " yet");
        }
        return new Quote(true, charge, null);
    }
}
