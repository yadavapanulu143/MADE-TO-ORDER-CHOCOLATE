package com.choco.identity.domain.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.domain.DomainEvent;
import com.choco.core.domain.EmailAddress;
import com.choco.core.domain.PhoneNumber;
import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerTest {

    private static final Instant NOW = Instant.parse("2026-03-01T09:00:00Z");
    private static final ContactDetails CONTACT =
            ContactDetails.of("Asha Menon", "asha@example.com", "+919876543210");

    private static Customer newCustomer() {
        return Customer.register(UUID.randomUUID(), CONTACT, "$argon2id$hash", NOW);
    }

    @Test
    void registration_starts_unverified_and_raises_registered_event() {
        Customer customer = newCustomer();

        assertThat(customer.fullyVerified()).isFalse();
        assertThat(customer.isVerified(VerificationChannel.EMAIL)).isFalse();
        assertThat(customer.isVerified(VerificationChannel.MOBILE)).isFalse();

        assertThat(customer.peekDomainEvents())
                .extracting(DomainEvent::eventType)
                .containsExactly("customer.registered");
    }

    @Test
    void customer_is_verified_only_after_both_channels() {
        Customer customer = newCustomer();

        customer.verify(VerificationChannel.EMAIL, NOW);

        assertThat(customer.fullyVerified())
                .as("email alone must not be enough")
                .isFalse();

        customer.verify(VerificationChannel.MOBILE, NOW);

        assertThat(customer.fullyVerified()).isTrue();
    }

    @Test
    void re_verifying_a_channel_does_not_raise_a_second_event() {
        Customer customer = newCustomer();

        customer.verify(VerificationChannel.EMAIL, NOW);
        customer.verify(VerificationChannel.EMAIL, NOW);

        assertThat(customer.peekDomainEvents())
                .filteredOn(e -> e.eventType().equals("customer.verified"))
                .as("a repeated verification link must not trigger a duplicate welcome message")
                .hasSize(1);
    }

    @Test
    void verified_event_reports_whether_the_account_is_now_fully_verified() {
        Customer customer = newCustomer();

        customer.verify(VerificationChannel.EMAIL, NOW);
        customer.verify(VerificationChannel.MOBILE, NOW);

        assertThat(customer.peekDomainEvents())
                .filteredOn(e -> e instanceof com.choco.identity.domain.event.CustomerVerified)
                .extracting(e -> ((com.choco.identity.domain.event.CustomerVerified) e).fullyVerified())
                .containsExactly(false, true);
    }

    @Test
    void account_locks_after_the_configured_number_of_failed_logins() {
        Customer customer = newCustomer();

        for (int i = 0; i < Customer.MAX_FAILED_LOGIN_ATTEMPTS - 1; i++) {
            customer.recordFailedLogin(NOW);
        }
        assertThat(customer.isLocked(NOW)).as("must not lock before the threshold").isFalse();

        customer.recordFailedLogin(NOW);

        assertThat(customer.isLocked(NOW)).isTrue();
        assertThatThrownBy(() -> customer.assertUsable(NOW))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).errorCode())
                .isEqualTo(ErrorCode.ACCOUNT_LOCKED);
    }

    @Test
    void lock_expires_after_the_lockout_window() {
        Customer customer = newCustomer();
        for (int i = 0; i < Customer.MAX_FAILED_LOGIN_ATTEMPTS; i++) {
            customer.recordFailedLogin(NOW);
        }

        Instant muchLater = NOW.plusSeconds(16 * 60);

        assertThat(customer.isLocked(muchLater)).isFalse();
        customer.assertUsable(muchLater);
    }

    @Test
    void successful_login_clears_the_failure_counter() {
        Customer customer = newCustomer();
        customer.recordFailedLogin(NOW);
        customer.recordFailedLogin(NOW);

        customer.recordSuccessfulLogin(NOW);

        assertThat(customer.failedLoginAttempts()).isZero();
        assertThat(customer.isLocked(NOW)).isFalse();
    }

    @Test
    void changing_the_password_clears_a_lockout_and_raises_an_event() {
        Customer customer = newCustomer();
        for (int i = 0; i < Customer.MAX_FAILED_LOGIN_ATTEMPTS; i++) {
            customer.recordFailedLogin(NOW);
        }
        customer.pullDomainEvents();

        customer.changePassword("$argon2id$new", NOW);

        assertThat(customer.isLocked(NOW))
                .as("proving control of the account should end the lockout")
                .isFalse();
        assertThat(customer.passwordHash()).isEqualTo("$argon2id$new");
        assertThat(customer.peekDomainEvents())
                .extracting(DomainEvent::eventType)
                .containsExactly("customer.password-changed");
    }

    @Test
    void password_change_event_carries_no_credential_material() {
        Customer customer = newCustomer();
        customer.pullDomainEvents();

        customer.changePassword("$argon2id$secret-hash", NOW);

        assertThat(customer.peekDomainEvents().getFirst().toString())
                .as("no event payload may leak the hash")
                .doesNotContain("secret-hash");
    }

    @Test
    void deactivated_account_is_not_usable() {
        Customer customer = newCustomer();
        customer.deactivate(NOW);

        assertThat(customer.isActive()).isFalse();
        assertThatThrownBy(() -> customer.assertUsable(NOW))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).errorCode())
                .isEqualTo(ErrorCode.ACCOUNT_LOCKED);
    }

    @Test
    void rehydrate_preserves_state_without_raising_events() {
        UUID id = UUID.randomUUID();
        Customer customer = Customer.rehydrate(id, CONTACT, "$argon2id$h",
                Set.of(VerificationChannel.EMAIL), NOW, NOW, 3, null, true);

        assertThat(customer.id()).isEqualTo(id);
        assertThat(customer.isVerified(VerificationChannel.EMAIL)).isTrue();
        assertThat(customer.isVerified(VerificationChannel.MOBILE)).isFalse();
        assertThat(customer.failedLoginAttempts()).isEqualTo(3);
        assertThat(customer.peekDomainEvents())
                .as("loading from the database must not re-publish history")
                .isEmpty();
    }

    @Test
    void contact_details_reject_blank_names() {
        assertThatThrownBy(() -> ContactDetails.of("   ", "a@b.com", "+919876543210"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Full name");
    }

    @Test
    void contact_details_collapse_extra_whitespace_in_names() {
        ContactDetails details = ContactDetails.of("  Asha   Menon  ", "a@b.com", "+919876543210");
        assertThat(details.fullName()).isEqualTo("Asha Menon");
    }

    @Test
    void masked_forms_hide_the_identifiers() {
        assertThat(CONTACT.maskedEmail()).isEqualTo("a***a@example.com");
        assertThat(CONTACT.maskedMobile()).isEqualTo("******3210");
        assertThat(CONTACT.maskedEmail()).doesNotContain("asha@");
    }

    @Test
    void changing_the_full_name_keeps_verification() {
        Customer customer = newCustomer();
        customer.verify(VerificationChannel.EMAIL, NOW);
        customer.verify(VerificationChannel.MOBILE, NOW);

        customer.changeFullName("Asha R Menon", NOW);

        assertThat(customer.contact().fullName()).isEqualTo("Asha R Menon");
        assertThat(customer.fullyVerified())
                .as("a display-name change must not disturb verification")
                .isTrue();
    }

    @Test
    void changing_the_email_clears_email_verification_only() {
        Customer customer = newCustomer();
        customer.verify(VerificationChannel.EMAIL, NOW);
        customer.verify(VerificationChannel.MOBILE, NOW);

        customer.changeEmail(EmailAddress.of("new@example.com"), NOW);

        assertThat(customer.contact().email().value()).isEqualTo("new@example.com");
        assertThat(customer.isVerified(VerificationChannel.EMAIL))
                .as("the new address has not been proven")
                .isFalse();
        assertThat(customer.isVerified(VerificationChannel.MOBILE))
                .as("changing the email must not touch the mobile channel")
                .isTrue();
        assertThat(customer.fullyVerified()).isFalse();
    }

    @Test
    void changing_the_mobile_clears_mobile_verification_only() {
        Customer customer = newCustomer();
        customer.verify(VerificationChannel.EMAIL, NOW);
        customer.verify(VerificationChannel.MOBILE, NOW);

        customer.changeMobile(PhoneNumber.of("9123456780"), NOW);

        assertThat(customer.contact().mobile().value()).isEqualTo("9123456780");
        assertThat(customer.isVerified(VerificationChannel.MOBILE)).isFalse();
        assertThat(customer.isVerified(VerificationChannel.EMAIL)).isTrue();
    }

    @Test
    void changing_the_email_keeps_the_password_hash() {
        Customer customer = newCustomer();
        customer.changeEmail(EmailAddress.of("new@example.com"), NOW);

        assertThat(customer.passwordHash())
                .as("a contact change must never reset credentials")
                .isEqualTo("$argon2id$hash");
    }

    @Test
    void changing_the_full_name_rejects_a_blank_value() {
        Customer customer = newCustomer();

        assertThatThrownBy(() -> customer.changeFullName("  ", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Full name");
    }
}
