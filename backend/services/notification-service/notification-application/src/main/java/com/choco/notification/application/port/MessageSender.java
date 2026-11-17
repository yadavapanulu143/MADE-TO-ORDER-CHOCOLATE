package com.choco.notification.application.port;

/**
 * Delivers a rendered message on its channel.
 *
 * <p>An outbound port so the use case never learns whether email goes out over SMTP or SMS over an
 * HTTP gateway. A transport failure is signalled by throwing a {@link MessageDeliveryException}; the
 * caller turns that into a recorded failed attempt rather than a business failure, because the order
 * or payment it is telling the customer about has already happened and must not be undone by a mail
 * server being down.
 */
public interface MessageSender {

    void send(OutboundMessage message);
}
