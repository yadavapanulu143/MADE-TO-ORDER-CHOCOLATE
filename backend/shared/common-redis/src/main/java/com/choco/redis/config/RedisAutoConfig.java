package com.choco.redis.config;

import com.choco.core.port.RateLimiter;
import com.choco.redis.ratelimit.RedisRateLimiter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis wiring shared by every service.
 *
 * <p><b>Why repository scanning is switched off.</b> Spring Data Redis scans for repository
 * interfaces by default. In a service that also has JPA repositories, it examines each one, cannot
 * decide whether it is a Redis repository or a JPA repository, and logs an INFO line for every
 * candidate at startup: "Could not safely identify store assignment for repository candidate ...".
 *
 * <p>The noise is the visible symptom, but the real problem is what it would become. The moment the
 * Redis client is on the classpath and a Spring Data Redis repository interface is added by mistake,
 * it is picked up and wired — a repository that silently writes to the wrong store. Turning scanning
 * off means a stray interface simply is not a repository, and the mistake surfaces as a missing bean
 * rather than as data in the wrong database.
 *
 * <p>This costs nothing here: ephemeral Redis data is accessed through {@link StringRedisTemplate}
 * and the rate limiter, never through a Spring Data repository. The {@code redisTemplate} and
 * {@code stringRedisTemplate} beans that {@link RedisAutoConfiguration} already provides are what the
 * code actually uses.
 *
 * <p>Scanning is disabled by {@link RedisRepositoryScanningPostProcessor}, which applies it to every
 * service that depends on this module, rather than by an annotation each service has to remember.
 */
@AutoConfiguration(after = RedisAutoConfiguration.class)
@ConditionalOnClass(RedisConnectionFactory.class)
public class RedisAutoConfig {

    /**
     * Rate limiter backed by Redis, so limits are shared across replicas.
     *
     * <p>An in-process counter would let an attacker multiply the allowance by the number of
     * instances: with three replicas, "five login attempts" becomes fifteen. The counter has to live
     * outside the process for the limit to mean anything.
     */
    @Bean
    @ConditionalOnMissingBean
    public RateLimiter rateLimiter(StringRedisTemplate stringRedisTemplate) {
        return new RedisRateLimiter(stringRedisTemplate);
    }
}
