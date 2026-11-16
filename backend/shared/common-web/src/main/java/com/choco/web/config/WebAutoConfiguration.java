package com.choco.web.config;

import com.choco.core.port.TransactionRunner;
import com.choco.web.client.ClientIpResolver;
import com.choco.web.error.GlobalExceptionHandler;
import com.choco.web.filter.CorrelationIdFilter;
import com.choco.web.transaction.SpringTransactionRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Registered through {@code AutoConfiguration.imports} so every service inherits identical error
 * handling, correlation ids, JSON conventions and OpenAPI setup without copying configuration.
 */
@AutoConfiguration
@Import({GlobalExceptionHandler.class, CorrelationIdFilter.class, OpenApiConfig.class, JacksonConfig.class})
public class WebAutoConfiguration {

    /**
     * Provides the transaction boundary to the application layer.
     *
     * <p>Conditional on a {@link PlatformTransactionManager}, because a service with no datasource
     * (the gateway, for one) has no transactions to offer and should still start.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(PlatformTransactionManager.class)
    public TransactionRunner transactionRunner(PlatformTransactionManager transactionManager) {
        return new SpringTransactionRunner(new TransactionTemplate(transactionManager));
    }

    /**
     * Resolves the client address for rate limiting.
     *
     * <p>Shared rather than repeated per service because the trust rules for forwarded headers must
     * be identical everywhere; one service reading the header loosely would be the weakest link.
     */
    @Bean
    @ConditionalOnMissingBean
    public ClientIpResolver clientIpResolver(
            @org.springframework.beans.factory.annotation.Value("${choco.http.trusted-proxies:}")
            java.util.List<String> trustedProxies) {
        return new ClientIpResolver(trustedProxies);
    }
}

