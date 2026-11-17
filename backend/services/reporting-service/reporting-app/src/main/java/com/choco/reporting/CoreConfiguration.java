package com.choco.reporting;

import com.choco.core.port.Clock;
import com.choco.core.port.IdGenerator;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Supplies the time and identifier ports.
 *
 * <p>Time is injected rather than read from {@code Instant.now()} at the point of use, so a report
 * window can be asserted at an exact instant in a test without waiting.
 */
@Configuration(proxyBeanMethods = false)
class CoreConfiguration {

    @Bean
    Clock clock() {
        return Instant::now;
    }

    @Bean
    IdGenerator idGenerator() {
        return UUID::randomUUID;
    }
}
