package com.choco.identity.application.service;

import com.choco.identity.domain.admin.AdminRole;
import com.choco.security.principal.Roles;
import java.util.Set;

/**
 * Maps an administrator's role to the fine-grained permissions carried in the access token.
 *
 * <p>Defined here rather than in the domain so the domain stays free of the security module. The
 * token carries permissions rather than a bare role because a downstream service can then ask "may
 * this caller export customers?" without having to know what an {@code ADMIN} is, and the mapping
 * can change without redeploying every other service.
 */
final class AdminPermissions {

    private AdminPermissions() {
    }

    static Set<String> forRole(AdminRole role) {
        if (role.isSuperAdmin()) {
            return Set.of(
                    Roles.PERM_CUSTOMER_EXPORT,
                    Roles.PERM_PRODUCT_WRITE,
                    Roles.PERM_ORDER_WRITE,
                    Roles.PERM_PAYMENT_REFUND,
                    Roles.PERM_DELIVERY_WRITE,
                    Roles.PERM_PRODUCTION_WRITE,
                    Roles.PERM_REPORT_READ,
                    Roles.PERM_SETTINGS_WRITE,
                    Roles.PERM_ADMIN_MANAGE,
                    Roles.PERM_NOTIFICATION_READ,
                    Roles.PERM_NOTIFICATION_WRITE);
        }
        return Set.of(
                Roles.PERM_CUSTOMER_EXPORT,
                Roles.PERM_PRODUCT_WRITE,
                Roles.PERM_ORDER_WRITE,
                Roles.PERM_DELIVERY_WRITE,
                Roles.PERM_PRODUCTION_WRITE,
                Roles.PERM_REPORT_READ,
                Roles.PERM_NOTIFICATION_READ,
                Roles.PERM_NOTIFICATION_WRITE);
    }
}
