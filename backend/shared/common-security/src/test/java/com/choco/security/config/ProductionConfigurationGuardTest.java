package com.choco.security.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultBootstrapContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

/**
 * The production guard exists to turn a silently weak production deployment into a loud startup
 * failure, so both directions matter: it must reject a development default and it must accept a real
 * secret, or a false positive would make the guard something an operator disables. It is also scoped
 * per capability, so a service without a database or a broker must not be blocked by a check for a
 * dependency it does not have.
 */
class ProductionConfigurationGuardTest {

    private final ProductionConfigurationGuard guard = new ProductionConfigurationGuard();

    private MockEnvironment withDatabase(String password) {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.datasource.url", "jdbc:postgresql://db:5432/choco");
        environment.setProperty("spring.datasource.password", password);
        return environment;
    }

    @Test
    @DisplayName("a real database and Redis secret passes")
    void realSecretPasses() {
        MockEnvironment environment = withDatabase("a-real-strong-password");
        environment.setProperty("spring.data.redis.host", "redis");
        environment.setProperty("spring.data.redis.password", "another-real-password");

        assertThatCode(() -> guard.verify(environment)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a service without a database is not blocked by the database check")
    void serviceWithoutDatabaseIsNotBlocked() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.data.redis.host", "redis");
        environment.setProperty("spring.data.redis.password", "a-real-password");

        assertThatCode(() -> guard.verify(environment)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the development database password is refused")
    void developmentDatabasePasswordIsRefused() {
        assertThatThrownBy(() -> guard.verify(withDatabase("choco_identity_pw")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.datasource.password");
    }

    @Test
    @DisplayName("every service's development database password is refused, not just identity's")
    void everyDevelopmentDatabasePasswordIsRefused() {
        for (String defaultValue : ProductionConfigurationGuard.developmentDefaultsByPasswordProperty()
                .get("spring.datasource.password")) {
            assertThatThrownBy(() -> guard.verify(withDatabase(defaultValue)))
                    .as("default %s must be refused", defaultValue)
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    @DisplayName("the development Redis password is refused")
    void developmentRedisPasswordIsRefused() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.data.redis.host", "redis");
        environment.setProperty("spring.data.redis.password", "choco_redis_pw");

        assertThatThrownBy(() -> guard.verify(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.data.redis.password");
    }

    @Test
    @DisplayName("the development RabbitMQ password is refused")
    void developmentRabbitPasswordIsRefused() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.rabbitmq.host", "rabbitmq");
        environment.setProperty("spring.rabbitmq.password", "choco_rabbit_pw");

        assertThatThrownBy(() -> guard.verify(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.rabbitmq.password");
    }

    @Test
    @DisplayName("a missing database password is refused when a datasource is configured")
    void missingDatabasePasswordIsRefused() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.datasource.url", "jdbc:postgresql://db:5432/choco");

        assertThatThrownBy(() -> guard.verify(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.datasource.password");
    }

    @Test
    @DisplayName("a blank database password is refused")
    void blankDatabasePasswordIsRefused() {
        assertThatThrownBy(() -> guard.verify(withDatabase("   ")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.datasource.password");
    }

    @Test
    @DisplayName("the guard only runs when prod is an active profile")
    void guardRunsOnlyUnderProd() {
        MockEnvironment environment = withDatabase("choco_identity_pw");
        environment.setActiveProfiles("prod");

        ApplicationEnvironmentPreparedEvent event = new ApplicationEnvironmentPreparedEvent(
                new DefaultBootstrapContext(),
                new SpringApplication(ProductionConfigurationGuardTest.class), new String[0],
                environment);

        assertThatThrownBy(() -> guard.onApplicationEvent(event))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("a non-prod profile list is ignored")
    void nonProdProfileIsIgnored() {
        MockEnvironment environment = withDatabase("choco_identity_pw");
        environment.setActiveProfiles("local", "test");

        ApplicationEnvironmentPreparedEvent event = new ApplicationEnvironmentPreparedEvent(
                new DefaultBootstrapContext(),
                new SpringApplication(ProductionConfigurationGuardTest.class), new String[0],
                environment);

        assertThatCode(() -> guard.onApplicationEvent(event)).doesNotThrowAnyException();
    }
}
