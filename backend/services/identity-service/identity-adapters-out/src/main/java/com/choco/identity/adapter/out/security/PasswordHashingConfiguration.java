package com.choco.identity.adapter.out.security;

import com.choco.core.port.PasswordHasher;
import com.choco.security.password.PasswordHasherFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the framework-free {@link PasswordHasher} port to the security module's implementation.
 *
 * <p>The adapter exists so that the domain and application layers can depend on
 * {@code com.choco.core.port.PasswordHasher} while the actual algorithm is chosen at deployment
 * time via {@code choco.security.password.algorithm}. Switching Argon2id to BCrypt then becomes a
 * configuration change, and existing hashes keep verifying because the delegating hasher recognises
 * both encodings and upgrades them on the next successful login.
 */
@Configuration
public class PasswordHashingConfiguration {

    @Bean
    public PasswordHasher passwordHasher(
            @Value("${choco.security.password.algorithm:argon2id}") String algorithm) {
        return PasswordHasherFactory.create(algorithm);
    }
}
