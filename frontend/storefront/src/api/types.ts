// Types mirror the backend DTOs. They are hand-written rather than generated so
// a wire change is a visible edit here, and every field the UI reads is named.

export interface PageResult<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
}

export interface ProductSummary {
  id: string;
  slug: string;
  name: string;
  shortDescription: string | null;
  fromPriceMinorUnits: number;
  fromPrice: string;
  currency: string;
  primaryImageUrl: string | null;
  variantCount: number;
  status: string;
  available: boolean;
}

export interface Variant {
  id: string;
  label: string;
  sku: string;
  priceMinorUnits: number;
  price: string;
  currency: string;
  minOrderQuantity: number;
  maxOrderQuantity: number;
  active: boolean;
}

export interface Image {
  id: string;
  url: string;
  altText: string | null;
  displayOrder: number;
  primary: boolean;
}

export interface Attribute {
  id: string;
  name: string;
  value: string;
  displayOrder: number;
}

export interface Product extends Omit<ProductSummary, 'primaryImageUrl' | 'variantCount'> {
  description: string | null;
  preparationInfo: string | null;
  deliveryInfo: string | null;
  variants: Variant[];
  images: Image[];
  attributes: Attribute[];
  createdAt: string;
  updatedAt: string;
}

export interface Settings {
  sellerName: string | null;
  contactEmail: string | null;
  contactPhone: string | null;
  contactAddress: string | null;
  privacyPolicy: string | null;
  termsAndConditions: string | null;
  shippingPolicy: string | null;
  refundPolicy: string | null;
  updatedAt: string | null;
}

export interface Serviceability {
  pincode: string;
  serviceable: boolean;
  message: string;
  deliveryChargeMinorUnits: number;
  deliveryCharge: string;
  currency: string;
}

export interface Address {
  id: string;
  recipientName: string;
  mobile: string;
  line1: string;
  line2: string | null;
  city: string;
  state: string;
  pincode: string;
  deliveryInstructions: string | null;
  isDefault: boolean;
}

export interface AddressInput {
  recipientName: string;
  mobile: string;
  line1: string;
  line2?: string;
  city: string;
  state: string;
  pincode: string;
  deliveryInstructions?: string;
  defaultAddress?: boolean;
}

export interface Customer {
  id: string;
  fullName: string;
  email: string;
  mobile: string;
  emailVerified: boolean;
  mobileVerified: boolean;
  fullyVerified: boolean;
  active: boolean;
  registeredAt: string;
  addresses: Address[];
}

export interface TokenPair {
  accessToken: string;
  refreshToken: string;
  accessTokenExpiresAt: string;
  refreshTokenExpiresAt: string;
  tokenType: string;
}

export interface AuthenticationResult {
  customer: Customer;
  tokens: TokenPair;
}

export interface RegistrationResult {
  customerId: string;
  email: string;
  mobile: string;
}

export interface CartLine {
  productId: string;
  variantId: string;
  productName: string;
  variantLabel: string;
  unitPriceMinorUnits: number;
  unitPrice: string;
  quantity: number;
  lineTotalMinorUnits: number;
  lineTotal: string;
}

export interface Cart {
  cartId: string;
  lines: CartLine[];
  totalItems: number;
  estimatedTotalMinorUnits: number;
  estimatedTotal: string;
  currency: string;
  updatedAt: string;
}

export interface OrderLine {
  productId: string;
  variantId: string;
  productName: string;
  variantLabel: string;
  sku: string;
  unitPriceMinorUnits: number;
  unitPrice: string;
  quantity: number;
  lineTotalMinorUnits: number;
  lineTotal: string;
}

export interface OrderAddress {
  recipientName: string;
  mobile: string;
  line1: string;
  line2: string | null;
  city: string;
  state: string;
  pincode: string;
  deliveryInstructions: string | null;
  formatted: string;
}

export interface OrderStatusChange {
  fromStatus: string | null;
  toStatus: string;
  reason: string | null;
  occurredAt: string;
}

export interface OrderNote {
  id: string;
  text: string;
  createdAt: string;
}

export interface OrderSummary {
  id: string;
  orderNumber: string;
  totalItems: number;
  totalMinorUnits: number;
  total: string;
  currency: string;
  status: string;
  paymentStatus: string;
  createdAt: string;
}

export interface Order extends OrderSummary {
  customerId: string;
  customerName: string;
  customerEmail: string;
  customerMobile: string;
  deliveryAddress: OrderAddress;
  lines: OrderLine[];
  itemsTotalMinorUnits: number;
  itemsTotal: string;
  deliveryChargeMinorUnits: number;
  deliveryCharge: string;
  preparationStatus: string;
  deliveryStatus: string;
  paymentId: string | null;
  providerOrderId: string | null;
  deliveryPersonName: string | null;
  dispatchedAt: string | null;
  deliveredAt: string | null;
  updatedAt: string;
  statusHistory: OrderStatusChange[];
  notes: OrderNote[];
  cancellable: boolean;
}

export interface CheckoutQuote {
  serviceable: boolean;
  serviceabilityMessage: string;
  lines: OrderLine[];
  totalItems: number;
  itemsTotalMinorUnits: number;
  itemsTotal: string;
  deliveryChargeMinorUnits: number;
  deliveryCharge: string;
  totalMinorUnits: number;
  total: string;
  currency: string;
}

export interface PaymentInitiation {
  provider: string;
  providerOrderId: string;
  amountMinorUnits: number;
  amount: string;
  currency: string;
  receipt: string | null;
  publicKey: string | null;
}

export interface Refund {
  id: string;
  amountMinorUnits: number;
  amount: string;
  status: string;
  reason: string | null;
  createdAt: string;
}

export interface Payment {
  id: string;
  orderId: string;
  customerId: string;
  amountMinorUnits: number;
  amount: string;
  currency: string;
  status: string;
  provider: string;
  providerOrderId: string | null;
  providerPaymentId: string | null;
  method: string | null;
  failureReason: string | null;
  refundedAmountMinorUnits: number;
  refundedAmount: string;
  createdAt: string;
  updatedAt: string;
  refunds: Refund[];
}

export interface InvoiceLine {
  productId: string;
  variantId: string;
  productName: string;
  variantLabel: string;
  sku: string;
  unitPriceMinorUnits: number;
  unitPrice: string;
  quantity: number;
  lineTotalMinorUnits: number;
  lineTotal: string;
}

export interface Invoice {
  id: string;
  invoiceNumber: string;
  orderId: string;
  orderNumber: string;
  customerId: string;
  customerName: string;
  customerEmail: string;
  customerMobile: string;
  billingAddress: OrderAddress;
  lines: InvoiceLine[];
  totalItems: number;
  itemsTotalMinorUnits: number;
  itemsTotal: string;
  deliveryChargeMinorUnits: number;
  deliveryCharge: string;
  totalMinorUnits: number;
  total: string;
  currency: string;
  issuedAt: string;
}

export interface ProductionJob {
  id: string;
  orderId: string;
  orderNumber: string;
  variantLabel: string;
  quantity: number;
  status: string;
  holdReason: string | null;
  receivedAt: string;
  updatedAt: string;
  notes: OrderNote[];
}

export interface Delivery {
  id: string;
  orderId: string;
  orderNumber: string;
  status: string;
  deliveryPersonName: string | null;
  deliveryChargeMinorUnits: number;
  deliveryCharge: string;
  currency: string;
  dispatchedAt: string | null;
  deliveredAt: string | null;
  attemptCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  code?: string;
  correlationId?: string;
  errors?: Record<string, string>;
}
