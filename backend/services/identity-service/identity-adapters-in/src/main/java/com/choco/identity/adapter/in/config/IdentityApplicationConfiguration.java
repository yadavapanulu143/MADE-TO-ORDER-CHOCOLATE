package com.choco.identity.adapter.in.config;

import com.choco.core.port.Clock;
import com.choco.core.port.EventPublisher;
import com.choco.core.port.IdGenerator;
import com.choco.core.port.PasswordHasher;
import com.choco.core.port.PasswordPolicy;
import com.choco.core.port.RateLimiter;
import com.choco.core.port.TransactionRunner;
import com.choco.identity.application.port.AccountNotificationPort;
import com.choco.identity.application.port.AuditLogPort;
import com.choco.identity.application.port.OpaqueTokenHasher;
import com.choco.identity.application.port.OtpDeliveryPort;
import com.choco.identity.application.port.PasswordResetTokenRepository;
import com.choco.identity.application.port.RefreshTokenRepository;
import com.choco.identity.application.port.TokenService;
import com.choco.identity.application.service.AbuseGuard;
import com.choco.identity.application.service.AuthenticationService;
import com.choco.identity.application.service.PasswordResetService;
import com.choco.identity.application.service.RegistrationService;
import com.choco.identity.application.service.VerificationService;
import com.choco.identity.domain.port.CustomerRepository;
import com.choco.identity.domain.port.OtpChallengeRepository;
import com.choco.identity.domain.port.OtpCodeService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the application-layer use cases.
 *
 * <p>The use cases are plain classes with constructor injection, not {@code @Service}-annotated
 * components. That is deliberate: annotating them would put a Spring import in the application layer
 * and make them depend on the container to be instantiated. Building them here keeps the use cases
 * unit-testable with plain constructor calls, which is exactly how the application tests construct
 * them.
 *
 * <p>{@link PasswordPolicy} is adapted from the security module's static validator, so the policy is
 * defined once and the application layer sees it through the port.
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
class IdentityApplicationConfiguration {

    /**
     * The authentication rate limits.
     *
     * <p>Built from {@link RateLimitProperties}, whose field initialisers read
     * {@link AbuseGuard.Policy#defaults()}. That keeps the shipped limits in one place: an operator
     * overriding {@code choco.security.rate-limit.*} replaces a single documented default rather than
     * shadowing a duplicate copy of it.
     */
    @Bean
    AbuseGuard abuseGuard(RateLimiter rateLimiter, OpaqueTokenHasher keyHasher,
                          RateLimitProperties properties) {
        return new AbuseGuard(rateLimiter, keyHasher, properties.toPolicy());
    }

    @Bean
    RegistrationService registrationService(CustomerRepository customerRepository,
                                            OtpChallengeRepository otpChallengeRepository,
                                            PasswordHasher passwordHasher,
                                            PasswordPolicy passwordPolicy,
                                            OtpCodeService otpCodeService,
                                            OtpDeliveryPort otpDelivery,
                                            AccountNotificationPort notifications,
                                            AuditLogPort auditLog,
                                            EventPublisher eventPublisher,
                                            TransactionRunner transactionRunner,
                                            AbuseGuard abuseGuard,
                                            IdGenerator idGenerator,
                                            Clock clock) {
        return new RegistrationService(customerRepository, otpChallengeRepository, passwordHasher,
                passwordPolicy, otpCodeService, otpDelivery, notifications, auditLog, eventPublisher,
                abuseGuard, transactionRunner, idGenerator, clock);
    }

    @Bean
    VerificationService verificationService(CustomerRepository customerRepository,
                                            OtpChallengeRepository otpChallengeRepository,
                                            OtpCodeService otpCodeService,
                                            AccountNotificationPort notifications,
                                            AuditLogPort auditLog,
                                            EventPublisher eventPublisher,
                                            TransactionRunner transactionRunner,
                                            AbuseGuard abuseGuard,
                                            Clock clock) {
        return new VerificationService(customerRepository, otpChallengeRepository, otpCodeService,
                notifications, auditLog, eventPublisher, abuseGuard, transactionRunner, clock);
    }

    @Bean
    AuthenticationService authenticationService(CustomerRepository customerRepository,
                                                RefreshTokenRepository refreshTokenRepository,
                                                PasswordHasher passwordHasher,
                                                TokenService tokenService,
                                                AuditLogPort auditLog,
                                                AbuseGuard abuseGuard,
                                                TransactionRunner transactionRunner,
                                                IdGenerator idGenerator,
                                                Clock clock) {
        return new AuthenticationService(customerRepository, refreshTokenRepository, passwordHasher,
                tokenService, auditLog, abuseGuard, transactionRunner, idGenerator, clock);
    }

    @Bean
    PasswordResetService passwordResetService(CustomerRepository customerRepository,
                                              PasswordResetTokenRepository tokenRepository,
                                              OpaqueTokenHasher tokenHasher,
                                              PasswordHasher passwordHasher,
                                              PasswordPolicy passwordPolicy,
                                              AccountNotificationPort notifications,
                                              AuditLogPort auditLog,
                                              AuthenticationService authenticationService,
                                              IdGenerator idGenerator,
                                              AbuseGuard abuseGuard,
                                              TransactionRunner transactionRunner,
                                              Clock clock) {
        return new PasswordResetService(customerRepository, tokenRepository, tokenHasher,
                passwordHasher, passwordPolicy, notifications, auditLog, authenticationService,
                idGenerator, abuseGuard, transactionRunner, clock);
    }

    /**
     * Bridges the security module's static validator to the port the application layer depends on.
     *
     * <p>Adapting rather than duplicating means the minimum length and blocklist are defined in one
     * place. A second copy of the rules would drift, and the drift would be invisible until a weak
     * password slipped through one path but not another.
     */
    @Bean
    PasswordPolicy passwordPolicy() {
        return rawPassword -> {
            try {
                com.choco.security.password.PasswordPolicy.validate(rawPassword);
                return java.util.List.of();
            } catch (com.choco.core.error.ValidationException e) {
                return java.util.List.copyOf(e.fieldErrors().values());
            }
        };
    }

    @Bean
    com.choco.identity.application.service.CustomerQueryService customerQueryService(
            CustomerRepository customerRepository) {
        return new com.choco.identity.application.service.CustomerQueryService(customerRepository);
    }
}
