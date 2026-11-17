import { api, download, upload } from './client';
import type {
  CustomerSummaryView,
  CustomerView,
  AdminAuthenticationResult,
  DashboardView,
  Delivery,
  DeliveryCounters,
  Ingredient,
  Invoice,
  Notification,
  NotificationCounters,
  Order,
  OrderCounters,
  OrderSummary,
  PageResult,
  Payment,
  PaymentCounters,
  PaymentSummary,
  Product,
  ProductSummary,
  ProductionJob,
  ProductionQueueCounters,
  Recipe,
  ReportView,
  ServiceableArea,
  ServiceabilityQuote,
  Settings,
  StockLedgerEntry,
} from './types';

function pageQuery(params: {
  page?: number;
  size?: number;
  search?: string;
  sort?: string;
  ascending?: boolean;
  status?: string[];
  includeInactive?: boolean;
  customerId?: string;
}): string {
  const query = new URLSearchParams();
  if (params.page !== undefined) query.set('page', String(params.page));
  if (params.size !== undefined) query.set('size', String(params.size));
  if (params.search) query.set('search', params.search);
  if (params.sort) query.set('sort', params.sort);
  if (params.ascending !== undefined) query.set('ascending', String(params.ascending));
  if (params.customerId) query.set('customerId', params.customerId);
  if (params.includeInactive !== undefined) query.set('includeInactive', String(params.includeInactive));
  for (const status of params.status ?? []) query.append('status', status);
  const suffix = query.toString();
  return suffix ? `?${suffix}` : '';
}

export const adminAuthApi = {
  login: (email: string, password: string) =>
    api.post<AdminAuthenticationResult>('/api/v1/auth/admin/login', { email, password }, undefined),
  logout: (refreshToken: string) =>
    api.post<void>('/api/v1/auth/admin/logout', { refreshToken }),
};

export const dashboardApi = {
  dashboard: () => api.get<DashboardView>('/api/v1/dashboard'),
  report: (type: string, params: { from?: string; to?: string; days?: number } = {}) => {
    const query = new URLSearchParams();
    if (params.from) query.set('from', params.from);
    if (params.to) query.set('to', params.to);
    if (params.days !== undefined) query.set('days', String(params.days));
    const suffix = query.toString() ? `?${query.toString()}` : '';
    return api.get<ReportView>(`/api/v1/reports/${type}${suffix}`);
  },
  download: (type: string, format: 'csv' | 'xlsx', days?: number) => {
    const query = new URLSearchParams({ format });
    if (days !== undefined) query.set('days', String(days));
    return download(`/api/v1/exports/${type}?${query.toString()}`);
  },
};

export const orderApi = {
  list: (params: Parameters<typeof pageQuery>[0] = {}) =>
    api.get<PageResult<OrderSummary>>(`/api/v1/admin/orders${pageQuery(params)}`),
  counters: () => api.get<OrderCounters>('/api/v1/admin/orders/counters'),
  get: (orderId: string) => api.get<Order>(`/api/v1/admin/orders/${orderId}`),
  preparation: (orderId: string, action: string, reason?: string) =>
    api.post<Order>(`/api/v1/admin/orders/${orderId}/preparation`, { action, reason }),
  assignDelivery: (orderId: string, deliveryPersonName: string, deliveryPersonPhone?: string) =>
    api.post<Order>(`/api/v1/admin/orders/${orderId}/delivery/assign`, {
      deliveryPersonName,
      deliveryPersonPhone,
    }),
  delivery: (orderId: string, action: string, reason?: string) =>
    api.post<Order>(`/api/v1/admin/orders/${orderId}/delivery`, { action, reason }),
  addNote: (orderId: string, text: string) =>
    api.post<Order>(`/api/v1/admin/orders/${orderId}/notes`, { text }),
  cancel: (orderId: string, reason: string) =>
    api.post<Order>(`/api/v1/admin/orders/${orderId}/cancel`, { reason }),
  allowPaymentRetry: (orderId: string) =>
    api.post<Order>(`/api/v1/admin/orders/${orderId}/payment-retry`),
};

export const paymentApi = {
  list: (params: Parameters<typeof pageQuery>[0] = {}) =>
    api.get<PageResult<PaymentSummary>>(`/api/v1/admin/payments${pageQuery(params)}`),
  counters: () => api.get<PaymentCounters>('/api/v1/admin/payments/counters'),
  get: (paymentId: string) => api.get<Payment>(`/api/v1/admin/payments/${paymentId}`),
  refund: (paymentId: string, amountMinorUnits: number, reason: string) =>
    api.post<Payment>(`/api/v1/admin/payments/${paymentId}/refunds`, { amountMinorUnits, reason }),
};

