package com.choco.identity.adapter.in.web;

import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.identity.application.dto.CustomerSummaryView;
import com.choco.identity.application.dto.CustomerView;
import com.choco.identity.application.service.CustomerQueryService;
import com.choco.security.principal.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer administration for the seller.
 *
 * <p>Every method requires the {@code customer:read} permission, declared on the class with
 * {@code @PreAuthorize} rather than left to the URL pattern alone, so a new endpoint cannot be added
 * without a visible access decision. This is deliberately a read-only surface: the seller can find and
 * inspect an account, but there is no endpoint here that edits a customer's identity, password or
 * verification state.
 *
 * <p>The response never carries credential material. The list uses {@link CustomerSummaryView} and the
 * single read reuses the same {@link CustomerView} the customer sees of themselves, neither of which
 * has a field for a password hash, a token or an OTP.
 */
@RestController
@RequestMapping("/api/v1/admin/customers")
@PreAuthorize("hasAuthority('" + Roles.PERM_CUSTOMER_READ + "')")
@Tag(name = "Customer administration", description = "Find and inspect customer accounts")
class AdminCustomerController {

    private final CustomerQueryService customerQueryService;

    AdminCustomerController(CustomerQueryService customerQueryService) {
        this.customerQueryService = customerQueryService;
    }

    @GetMapping
    @Operation(summary = "List customers, newest first, with an optional search over name, email or mobile")
    ResponseEntity<PageResult<CustomerSummaryView>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(customerQueryService.listForAdmin(search,
                PageQuery.of(page, size, null, null)));
    }

    @GetMapping("/{customerId}")
    @Operation(summary = "Read a customer's profile, including their saved addresses")
    ResponseEntity<CustomerView> get(@PathVariable UUID customerId) {
        return ResponseEntity.ok(customerQueryService.findForAdmin(customerId));
    }
}
