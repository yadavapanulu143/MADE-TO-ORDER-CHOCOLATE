package com.choco.notification.adapter.in.config;

import com.choco.core.port.Clock;
import com.choco.core.port.EventPublisher;
import com.choco.core.port.IdGenerator;
import com.choco.core.port.TransactionRunner;
import com.choco.notification.application.dto.SellerContact;
import com.choco.notification.application.port.AuditLogPort;
import com.choco.notification.application.port.ContactProjectionRepository;
import com.choco.notification.application.port.MessageSender;
import com.choco.notification.application.service.ContactProjectionService;
import com.choco.notification.application.service.NotificationDispatchService;
import com.choco.notification.application.service.NotificationQueryService;
import com.choco.notification.domain.port.ContactDirectory;
import com.choco.notification.domain.port.NotificationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the notification use cases.
 *
 * <p>The use cases are plain classes built here rather than {@code @Service}-annotated, so the
 * application layer carries no Spring import and each can be constructed in a unit test with no
 * container. This mirrors the order, payment and fulfilment services, so a reader who knows one knows
 * the others.
 *
 * <p>The seller's contact details, the retry budget and the sender's identity are business settings
 * read from configuration and passed in as plain values, not as a property bag the service reaches
 * into. The retry budget defaults to a small number: a message that has failed several times is more
 * likely to be undeliverable than momentarily unlucky, and repeatedly retrying it delays nothing and
 * helps nobody.
 */
@Configuration(proxyBeanMethods = false)
class NotificationApplicationConfiguration {

    @Bean
    SellerContact sellerContact(
            @Value("${choco.notifications.seller.email:}") String email,
            @Value("${choco.notifications.seller.mobile:}") String mobile) {
        return new SellerContact(email, mobile);
    }

    @Bean
    NotificationDispatchService notificationDispatchService(NotificationRepository repository,
                                                            ContactDirectory contactDirectory,
                                                            MessageSender messageSender,
                                                            SellerContact sellerContact,
                                                            EventPublisher eventPublisher,
                                                            AuditLogPort auditLog, Clock clock,
                                                            IdGenerator idGenerator,
                                                            TransactionRunner transactions,
                                                            @Value("${choco.notifications.retry.max-attempts:3}")
                                                            int maxAttempts) {
        return new NotificationDispatchService(repository, contactDirectory, messageSender, sellerContact,
                eventPublisher, auditLog, clock, idGenerator, transactions, maxAttempts);
    }

    @Bean
    NotificationQueryService notificationQueryService(NotificationRepository repository) {
        return new NotificationQueryService(repository);
    }

    @Bean
    ContactProjectionService contactProjectionService(ContactProjectionRepository repository,
                                                      ContactDirectory contactDirectory,
                                                      TransactionRunner transactions, Clock clock) {
        return new ContactProjectionService(repository, contactDirectory, transactions, clock);
    }
}
