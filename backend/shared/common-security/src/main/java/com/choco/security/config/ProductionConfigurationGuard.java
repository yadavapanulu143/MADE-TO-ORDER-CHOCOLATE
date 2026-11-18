package com.choco.security.config;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

/**
 * Refuses to start under the {@code prod} profile while a development default is still in effect.
 *
 * <p><b>Why this exists when the compose overlay already requires every secret.</b> The overlay uses
 * {@code ${VAR:?}}, which makes Compose refuse to start a stack whose credential is unset. That guard
 * lives in one file and only covers deployments that actually go through that file. A bare
 * {@code java -jar}, a Kubernetes manifest, a systemd unit or a forgotten {@code -f} flag all bypass
 * it, and the service's own {@code application.yml} would then quietly fall back to the development
 * password — a published password on a reachable database, which is the exact failure this project
 * has already had to correct once. This listener is the second line: it reads the *resolved*
 * environment, so it catches a default no matter which layer supplied it, and it fails closed.
 *
 * <p><b>Scoped per capability.</b> Not every service has every dependency: the gateway has Redis but
 * no database, and only some services use RabbitMQ. A check is therefore applied only when the
 * capability is actually configured, so the guard fails on a weak credential and never on an absent
 * feature.
 *
 * <p>It is deliberately active only under {@code prod}. Local runs and every test keep the convenient
 * defaults; the check runs where a weak credential would do real damage.
 */
public class ProductionConfigurationGuard
        implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ProductionConfigurationGuard.class);

    /** The profile whose presence turns the guard on. */
    private static final String PRODUCTION_PROFILE = "prod";

    /**
     * A dependency, the property that marks it as used, and its forbidden development passwords.
     *
     * <p>Each password value mirrors the {@code ${...:default}} fallback in the owning service's
     * {@code application.yml}; a test asserts the two agree, so a new default cannot be added without
     * being guarded.
     */
    record Capability(String presenceProperty, String passwordProperty,
                      List<String> developmentDefaults) {
    }

    static final List<Capability> CAPABILITIES = List.of(
            new Capability("spring.datasource.url", "spring.datasource.password", List.of(
                    "choco_identity_pw", "choco_catalog_pw", "choco_order_pw",
                    "choco_payment_pw", "choco_fulfilment_pw", "choco_notification_pw",
                    "choco_reporting_pw")),
            new Capability("spring.data.redis.host", "spring.data.redis.password",
                    List.of("choco_redis_pw")),
            new Capability("spring.rabbitmq.host", "spring.rabbitmq.password",
                    List.of("choco_rabbit_pw")));

    /**
     * Runs last, after Spring Boot's own {@code EnvironmentPostProcessorApplicationListener} has
     * loaded {@code application.yml} into the environment. Reading the resolved password depends on
     * that ordering: an earlier listener would see the raw placeholder as absent and reject a correct
     * production configuration.
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        Environment environment = event.getEnvironment();
        if (!List.of(environment.getActiveProfiles()).contains(PRODUCTION_PROFILE)) {
            return;
        }
        verify(environment);
    }

    /** Package-private so a unit test can drive it with a prepared {@link Environment}. */
    void verify(Environment environment) {
        for (Capability capability : CAPABILITIES) {
            if (!isConfigured(environment, capability.presenceProperty())) {
                continue;
            }
            String value = environment.getProperty(capability.passwordProperty());
            if (value == null || value.isBlank()) {
                throw new IllegalStateException(
                        "Refusing to start with the 'prod' profile: " + capability.passwordProperty()
                                + " is not set. Supply it through the environment or a secrets mount.");
            }
            if (capability.developmentDefaults().contains(value)) {
                throw new IllegalStateException(
                        "Refusing to start with the 'prod' profile: " + capability.passwordProperty()
                                + " is still the development default. Set a real secret.");
            }
        }
        log.info("Production configuration check passed: no development credential defaults are in effect.");
    }

    private boolean isConfigured(Environment environment, String presenceProperty) {
        String value = environment.getProperty(presenceProperty);
        return value != null && !value.isBlank();
    }

    /** Exposed for the drift test that compares these values to each service's yml defaults. */
    static Map<String, List<String>> developmentDefaultsByPasswordProperty() {
        return CAPABILITIES.stream().collect(Collectors.toMap(
                Capability::passwordProperty, Capability::developmentDefaults));
    }
}
