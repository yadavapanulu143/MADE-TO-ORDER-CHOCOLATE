import { api } from './client';
import type {
  Address,
  AddressInput,
  AuthenticationResult,
  Cart,
  CheckoutQuote,
  Customer,
  Delivery,
  Invoice,
  Order,
  OrderSummary,
  PageResult,
  Payment,
  PaymentInitiation,
  Product,
  ProductSummary,
  ProductionJob,
  RegistrationResult,
  Serviceability,
  Settings,
} from './types';

export const catalogApi = {
  listProducts: (params: { page?: number; size?: number; search?: string } = {}) => {
    const query = new URLSearchParams();
    if (params.page !== undefined) query.set('page', String(params.page));
    if (params.size !== undefined) query.set('size', String(params.size));
    if (params.search) query.set('search', params.search);
    const suffix = query.toString() ? `?${query.toString()}` : '';
    return api.get<PageResult<ProductSummary>>(`/api/v1/products${suffix}`, true);
  },
  getProduct: (slug: string) => api.get<Product>(`/api/v1/products/${encodeURIComponent(slug)}`, true),
  getSettings: () => api.get<Settings>('/api/v1/settings', true),
  serviceability: (pincode: string) =>
    api.get<Serviceability>(`/api/v1/serviceability/${encodeURIComponent(pincode)}`, true),
};

export const authApi = {
  register: (body: { fullName: string; email: string; mobile: string; password: string }) =>
    api.post<RegistrationResult>('/api/v1/auth/register', body),
  verify: (customerId: string, channel: 'EMAIL' | 'MOBILE', code: string) =>
    api.post<void>(
      `/api/v1/auth/customers/${encodeURIComponent(customerId)}/verifications?channel=${channel}`,
      { code },
    ),
  resend: (customerId: string, channel: 'EMAIL' | 'MOBILE') =>
    api.post<void>(`/api/v1/auth/customers/${encodeURIComponent(customerId)}/verifications/resend`, {
      channel,
    }),
  login: (identifier: string, password: string) =>
    api.post<AuthenticationResult>('/api/v1/auth/login', { identifier, password }),
  logout: (refreshToken: string) => api.post<void>('/api/v1/auth/logout', { refreshToken }),
  requestPasswordReset: (email: string) =>
    api.post<void>('/api/v1/auth/password-reset/request', { email }),
  completePasswordReset: (token: string, newPassword: string) =>
    api.post<void>('/api/v1/auth/password-reset/complete', { token, newPassword }),
};

export const customerApi = {
  me: () => api.get<Customer>('/api/v1/customers/me'),
  updateProfile: (body: { fullName: string }) => api.put<Customer>('/api/v1/customers/me', body),
  changeContact: (body: { email?: string; mobile?: string }) =>
    api.patch<Customer>('/api/v1/customers/me/contact', body),
  changePassword: (body: { currentPassword: string; newPassword: string }) =>
    api.post<void>('/api/v1/customers/me/password', body),
};

export const addressApi = {
  list: () => api.get<Address[]>('/api/v1/customers/me/addresses'),
  create: (body: AddressInput) => api.post<Address>('/api/v1/customers/me/addresses', body),
  update: (id: string, body: AddressInput) =>
    api.put<Address>(`/api/v1/customers/me/addresses/${id}`, body),
  makeDefault: (id: string) => api.post<Address>(`/api/v1/customers/me/addresses/${id}/default`),
  remove: (id: string) => api.del<void>(`/api/v1/customers/me/addresses/${id}`),
};

export const cartApi = {
  view: () => api.get<Cart>('/api/v1/carts/me'),
  addLine: (variantId: string, quantity: number) =>
    api.post<Cart>('/api/v1/carts/me/lines', { variantId, quantity }),
  updateLine: (variantId: string, quantity: number) =>
    api.put<Cart>(`/api/v1/carts/me/lines/${variantId}`, { quantity }),
  removeLine: (variantId: string) => api.del<Cart>(`/api/v1/carts/me/lines/${variantId}`),
  clear: () => api.del<Cart>('/api/v1/carts/me'),
};

export const checkoutApi = {
  quote: (addressId: string) =>
    api.get<CheckoutQuote>(`/api/v1/checkout/quote?addressId=${encodeURIComponent(addressId)}`),
  place: (addressId: string, idempotencyKey: string) =>
    api.post<Order>('/api/v1/checkout', { addressId }, { 'Idempotency-Key': idempotencyKey }),
};

export const orderApi = {
  list: (params: { page?: number; size?: number } = {}) => {
    const query = new URLSearchParams();
    if (params.page !== undefined) query.set('page', String(params.page));
    if (params.size !== undefined) query.set('size', String(params.size));
    const suffix = query.toString() ? `?${query.toString()}` : '';
    return api.get<PageResult<OrderSummary>>(`/api/v1/orders/me${suffix}`);
  },
  get: (orderId: string) => api.get<Order>(`/api/v1/orders/me/${orderId}`),
  cancel: (orderId: string, reason: string) =>
    api.post<Order>(`/api/v1/orders/me/${orderId}/cancel`, { reason }),
  requestRefund: (orderId: string, reason: string) =>
    api.post<Order>(`/api/v1/orders/me/${orderId}/refund-request`, { reason }),
};

export const paymentApi = {
  initiate: (orderId: string, idempotencyKey: string) =>
    api.post<PaymentInitiation>(`/api/v1/orders/me/${orderId}/payment`, undefined, {
      'Idempotency-Key': idempotencyKey,
    }),
  verify: (orderId: string, body: { providerOrderId: string; providerPaymentId: string; signature: string }) =>
    api.post<Payment>(`/api/v1/payments/me/orders/${orderId}/verify`, body),
  forOrder: (orderId: string) => api.get<Payment>(`/api/v1/payments/me/orders/${orderId}`),
};

export const fulfilmentApi = {
  preparation: (orderId: string) =>
    api.get<ProductionJob[]>(`/api/v1/fulfilment/me/orders/${orderId}/preparation`),
  delivery: (orderId: string) => api.get<Delivery>(`/api/v1/fulfilment/me/orders/${orderId}/delivery`),
};

export const invoiceApi = {
  list: () => api.get<Invoice[]>('/api/v1/invoices/me'),
  get: (invoiceId: string) => api.get<Invoice>(`/api/v1/invoices/me/${invoiceId}`),
  byOrder: (orderId: string) => api.get<Invoice>(`/api/v1/invoices/me/by-order/${orderId}`),
};
