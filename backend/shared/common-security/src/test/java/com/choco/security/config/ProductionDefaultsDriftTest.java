package com.choco.security.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Keeps {@link ProductionConfigurationGuard}'s list of forbidden development passwords honest.
 *
 * <p>The guard is only as good as the values it knows about. If a service adds a new credential
 * default and the guard is not told, production silently starts with a published password again —
 * exactly the failure the guard exists to prevent. Reading each service's {@code application.yml} and
 * asserting every {@code ${...:default}} secret is guarded turns that omission into a failing test.
 */
class ProductionDefaultsDriftTest {

    /** Matches a placeholder whose default looks like a secret, e.g. {@code ${X:choco_order_pw}}. */
    private static final Pattern SECRET_DEFAULT = Pattern.compile(
            "\\$\\{([A-Z_]+):(choco_[a-z_]+_pw|choco_redis_pw|choco_rabbit_pw)\\}");

    @Test
    @DisplayName("every development credential default in a service yml is known to the production guard")
    void everyDevelopmentCredentialDefaultIsGuarded() throws IOException {
        Path servicesRoot = locateServicesRoot();
        assertThat(Files.isDirectory(servicesRoot))
                .as("expected services directory at %s", servicesRoot)
                .isTrue();

        List<String> guardedDefaults = ProductionConfigurationGuard
                .developmentDefaultsByPasswordProperty().values().stream()
                .flatMap(List::stream)
                .toList();

        List<String> unguarded = new ArrayList<>();
        try (Stream<Path> ymls = Files.walk(servicesRoot)) {
            for (Path yml : ymls.filter(p -> p.toString().endsWith("application.yml")).toList()) {
                String content = Files.readString(yml);
                Matcher matcher = SECRET_DEFAULT.matcher(content);
                while (matcher.find()) {
                    String defaultValue = matcher.group(2);
                    if (!guardedDefaults.contains(defaultValue)) {
                        unguarded.add(defaultValue + " (in " + servicesRoot.relativize(yml) + ")");
                    }
                }
            }
        }

        assertThat(unguarded)
                .as("these development credential defaults are not rejected in production; add them to "
                        + "ProductionConfigurationGuard.CAPABILITIES")
                .isEmpty();
    }

    /**
     * Walks up from the module directory until the backend root (the parent of both {@code services}
     * and {@code shared}) is found, so the test does not depend on the exact working directory a
     * build tool happens to use.
     */
    private static Path locateServicesRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("services");
            if (Files.isDirectory(candidate) && Files.isDirectory(current.resolve("shared"))) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("could not locate the backend services directory");
    }
}