export const notificationApi = {
  // The notification search endpoint names its text parameter `q`, not `search`, and
  // filters by a single type rather than a status list, so it builds its own query.
  list: (params: {
    page?: number;
    size?: number;
    search?: string;
    status?: string[];
    type?: string;
  } = {}) => {
    const query = new URLSearchParams();
    if (params.page !== undefined) query.set('page', String(params.page));
    if (params.size !== undefined) query.set('size', String(params.size));
    if (params.search) query.set('q', params.search);
    if (params.type) query.set('type', params.type);
    for (const status of params.status ?? []) query.append('status', status);
    const suffix = query.toString();
    return api.get<PageResult<Notification>>(
      `/api/v1/admin/notifications${suffix ? `?${suffix}` : ''}`,
    );
  },
  counters: () => api.get<NotificationCounters>('/api/v1/admin/notifications/counters'),
  get: (id: string) => api.get<Notification>(`/api/v1/admin/notifications/${id}`),
  resend: (id: string) => api.post<Notification>(`/api/v1/admin/notifications/${id}/resend`),
};

export const productionApi = {
  queue: (params: Parameters<typeof pageQuery>[0] = {}) =>
    api.get<PageResult<ProductionJob>>(`/api/v1/admin/fulfilment/production/jobs${pageQuery(params)}`),
  counters: () => api.get<ProductionQueueCounters>('/api/v1/admin/fulfilment/production/counters'),
  get: (jobId: string) => api.get<ProductionJob>(`/api/v1/admin/fulfilment/production/jobs/${jobId}`),
  forOrder: (orderId: string) =>
    api.get<ProductionJob[]>(`/api/v1/admin/fulfilment/production/orders/${orderId}`),
  advance: (jobId: string, action: string, reason?: string) =>
    api.post<ProductionJob>(`/api/v1/admin/fulfilment/production/jobs/${jobId}/progress`, {
      action,
      reason,
    }),
  addNote: (jobId: string, text: string) =>
    api.post<ProductionJob>(`/api/v1/admin/fulfilment/production/jobs/${jobId}/notes`, { text }),
};

export const inventoryApi = {
  list: (params: Parameters<typeof pageQuery>[0] = {}) =>
    api.get<PageResult<Ingredient>>(`/api/v1/admin/fulfilment/ingredients${pageQuery(params)}`),
  lowStock: () => api.get<Ingredient[]>('/api/v1/admin/fulfilment/ingredients/low-stock'),
  create: (body: { name: string; unit: string; openingLevel: number; minimumLevel: number }) =>
    api.post<Ingredient>('/api/v1/admin/fulfilment/ingredients', body),
  update: (id: string, body: { minimumLevel: number; active?: boolean }) =>
    api.put<Ingredient>(`/api/v1/admin/fulfilment/ingredients/${id}`, body),
  adjustStock: (id: string, body: { amount: number; reason: string; note?: string }) =>
    api.post<Ingredient>(`/api/v1/admin/fulfilment/ingredients/${id}/stock`, body),
  ledger: (id: string, params: { page?: number; size?: number } = {}) =>
    api.get<PageResult<StockLedgerEntry>>(
      `/api/v1/admin/fulfilment/ingredients/${id}/ledger${pageQuery(params)}`,
    ),
  recipe: (variantId: string) =>
    api.get<Recipe>(`/api/v1/admin/fulfilment/recipes/${variantId}`),
  configureRecipe: (variantId: string, lines: Array<{ ingredientId: string; perUnit: number }>) =>
    api.put<Recipe>(`/api/v1/admin/fulfilment/recipes/${variantId}`, { lines }),
};

export const deliveryApi = {
  list: (params: Parameters<typeof pageQuery>[0] = {}) =>
    api.get<PageResult<Delivery>>(`/api/v1/admin/fulfilment/deliveries${pageQuery(params)}`),
  counters: () => api.get<DeliveryCounters>('/api/v1/admin/fulfilment/deliveries/counters'),
  forOrder: (orderId: string) =>
    api.get<Delivery>(`/api/v1/admin/fulfilment/deliveries/orders/${orderId}`),
  assign: (orderId: string, body: { deliveryPersonName: string; deliveryPersonPhone?: string }) =>
    api.post<Delivery>(`/api/v1/admin/fulfilment/deliveries/orders/${orderId}/assign`, body),
  progress: (
    orderId: string,
    body: { action: string; reason?: string; proofReference?: string; notes?: string },
  ) => api.post<Delivery>(`/api/v1/admin/fulfilment/deliveries/orders/${orderId}/progress`, body),
  areas: (params: Parameters<typeof pageQuery>[0] = {}) =>
    api.get<PageResult<ServiceableArea>>(
      `/api/v1/admin/fulfilment/serviceable-areas${pageQuery(params)}`,
    ),
  createArea: (body: {
    pincodePrefix: string;
    areaName?: string;
    deliveryChargeMinorUnits: number;
    active?: boolean;
  }) => api.post<ServiceableArea>('/api/v1/admin/fulfilment/serviceable-areas', body),
  updateArea: (
    areaId: string,
    body: {
      pincodePrefix: string;
      areaName?: string;
      deliveryChargeMinorUnits: number;
      active?: boolean;
    },
  ) => api.put<ServiceableArea>(`/api/v1/admin/fulfilment/serviceable-areas/${areaId}`, body),
  quote: (pincode: string) =>
    api.get<ServiceabilityQuote>(
      `/api/v1/admin/fulfilment/serviceability/${encodeURIComponent(pincode)}`,
    ),
};

