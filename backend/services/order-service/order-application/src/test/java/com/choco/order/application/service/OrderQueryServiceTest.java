package com.choco.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.domain.EmailAddress;
import com.choco.core.domain.Money;
import com.choco.core.domain.PhoneNumber;
import com.choco.core.domain.Pincode;
import com.choco.core.domain.Quantity;
import com.choco.core.error.ForbiddenException;
import com.choco.core.error.NotFoundException;
import com.choco.core.port.PageQuery;
import com.choco.order.application.support.InMemoryOrderRepository;
import com.choco.order.domain.order.CustomerSnapshot;
import com.choco.order.domain.order.DeliveryAddress;
import com.choco.order.domain.order.Order;
import com.choco.order.domain.order.OrderLine;
import com.choco.order.domain.order.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Order query service")
class OrderQueryServiceTest {

    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final UUID PRODUCT = UUID.randomUUID();
    private static final UUID VARIANT = UUID.randomUUID();

    private InMemoryOrderRepository orders;
    private OrderQueryService service;

    @BeforeEach
    void setUp() {
        orders = new InMemoryOrderRepository();
        service = new OrderQueryService(orders);
    }

    @Test
    @DisplayName("a customer reads their own order")
    void ownOrder() {
        Order order = seed(CUSTOMER, "CHOCO-1");
        assertThat(service.ownOrder(CUSTOMER, order.id()).orderNumber()).isEqualTo("CHOCO-1");
    }

    @Test
    @DisplayName("a customer cannot read another customer's order")
    void cannotReadOthersOrder() {
        Order order = seed(CUSTOMER, "CHOCO-1");
        assertThatThrownBy(() -> service.ownOrder(OTHER, order.id()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("reading a missing order is a not-found")
    void missingOrder() {
        assertThatThrownBy(() -> service.ownOrder(CUSTOMER, UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("order history only returns the caller's own orders")
    void historyIsScopedToCaller() {
        seed(CUSTOMER, "CHOCO-1");
        seed(CUSTOMER, "CHOCO-2");
        seed(OTHER, "CHOCO-3");

        var history = service.ownHistory(CUSTOMER, PageQuery.of(0, 20, null, null));

        assertThat(history.content()).hasSize(2);
        assertThat(history.content()).extracting("orderNumber").containsExactlyInAnyOrder("CHOCO-1",
                "CHOCO-2");
    }

    @Test
    @DisplayName("an administrator can read any order")
    void adminReadsAny() {
        Order order = seed(CUSTOMER, "CHOCO-1");
        assertThat(service.forAdmin(order.id()).id()).isEqualTo(order.id());
    }

    @Test
    @DisplayName("the admin search filters by status and search term")
    void adminSearch() {
        seed(CUSTOMER, "CHOCO-1");
        seed(CUSTOMER, "CHOCO-2");

        var bySearch = service.searchForAdmin(null, null, "CHOCO-1", PageQuery.of(0, 20, null, null));
        assertThat(bySearch.content()).hasSize(1);

        var byStatus = service.searchForAdmin(List.of(OrderStatus.CANCELLED), null, null,
                PageQuery.of(0, 20, null, null));
        assertThat(byStatus.content()).isEmpty();
    }

    @Test
    @DisplayName("the counters report the seller's workload")
    void counters() {
        seed(CUSTOMER, "CHOCO-1");
        seed(CUSTOMER, "CHOCO-2");

        var counters = service.counters();

        assertThat(counters.total()).isEqualTo(2);
        assertThat(counters.paymentPending()).isEqualTo(2);
        assertThat(counters.delivered()).isZero();
    }

    private Order seed(UUID customerId, String orderNumber) {
        Order order = Order.place(UUID.randomUUID(), orderNumber, customerId,
                new CustomerSnapshot("Asha Rao", EmailAddress.of("asha@example.com"),
                        PhoneNumber.of("9876543210")),
                new DeliveryAddress("Asha Rao", PhoneNumber.of("9876543210"), "12 Cocoa Lane", null,
                        "Bengaluru", "Karnataka", Pincode.of("560001"), null),
                List.of(new OrderLine(PRODUCT, VARIANT, "Dark Chocolate", "200 g", "SKU-1",
                        Money.inr(49900), Quantity.of(1))),
                Money.inr(5000), Instant.parse("2026-01-01T10:00:00Z"));
        return orders.save(order);
    }
}
