package com.choco.order.adapter.in.config;

import com.choco.core.port.Clock;
import com.choco.core.port.EventPublisher;
import com.choco.core.port.IdGenerator;
import com.choco.core.port.TransactionRunner;
import com.choco.order.application.port.AuditLogPort;
import com.choco.order.application.port.CatalogPort;
import com.choco.order.application.port.CustomerPort;
import com.choco.order.application.port.OrderNumberGenerator;
import com.choco.order.application.port.PaymentPort;
import com.choco.order.application.port.ServiceabilityPort;
import com.choco.order.application.service.CartService;
import com.choco.order.application.service.CheckoutService;
import com.choco.order.application.service.OrderCommandService;
import com.choco.order.application.service.OrderQueryService;
import com.choco.order.application.service.PaymentInitiationService;
import com.choco.order.domain.port.CartRepository;
import com.choco.order.domain.port.IdempotencyRecordRepository;
import com.choco.order.domain.port.OrderRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the order use cases.
 *
 * <p>The use cases are plain classes built here rather than {@code @Service}-annotated, so the
 * application layer carries no Spring import and each one can be constructed in a unit test with no
 * container. This mirrors the identity and catalog services, so a reader who knows one service knows
 * the others.
 *
 * <p>The outbound ports — the catalogue, identity and delivery clients — are injected as interfaces.
 * Which implementation supplies them is decided by what is on the classpath, so the same use cases run
 * against the real HTTP clients in production and against in-memory fakes in a unit test.
 */
@Configuration(proxyBeanMethods = false)
class OrderApplicationConfiguration {

    @Bean
    CartService cartService(CartRepository cartRepository, CatalogPort catalogPort, Clock clock,
                            IdGenerator idGenerator, TransactionRunner transactionRunner) {
        return new CartService(cartRepository, catalogPort, clock, idGenerator, transactionRunner);
    }

    @Bean
    CheckoutService checkoutService(CartRepository cartRepository, OrderRepository orderRepository,
                                    IdempotencyRecordRepository idempotencyRepository,
                                    CatalogPort catalogPort, CustomerPort customerPort,
                                    ServiceabilityPort serviceabilityPort,
                                    OrderNumberGenerator orderNumberGenerator, Clock clock,
                                    IdGenerator idGenerator, TransactionRunner transactionRunner) {
        return new CheckoutService(cartRepository, orderRepository, idempotencyRepository, catalogPort,
                customerPort, serviceabilityPort, orderNumberGenerator, clock, idGenerator,
                transactionRunner);
    }

    @Bean
    OrderCommandService orderCommandService(OrderRepository orderRepository, EventPublisher eventPublisher,
                                            AuditLogPort auditLog, Clock clock,
                                            TransactionRunner transactionRunner) {
        return new OrderCommandService(orderRepository, eventPublisher, auditLog, clock, transactionRunner);
    }

    @Bean
    OrderQueryService orderQueryService(OrderRepository orderRepository) {
        return new OrderQueryService(orderRepository);
    }

    @Bean
    PaymentInitiationService paymentInitiationService(OrderRepository orderRepository,
                                                      PaymentPort paymentPort, AuditLogPort auditLog) {
        return new PaymentInitiationService(orderRepository, paymentPort, auditLog);
    }
}
