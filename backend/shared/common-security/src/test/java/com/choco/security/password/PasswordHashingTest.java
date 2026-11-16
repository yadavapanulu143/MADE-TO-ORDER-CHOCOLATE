package com.choco.security.password;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.error.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PasswordHashingTest {

    private static final String RAW = "ChocoLate#2026x";

    @Nested
    @DisplayName("Argon2id (primary)")
    class Argon2id {

        private final Argon2PasswordHasher hasher = new Argon2PasswordHasher();

        @Test
        void produces_phc_encoded_hash_and_never_stores_plaintext() {
            String hash = hasher.hash(RAW);
            assertThat(hash).startsWith("$argon2id$").contains("m=19456,t=2,p=1");
            assertThat(hash).isNotEqualTo(RAW).doesNotContain(RAW);
        }

        @Test
        void verifies_correct_password_and_rejects_wrong_one() {
            String hash = hasher.hash(RAW);
            assertThat(hasher.matches(RAW, hash)).isTrue();
            assertThat(hasher.matches("wrong-password", hash)).isFalse();
        }

        @Test
        void same_password_produces_different_hashes_because_of_random_salt() {
            assertThat(hasher.hash(RAW)).isNotEqualTo(hasher.hash(RAW));
        }

        @Test
        void reports_no_rehash_needed_for_current_parameters() {
            assertThat(hasher.needsRehash(hasher.hash(RAW))).isFalse();
        }

        @Test
        void never_matches_on_null_or_blank_hash() {
            assertThat(hasher.matches(RAW, null)).isFalse();
            assertThat(hasher.matches(RAW, "")).isFalse();
        }
    }

    @Nested
    @DisplayName("BCrypt cost factor 15 (fallback)")
    class Bcrypt {

        private final BCryptPasswordHasher hasher = new BCryptPasswordHasher();

        @Test
        void uses_exactly_cost_factor_15() {
            String hash = hasher.hash(RAW);
            assertThat(hash).startsWith("$2a$15$");
        }

        @Test
        void verifies_correct_password() {
            String hash = hasher.hash(RAW);
            assertThat(hasher.matches(RAW, hash)).isTrue();
            assertThat(hasher.matches("nope", hash)).isFalse();
        }

        @Test
        void refuses_a_cost_factor_outside_the_safe_range() {
            assertThatThrownBy(() -> new BCryptPasswordHasher(9))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new BCryptPasswordHasher(18))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("algorithm migration")
    class Migration {

        @Test
        void verifies_legacy_bcrypt_hash_and_flags_it_for_upgrade() {
            var bcrypt = new BCryptPasswordHasher();
            var delegating = new DelegatingPasswordHasher(
                    new Argon2PasswordHasher(), java.util.List.of(new Argon2PasswordHasher(), bcrypt));

            String legacyHash = bcrypt.hash(RAW);

            assertThat(delegating.matches(RAW, legacyHash)).isTrue();
            assertThat(delegating.needsRehash(legacyHash)).isTrue();
            assertThat(delegating.hash(RAW)).startsWith("$argon2id$");
            assertThat(delegating.needsRehash(delegating.hash(RAW))).isFalse();
        }
    }

    @Nested
    @DisplayName("password policy")
    class Policy {

        @Test
        void accepts_a_reasonably_strong_password() {
            PasswordPolicy.validate("ChocoLate#2026x");
        }

        @Test
        void rejects_too_short() {
            assertThatThrownBy(() -> PasswordPolicy.validate("Ch0co"))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void rejects_letters_only_and_digits_only() {
            assertThatThrownBy(() -> PasswordPolicy.validate("abcdefghijkl")).isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> PasswordPolicy.validate("123456789012")).isInstanceOf(ValidationException.class);
        }

        @Test
        void rejects_common_passwords() {
            assertThatThrownBy(() -> PasswordPolicy.validate("password123"))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void rejects_empty() {
            assertThatThrownBy(() -> PasswordPolicy.validate(null)).isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> PasswordPolicy.validate("   ")).isInstanceOf(ValidationException.class);
        }
    }

    @Test
    void factory_rejects_unknown_algorithm() {
        assertThatThrownBy(() -> PasswordHasherFactory.create("md5"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported password hashing algorithm");
    }
}
