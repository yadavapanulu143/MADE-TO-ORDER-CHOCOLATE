package com.choco.fulfilment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.domain.Pincode;
import com.choco.core.error.ConflictException;
import com.choco.core.error.NotFoundException;
import com.choco.fulfilment.application.command.DeliveryAssignCommand;
import com.choco.fulfilment.application.command.DeliveryProgressCommand;
import com.choco.fulfilment.application.command.DeliveryProgressCommand.DeliveryAction;
import com.choco.fulfilment.application.command.OrderConfirmedCommand;
import com.choco.fulfilment.application.command.UpsertServiceableAreaCommand;
import com.choco.fulfilment.application.dto.DeliveryView;
import com.choco.fulfilment.application.support.InMemoryDeliveryRepository;
import com.choco.fulfilment.application.support.InMemoryProductionJobRepository;
import com.choco.fulfilment.application.support.InMemoryServiceableAreaRepository;
import com.choco.fulfilment.application.support.RecordingAuditLog;
import com.choco.fulfilment.domain.delivery.DeliveryStatus;
import com.choco.test.Fakes;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Delivery service")
class DeliveryServiceTest {

    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final String ORDER = UUID.randomUUID().toString();
    private static final String ADMIN = "admin-1";

    private InMemoryDeliveryRepository deliveries;
    private InMemoryServiceableAreaRepository areas;
    private RecordingAuditLog audit;
    private Fakes.RecordingEventPublisher events;
    private Fakes.FixedClock clock;
    private DeliveryService service;

    @BeforeEach
    void setUp() {
        deliveries = new InMemoryDeliveryRepository();
        areas = new InMemoryServiceableAreaRepository();
        audit = new RecordingAuditLog();
        events = new Fakes.RecordingEventPublisher();
        clock = Fakes.FixedClock.startingAt("2026-01-01T10:00:00Z");
        service = new DeliveryService(deliveries, areas, events, audit, clock,
                new Fakes.SequentialIdGenerator(), new Fakes.DirectTransactionRunner(), false);
    }

    @Nested
    @DisplayName("coverage")
    class Coverage {

        @Test
        @DisplayName("an uncovered PIN code is answered, not refused as an error")
        void uncoveredIsANormalAnswer() {
            var quote = service.quote(Pincode.of("560001"));

            assertThat(quote.serviceable()).isFalse();
            assertThat(quote.deliveryChargeMinorUnits()).isZero();
            assertThat(quote.reason()).contains("560001");
        }

        @Test
        @DisplayName("the most specific area wins: an exact PIN overrides the city-wide entry")
        void longestPrefixWins() {
            service.upsertArea(ADMIN, new UpsertServiceableAreaCommand(null, "5600", "Bengaluru", 4000,
                    true));
            service.upsertArea(ADMIN, new UpsertServiceableAreaCommand(null, "560001", "MG Road", 9900,
                    true));

            assertThat(service.quote(Pincode.of("560001")).deliveryChargeMinorUnits()).isEqualTo(9900);
            assertThat(service.quote(Pincode.of("560099")).deliveryChargeMinorUnits()).isEqualTo(4000);
        }

        @Test
        @DisplayName("a duplicate prefix is refused rather than silently overwriting")
        void duplicatePrefixIsRefused() {
            service.upsertArea(ADMIN, new UpsertServiceableAreaCommand(null, "560001", "MG Road", 5000,
                    true));

            assertThatThrownBy(() -> service.upsertArea(ADMIN,
                    new UpsertServiceableAreaCommand(null, "560001", "Other", 6000, true)))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        @DisplayName("a deactivated area stops being quoted")
        void deactivatedAreaIsNotQuoted() {
            var area = service.upsertArea(ADMIN, new UpsertServiceableAreaCommand(null, "560001",
                    "MG Road", 5000, true));
            service.upsertArea(ADMIN, new UpsertServiceableAreaCommand(area.id(), "560001", "MG Road",
                    5000, false));

            assertThat(service.quote(Pincode.of("560001")).serviceable()).isFalse();
        }
    }

    @Nested
    @DisplayName("seller steps")
    class SellerSteps {

        private void openDelivery() {
            deliveries.save(com.choco.fulfilment.domain.delivery.Delivery.open(
                    UUID.randomUUID(), UUID.fromString(ORDER), "CHOCO-2026001-1", CUSTOMER,
                    new com.choco.fulfilment.domain.delivery.DeliveryAddress("Asha Rao",
                            com.choco.core.domain.PhoneNumber.of("9876543210"), "12 Cocoa Lane", null,
                            "Mumbai", "Maharashtra", com.choco.core.domain.Pincode.of("400001"), null),
                    com.choco.core.domain.Money.inr(5000), clock.now()));
        }

