package com.choco.identity.adapter.out.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Supplies a logging SMS sender until a real gateway is configured.
 *
 * <p>Declared {@link ConditionalOnMissingBean} so that adding a provider adapter later replaces this
 * one without touching this class. The log line is deliberately loud: shipping to production with
 * verification codes going nowhere but a log file would let nobody register, and that should be
 * obvious from the first run rather than discovered by a customer.
 */
@Configuration
class SmsConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SmsConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(SmsSender.class)
    SmsSender loggingSmsSender() {
        return (mobileNumber, message) -> log.warn("No SMS provider is configured. A verification "
                + "message for {} was not sent. Configure an SMS gateway before going live. "
                + "Message body withheld from this log.", mask(mobileNumber));
    }

    private static String mask(String value) {
        if (value == null || value.length() < 4) {
            return "****";
        }
        return "****" + value.substring(value.length() - 4);
    }
}
