package com.choco.security.principal;

/**
 * Authority names. Held as constants so a typo in a string literal cannot silently widen or close
 * access to an endpoint.
 */
public final class Roles {

    public static final String ROLE_PREFIX = "ROLE_";

    public static final String CUSTOMER = "CUSTOMER";
    public static final String ADMIN = "ADMIN";
    public static final String SUPER_ADMIN = "SUPER_ADMIN";

    /** Fine-grained admin permissions. ADMIN holds the day-to-day set; SUPER_ADMIN holds all. */
    public static final String PERM_CUSTOMER_EXPORT = "customer:export";
    public static final String PERM_PRODUCT_WRITE = "product:write";
    public static final String PERM_ORDER_WRITE = "order:write";
    public static final String PERM_PAYMENT_REFUND = "payment:refund";
    public static final String PERM_DELIVERY_WRITE = "delivery:write";
    public static final String PERM_PRODUCTION_WRITE = "production:write";
    public static final String PERM_REPORT_READ = "report:read";
    public static final String PERM_SETTINGS_WRITE = "settings:write";
    public static final String PERM_ADMIN_MANAGE = "admin:manage";
    /** Reading the notification delivery log, and re-sending a message that failed. */
    public static final String PERM_NOTIFICATION_READ = "notification:read";
    public static final String PERM_NOTIFICATION_WRITE = "notification:write";

    private Roles() {
    }
}
