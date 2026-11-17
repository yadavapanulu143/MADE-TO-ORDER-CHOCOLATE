package com.choco.notification.adapter.out.delivery;

import com.choco.notification.application.port.MessageDeliveryException;
import com.choco.notification.application.port.MessageSender;
import com.choco.notification.application.port.OutboundMessage;
import com.choco.notification.domain.notification.NotificationChannel;
import org.springframework.stereotype.Component;

/**
 * Routes a message to its channel's transport.
 *
 * <p>The application layer depends on {@link MessageSender} and knows only a channel; this adapter is
 * the single place that maps a channel to a concrete transport. Adding a push channel later is a case
 * here and a new sender, with no change to any use case.
 *
 * <p>The channel is checked exhaustively rather than with a default, so a new channel added to the
 * enum is a compile error here instead of a message that silently goes nowhere.
 */
@Component
class ChannelMessageSender implements MessageSender {

    private final SmtpEmailSender emailSender;
    private final HttpSmsSender smsSender;

    ChannelMessageSender(SmtpEmailSender emailSender, HttpSmsSender smsSender) {
        this.emailSender = emailSender;
        this.smsSender = smsSender;
    }

    @Override
    public void send(OutboundMessage message) {
        switch (message.channel()) {
            case EMAIL -> emailSender.send(message);
            case SMS -> smsSender.send(message);
            default -> throw new MessageDeliveryException(
                    "No transport is configured for channel " + message.channel());
        }
    }
}
