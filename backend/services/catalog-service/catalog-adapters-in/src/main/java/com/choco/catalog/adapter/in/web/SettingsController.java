package com.choco.catalog.adapter.in.web;

import com.choco.catalog.application.command.UpdateSettingsCommand;
import com.choco.catalog.application.dto.SettingsView;
import com.choco.catalog.application.service.WebsiteSettingsService;
import com.choco.security.principal.AuthenticatedUser;
import com.choco.security.principal.CurrentUserProvider;
import com.choco.security.principal.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Website settings: the seller's contact details and the published policies.
 *
 * <p>The read is public because a visitor must be able to see the contact details and the refund and
 * shipping policies before ordering — that is the point of publishing them. The write needs
 * {@code settings:write}, a permission only a super administrator holds, because these fields are the
 * business's legal statements about itself.
 */
@RestController
@RequestMapping("/api/v1/settings")
@Tag(name = "Website settings", description = "Seller information and published policies")
class SettingsController {

    private final WebsiteSettingsService settingsService;
    private final CurrentUserProvider currentUserProvider;

    SettingsController(WebsiteSettingsService settingsService, CurrentUserProvider currentUserProvider) {
        this.settingsService = settingsService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    @Operation(summary = "Read the published website settings")
    ResponseEntity<SettingsView> get() {
        return ResponseEntity.ok(settingsService.get());
    }

    @PutMapping
    @PreAuthorize("hasAuthority('" + Roles.PERM_SETTINGS_WRITE + "')")
    @Operation(summary = "Update the website settings")
    ResponseEntity<SettingsView> update(@Valid @RequestBody SettingsRequest request) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(settingsService.update(caller.userId(), new UpdateSettingsCommand(
                request.sellerName(), request.contactEmail(), request.contactPhone(),
                request.contactAddress(), request.privacyPolicy(), request.termsAndConditions(),
                request.shippingPolicy(), request.refundPolicy())));
    }
}
