package com.choco.identity.adapter.out.notification;

import com.choco.identity.application.port.AccountNotificationPort;
import com.choco.identity.application.port.OtpDeliveryPort;
import com.choco.identity.domain.customer.VerificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Delivers OTPs and account messages by email and SMS.
 *
 * <p>Writing a verification code to the log lets a developer finish registration without a mail
 * server, but it is a genuine exposure: anyone who can read the log can use the code, and logs are
 * copied, shipped to aggregators and retained far more freely than the database. It is therefore
 * off unless explicitly switched on, and both the shipped configuration and the fallback below
 * default to {@code false}. The fallback matters on its own: a missing property must fail closed,
 * so the safe value is the one that takes effect when nobody has configured anything.
 *
 * <p>SMS is behind an interface rather than a vendor SDK because no provider is selected yet. The
 * current implementation logs, and the requirement to configure a provider is recorded in the
 * deployment documentation instead of being silently skipped.
 */
@Component
class NotificationAdapter implements OtpDeliveryPort, AccountNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(NotificationAdapter.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final boolean logOtp;
    private final SmsSender smsSender;

    NotificationAdapter(JavaMailSender mailSender,
                        @Value("${choco.notifications.from:noreply@choco.example}") String fromAddress,
                        @Value("${choco.notifications.log-otp:false}") boolean logOtp,
                        SmsSender smsSender) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.logOtp = logOtp;
        this.smsSender = smsSender;
        if (logOtp) {
            // Announced once at startup rather than only alongside each code, so an environment that
            // was never meant to have this on is visible in the boot log rather than buried among
            // per-request lines.
            log.warn("Verification codes will be written to this log. This is a development-only "
                    + "setting and must be off anywhere a real customer can register. "
                    + "Unset CHOCO_LOG_OTP, or set choco.notifications.log-otp=false.");
        }
    }

    @Override
    public void deliver(VerificationChannel channel, String destination, String code, String fullName) {
        if (logOtp) {
            log.warn("OTP delivery is in development mode: a verification code was written to the "
                    + "log for destination {} on channel {}. Set choco.notifications.log-otp=false "
                    + "outside development.", mask(destination), channel);
        }

        if (channel == VerificationChannel.EMAIL) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(destination);
            message.setSubject("Your chocolate order verification code");
            message.setText("Hello " + fullName + ",\n\n"
                    + "Your verification code is " + code + ".\n"
                    + "It expires in 5 minutes. Do not share it with anyone.\n\n"
                    + "If you did not request this, you can ignore this message.\n");
            deliverQuietly(message, destination);
            if (logOtp) {
                log.info("Verification code for {} (email) is {}", mask(destination), code);
            }
        } else {
            smsSender.send(destination, "Your chocolate order verification code is " + code
                    + ". It expires in 5 minutes.");
            if (logOtp) {
                log.info("Verification code for {} (sms) is {}", mask(destination), code);
            }
        }
    }

    /**
     * Sends a message without letting a transport failure fail the caller's business operation.
     *
     * <p>Registration already persisted the account and the OTP challenge by the time this runs. If a
     * mail outage propagated, the whole transaction would roll back and the customer would be unable
     * to register until the mail server recovered, for a reason unrelated to their account. The
     * challenge remains valid, and the customer can request a resend once mail is reachable, so
     * failing the operation would trade a recoverable delivery problem for an unrecoverable one.
     *
     * <p>The failure is logged at error level with the destination masked, because a silent failure
     * here is worse than the original problem: nobody would know a customer is waiting for a code.
     */
    private void deliverQuietly(SimpleMailMessage message, String destination) {
        try {
            mailSender.send(message);
        } catch (MailException ex) {
            log.error("Could not send mail to {}. The account and verification challenge are still "
                    + "recorded; the customer can request a resend.", mask(destination), ex);
        }
    }

    @Override
    public void welcome(String email, String fullName) {
        send(email, "Welcome to our chocolate shop",
                "Hello " + fullName + ",\n\nYour account is verified. Chocolates are made to order, "
                        + "so we begin preparation once your payment is confirmed.\n");
    }

    @Override
    public void passwordChanged(String email, String fullName) {
        send(email, "Your password was changed",
                "Hello " + fullName + ",\n\nYour password was changed and all other sessions were "
                        + "signed out. If this was not you, reset your password immediately.\n");
    }

    @Override
    public void passwordResetRequested(String email, String fullName, String resetToken) {
        // The token is placed in a link the frontend owns; the API never returns it directly.
        send(email, "Reset your password",
                "Hello " + fullName + ",\n\nUse this link to reset your password:\n"
                        + "https://choco.example/reset-password?token=" + resetToken + "\n\n"
                        + "The link expires in 30 minutes. If you did not request this, ignore this message.\n");
    }

    private void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        deliverQuietly(message, to);
    }

    /** Keeps the tail of an identifier for correlation without printing the whole thing. */
    private String mask(String value) {
        if (value == null || value.length() < 4) {
            return "****";
        }
        return "****" + value.substring(value.length() - 4);
    }
}
