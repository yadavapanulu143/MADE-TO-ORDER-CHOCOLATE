package com.choco.payment.adapter.in.config;

import com.choco.core.port.Clock;
import com.choco.core.port.EventPublisher;
import com.choco.core.port.IdGenerator;
import com.choco.core.port.TransactionRunner;
import com.choco.payment.application.port.AuditLogPort;
import com.choco.payment.application.service.PaymentQueryService;
import com.choco.payment.application.service.PaymentService;
import com.choco.payment.application.service.PaymentWebhookService;
import com.choco.payment.application.service.RefundService;
import com.choco.payment.domain.port.IdempotencyRecordRepository;
import com.choco.payment.domain.port.PaymentGatewayPort;
import com.choco.payment.domain.port.PaymentRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the payment use cases.
 *
 * <p>The use cases are plain classes built here rather than {@code @Service}-annotated, so the
 * application layer carries no Spring import and each can be constructed in a unit test with no
 * container. The gateway is injected as the {@link PaymentGatewayPort} interface, so the same use
 * cases run against the real Razorpay adapter in production and against a fake in a unit test.
 */
@Configuration(proxyBeanMethods = false)
class PaymentApplicationConfiguration {

    @Bean
    PaymentService paymentService(PaymentRepository paymentRepository,
                                  IdempotencyRecordRepository idempotencyRepository,
                                  PaymentGatewayPort gateway, EventPublisher eventPublisher,
                                  AuditLogPort auditLog, Clock clock, IdGenerator idGenerator,
                                  TransactionRunner transactionRunner) {
        return new PaymentService(paymentRepository, idempotencyRepository, gateway, eventPublisher,
                auditLog, clock, idGenerator, transactionRunner);
    }

    @Bean
    PaymentWebhookService paymentWebhookService(PaymentRepository paymentRepository,
                                                PaymentGatewayPort gateway, PaymentService paymentService,
                                                AuditLogPort auditLog, Clock clock,
                                                TransactionRunner transactionRunner) {
        return new PaymentWebhookService(paymentRepository, gateway, paymentService, auditLog, clock,
                transactionRunner);
    }

    @Bean
    RefundService refundService(PaymentRepository paymentRepository, PaymentGatewayPort gateway,
                                AuditLogPort auditLog, Clock clock, IdGenerator idGenerator,
                                TransactionRunner transactionRunner) {
        return new RefundService(paymentRepository, gateway, auditLog, clock, idGenerator,
                transactionRunner);
    }

    @Bean
    PaymentQueryService paymentQueryService(PaymentRepository paymentRepository) {
        return new PaymentQueryService(paymentRepository);
    }
}
