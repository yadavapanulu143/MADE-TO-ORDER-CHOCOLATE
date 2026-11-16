package com.choco.redis.config;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Turns off Spring Data Redis repository scanning for every service that depends on this module.
 *
 * <p>This is an {@link EnvironmentPostProcessor} rather than an entry in each service's
 * {@code application.yml} for one reason: the setting must not be forgettable. A new service that
 * omits it starts logging "Could not safely identify store assignment for repository candidate ..."
 * for every JPA repository it declares, and the log is noisy enough that people filter it out
 * instead of fixing it. Setting it once here means no service has to remember.
 *
 * <p>The property source is added <em>last</em>, which gives it the lowest precedence. A service that
 * genuinely needs Redis repositories can still switch scanning back on in its own configuration; the
 * default is off, but it is a default and not a wall.
 *
 * <p>Scanning off is safe because this platform never uses a Spring Data Redis repository. Ephemeral
 * state goes through {@code StringRedisTemplate} and the rate limiter. That is also the safer
 * arrangement: with scanning on, a repository interface added by accident would be wired to Redis and
 * would write to the wrong store without any error to notice.
 */
public class RedisRepositoryScanningPostProcessor implements EnvironmentPostProcessor {

    static final String PROPERTY = "spring.data.redis.repositories.enabled";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put(PROPERTY, "false");
        environment.getPropertySources().addLast(new MapPropertySource("chocoRedisDefaults", defaults));
    }
}
