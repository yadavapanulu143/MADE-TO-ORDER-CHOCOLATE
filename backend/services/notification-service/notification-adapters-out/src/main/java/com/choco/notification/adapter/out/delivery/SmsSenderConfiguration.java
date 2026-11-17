package com.choco.notification.adapter.out.delivery;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Supplies the SMS transport.
 *
 * <p>Declared as a bean rather than constructed inline so a test can replace it, and so the gateway
 * settings are read from configuration in one place. The gateway is optional: when its URL is blank the
 * sender logs rather than fails, which is the correct behaviour for a seller who has not chosen a
 * provider yet.
 *
 * <p>An {@code ObjectMapper} is not injected here: the sender builds a tiny fixed JSON body, and taking
 * a dependency on the shared mapper would tie the transport to whatever serialisation settings the
 * service happens to have. The body is small and self-contained.
 */
@Configuration(proxyBeanMethods = false)
class SmsSenderConfiguration {

    @Bean
    @ConditionalOnMissingBean(HttpSmsSender.class)
    HttpSmsSender httpSmsSender(
            @Value("${choco.notifications.sms.gateway-url:}") String gatewayUrl,
            @Value("${choco.notifications.sms.api-key:}") String apiKey,
            @Value("${choco.notifications.sms.sender-id:}") String senderId,
            @Value("${choco.notifications.sms.timeout-ms:5000}") long timeoutMillis) {
        return new HttpSmsSender(gatewayUrl, apiKey, senderId, Duration.ofMillis(timeoutMillis));
    }
}
