package com.choco.test;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Container definitions shared by every integration test.
 *
 * <p>Pinned image tags, not {@code latest}: an integration test must fail because the code changed,
 * never because a container image was silently replaced overnight.
 */
public final class Containers {

    public static final String POSTGRES_IMAGE = "postgres:17.6-alpine";
    public static final String RABBITMQ_IMAGE = "rabbitmq:4.1-management-alpine";

    private Containers() {
    }

    /** Single-use Postgres, reused across test classes within one JVM run. */
    public static PostgreSQLContainer<?> postgres(String databaseName) {
        return new PostgreSQLContainer<>(DockerImageName.parse(POSTGRES_IMAGE))
                .withDatabaseName(databaseName)
                .withUsername("choco")
                .withPassword("choco")
                .withReuse(true);
    }

    public static RabbitMQContainer rabbitmq() {
        return new RabbitMQContainer(DockerImageName.parse(RABBITMQ_IMAGE));
    }
}
