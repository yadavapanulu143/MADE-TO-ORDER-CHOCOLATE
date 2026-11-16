package com.choco.redis.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.choco.core.port.RateLimiter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifies the rate limiter against a real Redis.
 *
 * <p>Run against a container rather than a mock because the behaviour under test <em>is</em> the
 * interaction with the server: the Lua script's atomicity is not something a stub can demonstrate. A
 * mock would pass while the real limiter allowed unlimited requests.
 *
 * <p>The concurrency test is the important one. It is the only way to show that the check and the
 * increment really are one step; a read-then-write implementation passes every sequential test and
 * fails this one.
 */
@Testcontainers
class RedisRateLimiterIT {

    private static GenericContainer<?> redis;
    private static LettuceConnectionFactory connectionFactory;

    private StringRedisTemplate template;
    private RedisRateLimiter limiter;

    @BeforeAll
    static void startRedis() {
        redis = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
                .withExposedPorts(6379);
        redis.start();

        connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
    }

    @AfterAll
    static void stopRedis() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        if (redis != null) {
            redis.stop();
        }
    }

    @BeforeEach
    void setUp() {
        template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        limiter = new RedisRateLimiter(template);
        template.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void allows_exactly_the_limit_then_refuses() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryConsume("k", 3, 60).allowed())
                    .as("attempt %d is within the limit", i + 1)
                    .isTrue();
        }

        RateLimiter.Decision denied = limiter.tryConsume("k", 3, 60);
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfterSeconds())
                .as("a refusal must tell the client how long to wait")
                .isPositive();
    }

    @Test
    void the_counter_expires_so_idle_keys_do_not_accumulate() throws Exception {
        limiter.tryConsume("expiring", 5, 1);
        assertThat(limiter.currentCount("expiring")).isEqualTo(1);

        template.expire("expiring", Duration.ofMillis(200));
        Thread.sleep(1200);

        assertThat(limiter.currentCount("expiring"))
                .as("the key must clear itself; otherwise every identifier ever seen stays in memory")
                .isZero();
    }

    @Test
    void reset_clears_the_counter() {
        limiter.tryConsume("resettable", 5, 60);
        limiter.tryConsume("resettable", 5, 60);
        assertThat(limiter.currentCount("resettable")).isEqualTo(2);

        limiter.reset("resettable");

        assertThat(limiter.currentCount("resettable")).isZero();
    }

    @Test
    void concurrent_requests_cannot_exceed_the_limit() throws Exception {
        int limit = 10;
        int threads = 40;

        // Every thread fires at once. If the increment and the check were separate round trips, many
        // threads would read the same count and all be allowed.
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> limiter.tryConsume("contended", limit, 60).allowed());
        }

        List<Future<Boolean>> futures = pool.invokeAll(tasks);
        long allowed = 0;
        for (Future<Boolean> future : futures) {
            if (future.get()) {
                allowed++;
            }
        }
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(allowed)
                .as("exactly %d of %d concurrent requests may pass; a higher number means the "
                        + "check and increment are not atomic", limit, threads)
                .isEqualTo(limit);
    }
}
