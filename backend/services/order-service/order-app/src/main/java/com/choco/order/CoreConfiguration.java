package com.choco.order;

import com.choco.core.port.Clock;
import com.choco.core.port.IdGenerator;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Supplies the time and identifier ports.
 *
 * <p>Time is injected rather than read from {@code Instant.now()} at the point of use, so created and
 * updated timestamps can be asserted at exact values in a test without waiting. The identifier port
 * keeps the aggregate's identity generation out of the domain, which is what lets a unit test supply
 * predictable ids.
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
