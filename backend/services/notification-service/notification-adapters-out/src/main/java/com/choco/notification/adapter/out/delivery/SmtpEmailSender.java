package com.choco.notification.adapter.out.delivery;

import com.choco.notification.application.port.MessageDeliveryException;
import com.choco.notification.application.port.MessageSender;
import com.choco.notification.application.port.OutboundMessage;
import com.choco.notification.domain.notification.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Sends email over SMTP through Spring's {@link JavaMailSender}.
 *
 * <p>A multipart message with both a plain-text and an HTML part is sent, so a client that shows either
 * one gets the same information. The text part is not optional: it is what a plain client, a screen
 * reader and a spam filter see, and an HTML-only message degrades badly for all three.
 *
 * <p><b>A transport failure is translated, never swallowed.</b> Unlike the identity service, where a
 * mail outage must not fail a registration that has already committed, here the send <em>is</em> the
 * operation. The failure is turned into a {@link MessageDeliveryException} carrying a short,
 * provider-neutral summary — the caller records it and retries — and the raw exception is logged
 * locally but never stored, because a mail server error can echo the recipient address.
 */
@Component
class SmtpEmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    SmtpEmailSender(JavaMailSender mailSender,
                    @Value("${choco.notifications.from:noreply@choco.example}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    void send(OutboundMessage message) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(message.recipient());
            helper.setSubject(message.subject());
            helper.setText(message.textBody(), message.htmlBody());
            mailSender.send(mime);
        } catch (MailException | jakarta.mail.MessagingException e) {
            // Logged with the address masked; the exception message is not propagated upward because it
            // can contain the recipient address, and the stored failure must stay provider-neutral.
            log.error("Could not send email to {}: {}", mask(message.recipient()), e.getMessage());
            throw new MessageDeliveryException("Email delivery failed: " + summarise(e));
        }
    }

    /** A short class of failure, never the provider's own text, so nothing sensitive is stored. */
    private static String summarise(Exception e) {
        String name = e.getClass().getSimpleName();
        return switch (name) {
            case "MailSendException" -> "the mail server rejected or could not accept the message";
            case "MailAuthenticationException" -> "the mail server rejected the credentials";
            case "MailParseException" -> "the message could not be built";
            default -> "the mail transport failed";
        };
    }

    private static String mask(String value) {
        if (value == null || value.length() < 4) {
            return "****";
        }
        int at = value.indexOf('@');
        if (at <= 1) {
            return "****";
        }
        return value.charAt(0) + "***" + value.substring(at);
    }
}
