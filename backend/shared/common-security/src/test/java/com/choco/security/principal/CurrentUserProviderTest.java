package com.choco.security.principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.error.DomainException;
import com.choco.core.error.ErrorCode;
import com.choco.core.error.ForbiddenException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CurrentUserProviderTest {

    private final CurrentUserProvider provider = new CurrentUserProvider();

    @AfterEach
    void clear() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void reports_no_user_when_context_is_empty() {
        assertThat(provider.currentUser()).isEmpty();
        assertThatThrownBy(provider::requireUser).isInstanceOf(DomainException.class);
    }

    @Test
    void builds_user_from_jwt_claims() {
        UUID id = UUID.randomUUID();
        authenticate(id, Roles.CUSTOMER, false, false, List.of());

        AuthenticatedUser user = provider.requireUser();
        assertThat(user.userId()).isEqualTo(id);
        assertThat(user.isCustomer()).isTrue();
        assertThat(user.fullyVerified()).isFalse();
    }

    @Test
    void customer_may_access_own_resource() {
        UUID id = UUID.randomUUID();
        authenticate(id, Roles.CUSTOMER, true, true, List.of());
        assertThat(provider.requireOwnerOrAdmin(id).userId()).isEqualTo(id);
    }

    @Test
    void customer_may_not_access_another_customers_resource() {
        authenticate(UUID.randomUUID(), Roles.CUSTOMER, true, true, List.of());
        assertThatThrownBy(() -> provider.requireOwnerOrAdmin(UUID.randomUUID()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void admin_may_access_any_resource() {
        authenticate(UUID.randomUUID(), Roles.ADMIN, true, true, List.of());
        assertThat(provider.requireOwnerOrAdmin(UUID.randomUUID())).isNotNull();
    }

    @Test
    void checkout_requires_both_verifications() {
        authenticate(UUID.randomUUID(), Roles.CUSTOMER, true, false, List.of());
        assertThatThrownBy(provider::requireFullyVerifiedCustomer)
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).errorCode())
                .isEqualTo(ErrorCode.MOBILE_NOT_VERIFIED);
    }

    @Test
    void permission_check_honours_granted_permissions() {
        authenticate(UUID.randomUUID(), Roles.ADMIN, true, true, List.of(Roles.PERM_CUSTOMER_EXPORT));
        assertThat(provider.requirePermission(Roles.PERM_CUSTOMER_EXPORT)).isNotNull();
        assertThatThrownBy(() -> provider.requirePermission(Roles.PERM_ADMIN_MANAGE))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void super_admin_holds_every_permission() {
        authenticate(UUID.randomUUID(), Roles.SUPER_ADMIN, true, true, List.of());
        assertThat(provider.requirePermission(Roles.PERM_ADMIN_MANAGE)).isNotNull();
    }

    private void authenticate(UUID userId, String role, boolean emailVerified, boolean mobileVerified,
                              List<String> permissions) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(userId.toString())
                .claim("email", "user@example.com")
                .claim("role", role)
                .claim("email_verified", emailVerified)
                .claim("mobile_verified", mobileVerified)
                .claim("permissions", permissions)
                .build();
        var authorities = Set.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                Roles.ROLE_PREFIX + role));
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt, authorities));
    }
}
