package com.choco.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.error.NotFoundException;
import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.identity.application.dto.CustomerSummaryView;
import com.choco.identity.application.fake.InMemoryAddressRepository;
import com.choco.identity.application.fake.InMemoryCustomerRepository;
import com.choco.identity.domain.customer.ContactDetails;
import com.choco.identity.domain.customer.Customer;
import com.choco.identity.domain.customer.VerificationChannel;
import com.choco.test.Fakes;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests the seller's customer read side.
 *
 * <p>The two risks are a search that leaks every account (an unescaped or ignored term) and a summary
 * that carries a field it should not. {@link Search} pins the term reaching the query and the page
 * bounds, and {@link Listing} asserts the summary carries only the recognition fields.
 */
@DisplayName("CustomerQueryService")
class CustomerQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-01T09:00:00Z");

    private InMemoryCustomerRepository customers;
    private CustomerQueryService service;
    private final UUID asha = UUID.randomUUID();
    private final UUID bhavna = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        customers = new InMemoryCustomerRepository();
        service = new CustomerQueryService(customers, new InMemoryAddressRepository());
    }

    private Customer seed(UUID id, String name, String email, String mobile, Instant registeredAt) {
        Customer customer = Customer.register(id, ContactDetails.of(name, email, mobile),
                "$argon2id$hash", registeredAt);
        customer.verify(VerificationChannel.EMAIL, registeredAt);
        customers.save(customer);
        return customer;
    }

    @Nested
    @DisplayName("listing")
    class Listing {

        @Test
        void returns_every_customer_when_no_term_is_given() {
            seed(asha, "Asha Rao", "asha@example.com", "9876543210", NOW);
            seed(bhavna, "Bhavna Iyer", "bhavna@example.com", "9876543211", NOW.plusSeconds(60));

            PageResult<CustomerSummaryView> page = service.listForAdmin(null, PageQuery.of(0, 20, null, null));

            assertThat(page.totalElements()).isEqualTo(2);
            assertThat(page.content()).hasSize(2);
        }

        @Test
        void newest_customer_comes_first() {
            seed(asha, "Asha Rao", "asha@example.com", "9876543210", NOW);
            seed(bhavna, "Bhavna Iyer", "bhavna@example.com", "9876543211", NOW.plusSeconds(60));

            PageResult<CustomerSummaryView> page = service.listForAdmin(null, PageQuery.of(0, 20, null, null));

            assertThat(page.content().get(0).id()).isEqualTo(bhavna);
        }

        @Test
        void the_summary_carries_only_the_recognition_fields() {
            seed(asha, "Asha Rao", "asha@example.com", "9876543210", NOW);

            CustomerSummaryView row = service.listForAdmin(null, PageQuery.of(0, 20, null, null))
                    .content().get(0);

            assertThat(row.fullName()).isEqualTo("Asha Rao");
            assertThat(row.email()).isEqualTo("asha@example.com");
            assertThat(row.mobile()).isEqualTo("9876543210");
            assertThat(row.emailVerified()).isTrue();
            assertThat(row.fullyVerified()).isFalse();
            assertThat(row.active()).isTrue();
            // The record has no field for a hash, a token or an OTP, so a leak is a compile error.
            assertThat(row.toString()).doesNotContain("argon2id");
        }

        @Test
        void paging_bounds_the_page() {
            for (int i = 0; i < 5; i++) {
                seed(UUID.randomUUID(), "Customer " + i, "c" + i + "@example.com",
                        "98765432" + (10 + i), NOW.plusSeconds(i));
            }

            PageResult<CustomerSummaryView> first = service.listForAdmin(null, PageQuery.of(0, 2, null, null));
            PageResult<CustomerSummaryView> second = service.listForAdmin(null, PageQuery.of(1, 2, null, null));

            assertThat(first.content()).hasSize(2);
            assertThat(first.totalElements()).isEqualTo(5);
            assertThat(second.content()).hasSize(2);
            assertThat(first.content()).doesNotContainAnyElementsOf(second.content());
        }
    }

    @Nested
    @DisplayName("search")
    class Search {

        @Test
        void matches_a_partial_name_case_insensitively() {
            seed(asha, "Asha Rao", "asha@example.com", "9876543210", NOW);
            seed(bhavna, "Bhavna Iyer", "bhavna@example.com", "9876543211", NOW.plusSeconds(60));

            PageResult<CustomerSummaryView> page =
                    service.listForAdmin("asha", PageQuery.of(0, 20, null, null));

            assertThat(page.content()).extracting(CustomerSummaryView::id).containsExactly(asha);
        }

        @Test
        void matches_by_email_and_by_mobile() {
            seed(asha, "Asha Rao", "asha@example.com", "9876543210", NOW);
            seed(bhavna, "Bhavna Iyer", "bhavna@example.com", "9876543211", NOW.plusSeconds(60));

            assertThat(service.listForAdmin("bhavna@", PageQuery.of(0, 20, null, null)).content())
                    .extracting(CustomerSummaryView::id).containsExactly(bhavna);
            assertThat(service.listForAdmin("9876543210", PageQuery.of(0, 20, null, null)).content())
                    .extracting(CustomerSummaryView::id).containsExactly(asha);
        }

        @Test
        void a_term_that_matches_nothing_returns_an_empty_page() {
            seed(asha, "Asha Rao", "asha@example.com", "9876543210", NOW);

            PageResult<CustomerSummaryView> page =
                    service.listForAdmin("nobody", PageQuery.of(0, 20, null, null));

            assertThat(page.content()).isEmpty();
            assertThat(page.totalElements()).isZero();
        }
    }

    @Nested
    @DisplayName("single read")
    class SingleRead {

        @Test
        void an_unknown_customer_is_a_404() {
            assertThatThrownBy(() -> service.findForAdmin(UUID.randomUUID()))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        void reads_the_profile_of_a_known_customer() {
            seed(asha, "Asha Rao", "asha@example.com", "9876543210", NOW);

            assertThat(service.findForAdmin(asha).email()).isEqualTo("asha@example.com");
        }
    }
}
