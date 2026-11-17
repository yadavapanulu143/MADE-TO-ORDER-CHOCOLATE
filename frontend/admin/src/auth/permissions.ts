// The fine-grained admin permissions the backend issues in the access token. The
// dashboard uses them only to hide controls the server would refuse anyway.
export const Permissions = {
  CUSTOMER_READ: 'customer:read',
  CUSTOMER_EXPORT: 'customer:export',
  PRODUCT_WRITE: 'product:write',
  ORDER_WRITE: 'order:write',
  PAYMENT_REFUND: 'payment:refund',
  DELIVERY_WRITE: 'delivery:write',
  PRODUCTION_WRITE: 'production:write',
  REPORT_READ: 'report:read',
  SETTINGS_WRITE: 'settings:write',
  ADMIN_MANAGE: 'admin:manage',
  NOTIFICATION_READ: 'notification:read',
  NOTIFICATION_WRITE: 'notification:write',
} as const;
