package com.choco.identity.adapter.in.web;

import com.choco.identity.application.command.LoginCommand;
import com.choco.identity.application.command.RegisterCustomerCommand;
import com.choco.identity.application.command.VerifyOtpCommand;
import com.choco.identity.application.dto.AuthenticationResult;
import com.choco.identity.application.dto.RegistrationResult;
import com.choco.identity.application.service.AuthenticationService;
import com.choco.identity.application.service.PasswordResetService;
import com.choco.identity.application.service.RegistrationService;
import com.choco.identity.application.service.VerificationService;
import com.choco.web.client.ClientIpResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints.
 *
 * <p>Registration returns {@code 202 Accepted}, not {@code 201 Created}, and deliberately issues no
 * tokens. The account exists but is not usable until both channels are verified, so a token at this
 * point would let an unverified customer reach checkout and contradict the rule that verification is
 * mandatory.
 *
 * <p>Password reset requests always return {@code 202} with the same body whether or not the address
 * is registered; the service is responsible for the uniform behaviour and this endpoint must not
 * reintroduce a difference.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Registration, verification and sessions")
class AuthController {

    private final RegistrationService registrationService;
    private final VerificationService verificationService;
    private final AuthenticationService authenticationService;
    private final PasswordResetService passwordResetService;
    private final ClientIpResolver clientIpResolver;

    AuthController(RegistrationService registrationService,
                   VerificationService verificationService,
                   AuthenticationService authenticationService,
                   PasswordResetService passwordResetService,
                   ClientIpResolver clientIpResolver) {
        this.registrationService = registrationService;
        this.verificationService = verificationService;
        this.authenticationService = authenticationService;
        this.passwordResetService = passwordResetService;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a customer and send both verification codes")
    ResponseEntity<RegistrationResult> register(@Valid @RequestBody RegisterCustomerRequest request,
                                                HttpServletRequest httpRequest) {
        RegistrationResult result = registrationService.register(new RegisterCustomerCommand(
                request.fullName(), request.email(), request.mobile(), request.password(),
                clientIpResolver.resolve(httpRequest)));
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/customers/" + result.customerId()))
                .body(result);
    }

    @PostMapping("/customers/{customerId}/verifications")
    @Operation(summary = "Verify an email or mobile code")
    ResponseEntity<Void> verify(@PathVariable String customerId,
                                @Valid @RequestBody VerifyOtpRequest request,
                                HttpServletRequest httpRequest) {
        String channel = channelFrom(httpRequest);
        verificationService.verify(new VerifyOtpCommand(customerId, channel, request.code()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/customers/{customerId}/verifications/resend")
    @Operation(summary = "Resend a verification code, subject to the resend throttle")
    ResponseEntity<Void> resend(@PathVariable String customerId,
                               @Valid @RequestBody ResendOtpRequest request) {
        registrationService.resendChallenge(customerId,
                com.choco.identity.domain.customer.VerificationChannel.valueOf(request.channel().toUpperCase()));
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in with an email address or mobile number")
    ResponseEntity<AuthenticationResult> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest httpRequest) {
        AuthenticationResult result = authenticationService.login(new LoginCommand(
                request.identifier(), request.password(), clientIpResolver.resolve(httpRequest),
                httpRequest.getHeader("User-Agent")));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new token pair")
    ResponseEntity<AuthenticationResult> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authenticationService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke a refresh token")
    ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authenticationService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password-reset/request")
    @Operation(summary = "Request a password reset link")
    ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody RequestPasswordResetRequest request,
                                              HttpServletRequest httpRequest) {
        passwordResetService.requestReset(request.email(), clientIpResolver.resolve(httpRequest));
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/password-reset/complete")
    @Operation(summary = "Complete a password reset with a token")
    ResponseEntity<Void> completePasswordReset(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    /**
     * The channel is read from a query parameter rather than the path so that the verification
     * endpoint keeps the same shape for both channels while the code itself is a body field.
     */
    private String channelFrom(HttpServletRequest request) {
        String channel = request.getParameter("channel");
        if (channel == null || channel.isBlank()) {
            throw new com.choco.core.error.ValidationException(
                    com.choco.core.error.ErrorCode.VALIDATION_FAILED,
                    "The channel query parameter is required (EMAIL or MOBILE)");
        }
        return channel;
    }

}
