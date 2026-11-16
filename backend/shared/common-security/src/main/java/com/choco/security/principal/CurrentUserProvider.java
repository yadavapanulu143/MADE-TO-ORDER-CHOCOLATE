package com.choco.security.principal;

import com.choco.core.error.DomainException;
import com.choco.core.error.ErrorCode;
import com.choco.core.error.ForbiddenException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Reads the authenticated caller from the security context and enforces the ownership rule that a
 * customer may only act on their own resources.
 *
 * <p>Centralising the ownership check here is deliberate: if every controller re-implemented it, a
 * single missed comparison would expose another customer's order or address.
 */
@Component
public class CurrentUserProvider {

    public Optional<AuthenticatedUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }
        return Optional.of(toAuthenticatedUser(jwt));
    }

    public AuthenticatedUser requireUser() {
        return currentUser().orElseThrow(() ->
                new DomainException(ErrorCode.UNAUTHENTICATED, "Authentication is required"));
    }

    /**
     * Ensures the caller owns the resource, or is an administrator.
     *
     * @throws ForbiddenException when a customer tries to reach another customer's data
     */
    public AuthenticatedUser requireOwnerOrAdmin(UUID ownerId) {
        AuthenticatedUser user = requireUser();
        if (user.isAdmin()) {
            return user;
        }
        if (!user.userId().equals(ownerId)) {
            throw new ForbiddenException("You are not allowed to access this resource");
        }
        return user;
    }

    public AuthenticatedUser requirePermission(String permission) {
        AuthenticatedUser user = requireUser();
        if (!user.hasPermission(permission)) {
            throw new ForbiddenException("Missing required permission: " + permission);
        }
        return user;
    }

    /** Guards checkout-style actions that must not proceed until both OTP steps are done. */
    public AuthenticatedUser requireFullyVerifiedCustomer() {
        AuthenticatedUser user = requireUser();
        if (!user.isCustomer()) {
            throw new ForbiddenException("Only customers may perform this action");
        }
        if (!user.emailVerified()) {
            throw new DomainException(ErrorCode.EMAIL_NOT_VERIFIED, "Email address is not verified");
        }
        if (!user.mobileVerified()) {
            throw new DomainException(ErrorCode.MOBILE_NOT_VERIFIED, "Mobile number is not verified");
        }
        return user;
    }

    private AuthenticatedUser toAuthenticatedUser(Jwt jwt) {
        String role = jwt.getClaimAsString("role");
        return new AuthenticatedUser(
                UUID.fromString(jwt.getSubject()),
                jwt.getClaimAsString("email"),
                role == null ? Roles.CUSTOMER : role,
                Boolean.TRUE.equals(jwt.getClaim("email_verified")),
                Boolean.TRUE.equals(jwt.getClaim("mobile_verified")),
                permissions(jwt));
    }

    private Set<String> permissions(Jwt jwt) {
        List<String> claimed = jwt.getClaimAsStringList("permissions");
        return claimed == null ? Set.of() : Set.copyOf(claimed);
    }
}
