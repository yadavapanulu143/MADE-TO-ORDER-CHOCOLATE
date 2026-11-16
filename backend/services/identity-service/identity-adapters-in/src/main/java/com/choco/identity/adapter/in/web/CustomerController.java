package com.choco.identity.adapter.in.web;

import com.choco.identity.application.command.ChangeContactCommand;
import com.choco.identity.application.command.ChangePasswordCommand;
import com.choco.identity.application.command.UpdateProfileCommand;
import com.choco.identity.application.dto.CustomerView;
import com.choco.identity.application.service.CustomerQueryService;
import com.choco.identity.application.service.PasswordChangeService;
import com.choco.identity.application.service.ProfileService;
import com.choco.security.principal.AuthenticatedUser;
import com.choco.security.principal.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer profile endpoints.
 *
 * <p>The path carries the customer id, and every handler re-checks ownership against the token
 * rather than trusting the id in the URL. Without that check, changing the id would read another
 * customer's record; the check is delegated to
 * {@link CurrentUserProvider#requireOwnerOrAdmin(UUID)} so the rule is written once.
 *
 * <p>The {@code /me} variants take the id from the token instead, so a customer editing their own
 * profile has no id to tamper with at all.
 */
@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "Customers", description = "Customer profile")
class CustomerController {

    private final CustomerQueryService customerQueryService;
    private final ProfileService profileService;
    private final PasswordChangeService passwordChangeService;
    private final CurrentUserProvider currentUserProvider;

    CustomerController(CustomerQueryService customerQueryService,
                       ProfileService profileService,
                       PasswordChangeService passwordChangeService,
                       CurrentUserProvider currentUserProvider) {
        this.customerQueryService = customerQueryService;
        this.profileService = profileService;
        this.passwordChangeService = passwordChangeService;
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

    @PutMapping("/me")
    @Operation(summary = "Update the signed-in customer's display name")
    ResponseEntity<CustomerView> updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(profileService.updateProfile(caller.userId(),
                new UpdateProfileCommand(request.fullName())));
    }

    /**
     * Changes the email and/or mobile number.
     *
     * <p>Each changed channel loses its verified flag and receives a fresh code, so the response
     * reflects an account that may no longer be fully verified. That is the honest answer: the new
     * address has not been proven yet.
     */
    @PatchMapping("/me/contact")
    @Operation(summary = "Change the signed-in customer's email or mobile number")
    ResponseEntity<CustomerView> changeContact(@Valid @RequestBody ChangeContactRequest request) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(profileService.changeContact(caller.userId(),
                new ChangeContactCommand(request.email(), request.mobile())));
    }

    /**
     * Changes the password and ends every other session.
     *
     * <p>Returns {@code 204} rather than the profile: the operation has no new representation to
     * hand back, and echoing the account would invite a client to treat the response as proof of
     * anything. The current password is required in the body, so a borrowed session alone is not
     * enough to take the account over.
     */
    @PostMapping("/me/password")
    @Operation(summary = "Change the signed-in customer's password")
    ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        passwordChangeService.changePassword(caller.userId(),
                new ChangePasswordCommand(request.currentPassword(), request.newPassword()));
        return ResponseEntity.noContent().build();
    }
}
