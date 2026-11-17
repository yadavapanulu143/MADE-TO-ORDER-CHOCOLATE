package com.choco.notification.application.port;

/**
 * A message could not be handed to its provider.
 *
 * <p>A dedicated type rather than a generic runtime exception so the delivery loop can tell "the
 * provider rejected or was unreachable" — which is retryable and expected — apart from a programming
 * error, which is not. The message is a short, provider-neutral summary: it is stored on the
 * notification record, so it must not carry the recipient address or a raw provider response body.
 */
public class MessageDeliveryException extends RuntimeException {

    public MessageDeliveryException(String message) {
        super(message);
    }

    public MessageDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
