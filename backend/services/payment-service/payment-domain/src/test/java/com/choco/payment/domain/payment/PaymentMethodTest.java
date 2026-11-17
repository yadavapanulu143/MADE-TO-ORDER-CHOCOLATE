package com.choco.payment.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

@DisplayName("Payment method classification")
class PaymentMethodTest {

    @ParameterizedTest
    @CsvSource({
            "upi,UPI",
            "card,CARD",
            "credit_card,CARD",
            "debit_card,CARD",
            "netbanking,NETBANKING",
            "wallet,WALLET",
            "emi,EMI",
            "paylater,PAY_LATER"
    })
    @DisplayName("maps a provider method string onto a coarse classification")
    void mapsProviderMethod(String provider, PaymentMethod expected) {
        assertThat(PaymentMethod.fromProvider(provider)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("defaults to unknown rather than guessing")
    void defaultsToUnknown(String provider) {
        assertThat(PaymentMethod.fromProvider(provider)).isEqualTo(PaymentMethod.UNKNOWN);
    }

    @Test
    @DisplayName("an unrecognised method is unknown, never a silent card classification")
    void unrecognisedIsUnknown() {
        assertThat(PaymentMethod.fromProvider("some_new_method")).isEqualTo(PaymentMethod.UNKNOWN);
    }
}
