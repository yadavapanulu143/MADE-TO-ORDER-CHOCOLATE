package com.choco.identity.adapter.out.notification;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.choco.identity.domain.customer.VerificationChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Proves that a verification code only reaches the log when it has been explicitly asked for.
 *
 * <p>The code is captured from the real logger rather than a mock, because the thing under test is
 * exactly what ends up in the log stream. Asserting that a mock was not called would still pass if
 * the code were logged somewhere else.
 */
class NotificationAdapterOtpLoggingTest {

    private static final String CODE = "483920";
    private static final String EMAIL = "buyer@example.com";

    private Logger adapterLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachLogCapture() {
        adapterLogger = (Logger) LoggerFactory.getLogger(NotificationAdapter.class);
        appender = new ListAppender<>();
        appender.start();
        adapterLogger.addAppender(appender);
    }

    @AfterEach
    void detachLogCapture() {
        adapterLogger.detachAppender(appender);
        appender.stop();
    }

    private NotificationAdapter adapter(boolean logOtp) {
        JavaMailSender mailSender = new NoOpMailSender();
        SmsSender smsSender = (number, message) -> { };
        return new NotificationAdapter(mailSender, "noreply@choco.example", logOtp, smsSender);
    }

    private String capturedLog() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .reduce("", (a, b) -> a + "\n" + b);
    }

    @Test
    void with_logging_off_the_code_never_appears_in_the_log() {
        adapter(false).deliver(VerificationChannel.EMAIL, EMAIL, CODE, "Asha");

        assertThat(capturedLog())
                .as("a disabled log-otp setting must not leak the code by any path")
                .doesNotContain(CODE);
    }

    @Test
    void with_logging_off_the_code_never_appears_for_sms_either() {
        adapter(false).deliver(VerificationChannel.MOBILE, "+919812345678", CODE, "Asha");

        assertThat(capturedLog()).doesNotContain(CODE);
    }

    @Test
    void with_logging_on_the_code_is_logged_so_a_developer_can_use_it() {
        adapter(true).deliver(VerificationChannel.EMAIL, EMAIL, CODE, "Asha");

        assertThat(capturedLog())
                .as("the opt-in exists so a developer can register without a mail server")
                .contains(CODE);
    }

    @Test
    void turning_logging_on_is_announced_once_at_startup() {
        String duringConstruction = captureStartupWarnings(true);

        assertThat(duringConstruction)
                .as("an environment that should not have this on must be visible in the boot log")
                .containsIgnoringCase("development-only");
    }

    @Test
    void the_secure_setting_is_not_announced() {
        assertThat(captureStartupWarnings(false)).isEmpty();
    }

    /** Builds the adapter and returns whatever it logged from its constructor. */
    private String captureStartupWarnings(boolean logOtp) {
        ListAppender<ILoggingEvent> startupAppender = new ListAppender<>();
        startupAppender.start();
        adapterLogger.addAppender(startupAppender);
        try {
            adapter(logOtp);
            return startupAppender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (a, b) -> a + "\n" + b);
        } finally {
            adapterLogger.detachAppender(startupAppender);
            startupAppender.stop();
        }
    }

    /**
     * A mail sender that records nothing and sends nothing. The adapter catches mail failures and
     * continues, so an implementation that threw would change the behaviour being measured.
     */
    private static final class NoOpMailSender implements JavaMailSender {
        @Override
        public void send(org.springframework.mail.SimpleMailMessage simpleMessage) {
            // Deliberately does nothing: this test is about logging, not delivery.
        }

        @Override
        public void send(org.springframework.mail.SimpleMailMessage... simpleMessages) {
            // Deliberately does nothing.
        }

        @Override
        public jakarta.mail.internet.MimeMessage createMimeMessage() {
            throw new UnsupportedOperationException("not used by the adapter");
        }

        @Override
        public jakarta.mail.internet.MimeMessage createMimeMessage(java.io.InputStream contentStream) {
            throw new UnsupportedOperationException("not used by the adapter");
        }

        @Override
        public void send(jakarta.mail.internet.MimeMessage... mimeMessages) {
            throw new UnsupportedOperationException("not used by the adapter");
        }
    }

    /** Keeps the level assertion explicit so a future edit cannot silence the warnings under test. */
    @Test
    void the_opt_in_warning_is_at_warn_level() {
        ListAppender<ILoggingEvent> startupAppender = new ListAppender<>();
        startupAppender.start();
        adapterLogger.addAppender(startupAppender);
        try {
            adapter(true);
            assertThat(startupAppender.list).anySatisfy(event ->
                    assertThat(event.getLevel()).isEqualTo(Level.WARN));
        } finally {
            adapterLogger.detachAppender(startupAppender);
            startupAppender.stop();
        }
    }
}