        @Test
        @DisplayName("a delivery starts pending and can be assigned")
        void assignDelivery() {
            openDelivery();
            DeliveryView view = service.assign(ADMIN, new DeliveryAssignCommand(UUID.fromString(ORDER),
                    "Ravi", "9000000000"));

            assertThat(view.status()).isEqualTo(DeliveryStatus.ASSIGNED.name());
            assertThat(view.deliveryPersonName()).isEqualTo("Ravi");
            assertThat(audit.hasAction("delivery.assign")).isTrue();
            // The customer is told a delivery has been arranged, so the assignment must reach the bus.
            assertThat(events.eventsOfType("fulfilment.delivery-assigned")).hasSize(1);
        }

        @Test
        @DisplayName("a re-assignment before dispatch raises no further event")
        void reassignmentRaisesNoFurtherEvent() {
            openDelivery();
            service.assign(ADMIN, new DeliveryAssignCommand(UUID.fromString(ORDER), "Ravi", null));
            service.assign(ADMIN, new DeliveryAssignCommand(UUID.fromString(ORDER), "Sita", null));

            // Only the first assignment is news; swapping the seller's own staff is not.
            assertThat(events.eventsOfType("fulfilment.delivery-assigned")).hasSize(1);
        }

        @Test
        @DisplayName("a delivery cannot go out before it is assigned")
        void cannotGoOutBeforeAssign() {
            openDelivery();
            assertThatThrownBy(() -> service.advance(ADMIN, new DeliveryProgressCommand(
                    UUID.fromString(ORDER), DeliveryAction.OUT_FOR_DELIVERY, null, null, null)))
                    .isInstanceOf(com.choco.core.error.BusinessRuleException.class);
        }

        @Test
        @DisplayName("a failed attempt is recorded and the delivery can be rescheduled")
        void failedAttemptThenReschedule() {
            openDelivery();
            service.assign(ADMIN, new DeliveryAssignCommand(UUID.fromString(ORDER), "Ravi", null));
            service.advance(ADMIN, new DeliveryProgressCommand(UUID.fromString(ORDER),
                    DeliveryAction.OUT_FOR_DELIVERY, null, null, null));
            DeliveryView failed = service.advance(ADMIN, new DeliveryProgressCommand(
                    UUID.fromString(ORDER), DeliveryAction.ATTEMPT_FAILED, "no one home", null, null));

            assertThat(failed.status()).isEqualTo(DeliveryStatus.ATTEMPT_FAILED.name());
            assertThat(failed.attempts()).hasSize(1);

            DeliveryView rescheduled = service.advance(ADMIN, new DeliveryProgressCommand(
                    UUID.fromString(ORDER), DeliveryAction.RESCHEDULED, null, null, "tomorrow"));
            assertThat(rescheduled.status()).isEqualTo(DeliveryStatus.RESCHEDULED.name());
        }

        @Test
        @DisplayName("a successful delivery is recorded with its time and proof reference")
        void delivered() {
            openDelivery();
            service.assign(ADMIN, new DeliveryAssignCommand(UUID.fromString(ORDER), "Ravi", null));
            service.advance(ADMIN, new DeliveryProgressCommand(UUID.fromString(ORDER),
                    DeliveryAction.OUT_FOR_DELIVERY, null, null, null));
            clock.advanceSeconds(3600);
            DeliveryView delivered = service.advance(ADMIN, new DeliveryProgressCommand(
                    UUID.fromString(ORDER), DeliveryAction.DELIVERED, null, "signed by Asha", null));

            assertThat(delivered.status()).isEqualTo(DeliveryStatus.DELIVERED.name());
            assertThat(delivered.deliveredAt()).isNotNull();
            assertThat(events.eventsOfType("fulfilment.delivery-delivered")).hasSize(1);
        }
    }

    @Nested
    @DisplayName("customer scoping")
    class Scoping {

        @BeforeEach
        void openDelivery() {
            deliveries.save(com.choco.fulfilment.domain.delivery.Delivery.open(
                    UUID.randomUUID(), UUID.fromString(ORDER), "CHOCO-2026001-1", CUSTOMER,
                    new com.choco.fulfilment.domain.delivery.DeliveryAddress("Asha Rao",
                            com.choco.core.domain.PhoneNumber.of("9876543210"), "12 Cocoa Lane", null,
                            "Mumbai", "Maharashtra", com.choco.core.domain.Pincode.of("400001"), null),
                    com.choco.core.domain.Money.inr(5000), clock.now()));
        }

        @Test
        @DisplayName("the owner sees their delivery")
        void ownerSeesDelivery() {
            assertThat(service.forCustomerOrder(CUSTOMER, UUID.fromString(ORDER)).orderId())
                    .isEqualTo(UUID.fromString(ORDER));
        }

        @Test
        @DisplayName("another customer's order is not found, not returned")
        void otherCustomerGetsNotFound() {
            assertThatThrownBy(() -> service.forCustomerOrder(UUID.randomUUID(), UUID.fromString(ORDER)))
                    .isInstanceOf(NotFoundException.class);
        }
    }
}
