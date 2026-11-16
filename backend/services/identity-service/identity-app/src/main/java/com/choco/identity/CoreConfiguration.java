package com.choco.identity;

import com.choco.core.port.Clock;
import com.choco.core.port.IdGenerator;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Supplies the time and identifier ports.
 *
 * <p>Time is injected rather than read from {@code Instant.now()} at the point of use so that
 * expiry, lockout and resend-throttle behaviour can be tested at exact boundaries without sleeping.
 * A test that has to wait fifteen minutes to prove a lockout expires is a test nobody runs.
 */
@Configuration
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
