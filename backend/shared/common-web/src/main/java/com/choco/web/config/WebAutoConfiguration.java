package com.choco.web.config;

import com.choco.web.error.GlobalExceptionHandler;
import com.choco.web.filter.CorrelationIdFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Registered through {@code AutoConfiguration.imports} so every service inherits identical error
 * handling, correlation ids, JSON conventions and OpenAPI setup without copying configuration.
 */
@AutoConfiguration
@Import({GlobalExceptionHandler.class, CorrelationIdFilter.class, OpenApiConfig.class, JacksonConfig.class})
public class WebAutoConfiguration {
}
