package com.choco.identity.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Proves the shipped configuration does not log verification codes by default.
 *
 * <p>This reads the real {@code application.yml} rather than re-stating the property, because the
 * value a deployment actually receives comes from that file. A test that bound the property itself
 * would keep passing while the file defaulted to {@code true}, which is the exact failure it is here
 * to prevent.
 *
 * <p>The default is parsed as text and checked directly. Resolving {@code ${CHOCO_LOG_OTP:false}}
 * would require the property source stack, and the placeholder's fallback is the whole point.
 */
class OtpLoggingDefaultTest {

    private static final String DEFAULT_ON_PLACEHOLDER = "${CHOCO_LOG_OTP:true}";

    @SuppressWarnings("unchecked")
    private Map<String, Object> notificationsSection() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application.yml")) {
            assertThat(in).as("application.yml must be on the test classpath").isNotNull();
            Map<String, Object> root = new Yaml().load(in);
            Map<String, Object> choco = (Map<String, Object>) root.get("choco");
            return (Map<String, Object>) choco.get("notifications");
        } catch (Exception e) {
            throw new IllegalStateException("could not read application.yml", e);
        }
    }

    @Test
    void the_shipped_default_does_not_log_otp() {
        String configured = String.valueOf(notificationsSection().get("log-otp"));

        assertThat(configured)
                .as("an environment that sets nothing must not write verification codes to its log")
                .doesNotContain(DEFAULT_ON_PLACEHOLDER)
                .isEqualTo("${CHOCO_LOG_OTP:false}");
    }

    @Test
    void the_setting_can_still_be_turned_on_explicitly() {
        String configured = String.valueOf(notificationsSection().get("log-otp"));

        assertThat(configured)
                .as("local development against a mail server must remain possible")
                .contains("CHOCO_LOG_OTP");
    }
}