export const catalogAdminApi = {
  list: (params: Parameters<typeof pageQuery>[0] = {}) =>
    api.get<PageResult<ProductSummary>>(`/api/v1/admin/catalog/products${pageQuery(params)}`),
  get: (productId: string) => api.get<Product>(`/api/v1/admin/catalog/products/${productId}`),
  create: (body: {
    name: string;
    shortDescription?: string;
    description?: string;
    preparationInfo?: string;
    deliveryInfo?: string;
  }) => api.post<Product>('/api/v1/admin/catalog/products', body),
  update: (
    productId: string,
    body: {
      name: string;
      shortDescription?: string;
      description?: string;
      preparationInfo?: string;
      deliveryInfo?: string;
    },
  ) => api.put<Product>(`/api/v1/admin/catalog/products/${productId}`, body),
  publish: (productId: string) =>
    api.post<Product>(`/api/v1/admin/catalog/products/${productId}/publish`),
  unpublish: (productId: string) =>
    api.post<Product>(`/api/v1/admin/catalog/products/${productId}/unpublish`),
  archive: (productId: string) =>
    api.post<Product>(`/api/v1/admin/catalog/products/${productId}/archive`),
  addVariant: (
    productId: string,
    body: {
      label: string;
      sku?: string;
      price: number;
      minOrderQuantity: number;
      maxOrderQuantity: number;
    },
  ) => api.post<Product>(`/api/v1/admin/catalog/products/${productId}/variants`, body),
  updateVariant: (
    productId: string,
    variantId: string,
    body: {
      label: string;
      sku?: string;
      price: number;
      minOrderQuantity: number;
      maxOrderQuantity: number;
    },
  ) => api.put<Product>(`/api/v1/admin/catalog/products/${productId}/variants/${variantId}`, body),
  removeVariant: (productId: string, variantId: string) =>
    api.del<Product>(`/api/v1/admin/catalog/products/${productId}/variants/${variantId}`),
  uploadImage: (productId: string, file: File, altText?: string) => {
    const form = new FormData();
    form.append('file', file);
    const suffix = altText ? `?altText=${encodeURIComponent(altText)}` : '';
    return upload<Product>(`/api/v1/admin/catalog/products/${productId}/images${suffix}`, form);
  },
  setPrimaryImage: (productId: string, imageId: string) =>
    api.post<Product>(`/api/v1/admin/catalog/products/${productId}/images/${imageId}/primary`),
  removeImage: (productId: string, imageId: string) =>
    api.del<Product>(`/api/v1/admin/catalog/products/${productId}/images/${imageId}`),
  addAttribute: (productId: string, body: { name: string; value: string }) =>
    api.post<Product>(`/api/v1/admin/catalog/products/${productId}/attributes`, body),
  removeAttribute: (productId: string, attributeId: string) =>
    api.del<Product>(`/api/v1/admin/catalog/products/${productId}/attributes/${attributeId}`),
};

export const invoiceApi = {
  list: (params: Parameters<typeof pageQuery>[0] = {}) =>
    api.get<PageResult<Invoice>>(`/api/v1/admin/invoices${pageQuery(params)}`),
  get: (invoiceId: string) => api.get<Invoice>(`/api/v1/admin/invoices/${invoiceId}`),
  issueForOrder: (orderId: string) =>
    api.post<Invoice>(`/api/v1/admin/invoices/order/${orderId}`),
};

export const settingsApi = {
  get: () => api.get<Settings>('/api/v1/settings'),
  // updatedAt is the server's to set; the update request carries only editable fields.
  update: ({ updatedAt: _updatedAt, ...body }: Settings) =>
    api.put<Settings>('/api/v1/settings', body),
};

export const customerAdminApi = {
  list: (params: { page?: number; size?: number; search?: string } = {}) => {
    const query = new URLSearchParams();
    if (params.page !== undefined) query.set('page', String(params.page));
    if (params.size !== undefined) query.set('size', String(params.size));
    if (params.search) query.set('search', params.search);
    const suffix = query.toString();
    return api.get<PageResult<CustomerSummaryView>>(
      `/api/v1/admin/customers${suffix ? `?${suffix}` : ''}`,
    );
  },
  get: (customerId: string) => api.get<CustomerView>(`/api/v1/admin/customers/${customerId}`),
};
