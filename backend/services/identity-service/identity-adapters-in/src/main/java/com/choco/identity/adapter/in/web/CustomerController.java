package com.choco.identity.adapter.in.web;

import com.choco.identity.application.dto.CustomerView;
import com.choco.identity.application.service.CustomerQueryService;
import com.choco.security.principal.AuthenticatedUser;
import com.choco.security.principal.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer profile endpoints.
 *
 * <p>The path carries the customer id, and every handler re-checks ownership against the token
 * rather than trusting the id in the URL. Without that check, changing the id would read another
 * customer's record; the check is delegated to
 * {@link CurrentUserProvider#requireOwnerOrAdmin(UUID)} so the rule is written once.
 */
@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "Customers", description = "Customer profile")
class CustomerController {

    private final CustomerQueryService customerQueryService;
    private final CurrentUserProvider currentUserProvider;

    CustomerController(CustomerQueryService customerQueryService,
                       CurrentUserProvider currentUserProvider) {
        this.customerQueryService = customerQueryService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/{customerId}")
    @Operation(summary = "Read a customer's own profile")
    ResponseEntity<CustomerView> profile(@PathVariable UUID customerId) {
        AuthenticatedUser caller = currentUserProvider.requireOwnerOrAdmin(customerId);
        CustomerView view = caller.isAdmin()
                ? customerQueryService.findForAdmin(customerId)
                : customerQueryService.findOwnProfile(customerId);
        return ResponseEntity.ok(view);
    }

    @GetMapping("/me")
    @Operation(summary = "Read the signed-in customer's profile")
    ResponseEntity<CustomerView> me() {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(customerQueryService.findOwnProfile(caller.userId()));
    }
}
