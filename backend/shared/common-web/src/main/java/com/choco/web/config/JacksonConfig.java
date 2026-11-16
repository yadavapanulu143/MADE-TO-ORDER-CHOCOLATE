package com.choco.web.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.TimeZone;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    /**
     * Serialises timestamps as ISO-8601 UTC and omits nulls, so clients see one unambiguous date
     * format and response bodies stay small.
     *
     * <p>No {@code ObjectMapper} bean is declared here. Spring Boot builds one from every
     * {@link Jackson2ObjectMapperBuilderCustomizer} on the context, so this customiser is applied
     * automatically. Declaring a competing {@code ObjectMapper} bean would override Boot's and, more
     * subtly, would drop the other customisers from the result; it also failed to start, because the
     * method could not choose between this customiser and Boot's own.
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsonCustomizer() {
        return builder -> builder
                .modules(new JavaTimeModule())
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .timeZone(TimeZone.getTimeZone("UTC"));
    }
}
