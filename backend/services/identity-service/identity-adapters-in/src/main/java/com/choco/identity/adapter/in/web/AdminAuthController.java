package com.choco.identity.adapter.in.web;

import com.choco.identity.application.command.AdminLoginCommand;
import com.choco.identity.application.dto.AdminAuthenticationResult;
import com.choco.identity.application.service.AdminAuthenticationService;
import com.choco.web.client.ClientIpResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrator authentication endpoints.
 *
 * <p>Mounted under {@code /api/v1/auth/admin} and, like the customer sign-in endpoints, reachable
 * without a token — that is the point of a login. Everything that acts on administrative data lives
 * elsewhere and requires a token whose {@code role} is an administrator role.
 *
 * <p>There is no registration endpoint here by design. Administrators are provisioned from
 * configuration; an open admin sign-up would defeat the authorisation model.
 */
@RestController
@RequestMapping("/api/v1/auth/admin")
@Tag(name = "Admin authentication", description = "Administrator sign-in and sessions")
class AdminAuthController {

    private final AdminAuthenticationService adminAuthenticationService;
    private final ClientIpResolver clientIpResolver;

    AdminAuthController(AdminAuthenticationService adminAuthenticationService,
                        ClientIpResolver clientIpResolver) {
        this.adminAuthenticationService = adminAuthenticationService;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in as an administrator")
    ResponseEntity<AdminAuthenticationResult> login(@Valid @RequestBody AdminLoginRequest request,
                                                    HttpServletRequest httpRequest) {
        AdminAuthenticationResult result = adminAuthenticationService.login(new AdminLoginCommand(
                request.email(), request.password(), clientIpResolver.resolve(httpRequest),
                httpRequest.getHeader("User-Agent")));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange an administrator refresh token for a new token pair")
    ResponseEntity<AdminAuthenticationResult> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(adminAuthenticationService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke an administrator refresh token")
    ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        adminAuthenticationService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
