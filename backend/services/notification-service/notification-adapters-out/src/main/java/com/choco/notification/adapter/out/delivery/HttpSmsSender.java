package com.choco.notification.adapter.out.delivery;

import com.choco.notification.application.port.MessageDeliveryException;
import com.choco.notification.application.port.OutboundMessage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;

/**
 * Sends SMS through a configurable HTTP gateway.
 *
 * <p>No SMS provider has been selected, and hard-coding a vendor would be a guess. Rather than a
 * vendor SDK, this uses the one shape nearly every Indian gateway offers: a POST with the recipient,
 * the message and an API key, at a URL the seller configures. That keeps the provider a deployment
 * decision — MSG91, Twilio or a local gateway — without a code change or a new dependency, and without
 * pretending a specific vendor was chosen.
 *
 * <p>When no gateway URL is configured the sender logs a loud warning and reports success. That is
 * deliberate and it is a development affordance: the alternative is a service that cannot start, or
 * one that silently claims a text was sent. The warning names the missing configuration, so shipping
 * without a gateway is obvious from the first delivery rather than discovered by a customer who never
 * got their code. The message body is not logged.
 *
 * <p>The API key is read from configuration and is never logged or stored on the notification record.
 */
class HttpSmsSender {

    private static final Logger log = LoggerFactory.getLogger(HttpSmsSender.class);

    private final HttpClient http;
    private final String gatewayUrl;
    private final String apiKey;
    private final String senderId;
    private final Duration timeout;

    HttpSmsSender(String gatewayUrl, String apiKey, String senderId, Duration timeout) {
        this.gatewayUrl = gatewayUrl;
        this.apiKey = apiKey;
        this.senderId = senderId;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    boolean isConfigured() {
        return gatewayUrl != null && !gatewayUrl.isBlank();
    }

    void send(OutboundMessage message) {
        if (!isConfigured()) {
            log.warn("No SMS gateway is configured. A message for {} was not sent. Set "
                    + "choco.notifications.sms.gateway-url before going live. Message body withheld "
                    + "from this log.", mask(message.recipient()));
            return;
        }

        String body = buildBody(message);
        HttpRequest request = HttpRequest.newBuilder(URI.create(gatewayUrl))
                .timeout(timeout)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                // The gateway's own body is not stored: it can echo the number back.
                log.error("SMS gateway returned {} for a message to {}", response.statusCode(),
                        mask(message.recipient()));
                throw new MessageDeliveryException("the SMS gateway returned " + response.statusCode());
            }
        } catch (java.io.IOException e) {
            log.error("SMS gateway unreachable for a message to {}: {}", mask(message.recipient()),
                    e.getMessage());
            throw new MessageDeliveryException("the SMS gateway was unreachable");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MessageDeliveryException("the SMS gateway call was interrupted");
        }
    }

    /**
     * Builds the request body.
     *
     * <p>A plain JSON object of the fields every gateway expects. Kept small and obvious so a seller
     * adapting it to their gateway's exact field names changes one method, not the delivery logic.
     */
    private String buildBody(OutboundMessage message) {
        Map<String, String> fields = new java.util.LinkedHashMap<>();
        fields.put("to", message.recipient());
        fields.put("message", message.textBody());
        if (senderId != null && !senderId.isBlank()) {
            fields.put("sender", senderId);
        }
        if (apiKey != null && !apiKey.isBlank()) {
            fields.put("apiKey", apiKey);
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(fields);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new MessageDeliveryException("the SMS request could not be built");
        }
    }

    private static String mask(String value) {
        if (value == null || value.length() < 4) {
            return "****";
        }
        return "******" + value.substring(value.length() - 4);
    }
}
