// Types mirror the backend admin DTOs. They are hand-written rather than generated
// so a wire change is a visible edit here, and every field the UI reads is named.

export interface PageResult<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
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

export interface TokenPair {
  accessToken: string;
  refreshToken: string;
  accessTokenExpiresAt: string;
  refreshTokenExpiresAt: string;
  tokenType: string;
}

export interface AdminView {
  id: string;
  fullName: string;
  email: string;
  role: string;
  permissions: string[];
  active: boolean;
  createdAt: string;
}

export interface AdminAuthenticationResult {
  admin: AdminView;
  tokens: TokenPair;
}

export interface ReportMetric {
  key: string;
  label: string;
  value: unknown;
}

export interface DashboardView {
  metrics: ReportMetric[];
  recentOrders: string[];
  pendingRefunds: string[];
}

export interface ReportView {
  report: string;
  from: string;
  to: string;
  metrics: ReportMetric[];
  columns: string[];
  rows: unknown[][];
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

export interface OrderCounters {
  total: number;
  paymentPending: number;
  awaitingPreparation: number;
  inPreparation: number;
  preparationOnHold: number;
  preparationCompleted: number;
  packaging: number;
  readyForDispatch: number;
  outForDelivery: number;
  delivered: number;
  paymentFailed: number;
  cancelled: number;
  refundUnderReview: number;
}

export interface PaymentSummary {
  id: string;
  orderId: string;
  amountMinorUnits: number;
  amount: string;
  currency: string;
  status: string;
  method: string | null;
  createdAt: string;
}

export interface Refund {
  id: string;
  amountMinorUnits: number;
  amount: string;
  status: string;
  reason: string | null;
  providerRefundId: string | null;
  failureReason: string | null;
  requestedAt: string;
  processedAt: string | null;
}

export interface PaymentAttempt {
  id: string;
  status: string;
  failureReason: string | null;
  attemptedAt: string;
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
  attempts: PaymentAttempt[];
}

export interface PaymentCounters {
  total: number;
  paid: number;
  pending: number;
  failed: number;
  refunded: number;
  disputed: number;
}

export interface Notification {
  id: string;
  type: string;
  channel: string;
  recipientType: string;
  recipientMasked: string;
  subject: string;
  status: string;
  attempts: number;
  lastError: string | null;
  referenceId: string | null;
  createdAt: string;
  sentAt: string | null;
}

export interface NotificationCounters {
  pending: number;
  sent: number;
  failed: number;
  deadLetter: number;
  suppressed: number;
}

export interface ProductionJob {
  id: string;
  orderId: string;
  orderNumber: string;
  customerId: string;
  productId: string;
  variantId: string;
  variantLabel: string;
  quantity: number;
  status: string;
  holdReason: string | null;
  ingredientsConsumed: boolean;
  receivedAt: string;
  updatedAt: string;
  notes: OrderNote[];
}

export interface ProductionQueueCounters {
  received: number;
  awaitingPreparation: number;
  inPreparation: number;
  preparationCompleted: number;
  packaging: number;
  readyForDispatch: number;
  onHold: number;
  cancelled: number;
  unitsOccupyingCapacity: number;
  total: number;
}

export interface DeliveryAddress {
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

export interface DeliveryAttempt {
  id: string;
  outcome: string;
  reason: string | null;
  notes: string | null;
  attemptedAt: string;
}

export interface Delivery {
  id: string;
  orderId: string;
  orderNumber: string;
  customerId: string;
  address: DeliveryAddress;
  pincode: string;
  deliveryChargeMinorUnits: number;
  deliveryCharge: string;
  currency: string;
  status: string;
  deliveryPersonName: string | null;
  deliveryPersonPhone: string | null;
  dispatchedAt: string | null;
  deliveredAt: string | null;
  proofReference: string | null;
  attemptCount: number;
  createdAt: string;
  updatedAt: string;
  attempts: DeliveryAttempt[];
}

export interface DeliveryCounters {
  pending: number;
  assigned: number;
  outForDelivery: number;
  delivered: number;
  attemptFailed: number;
  rescheduled: number;
  cancelled: number;
  returned: number;
  total: number;
}

export interface Ingredient {
  id: string;
  name: string;
  unit: string;
  onHand: number;
  onHandMilliUnits: number;
  minimumLevel: number;
  minimumMilliUnits: number;
  lowStock: boolean;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface StockLedgerEntry {
  id: string;
  ingredientId: string;
  reason: string;
  delta: number;
  deltaMilliUnits: number;
  balanceAfter: number;
  balanceAfterMilliUnits: number;
  productionJobId: string | null;
  note: string | null;
  occurredAt: string;
}

export interface RecipeLine {
  ingredientId: string;
  ingredientName: string;
  unit: string;
  perUnit: number;
  perUnitMilliUnits: number;
}

export interface Recipe {
  variantId: string;
  lines: RecipeLine[];
}

export interface ServiceableArea {
  id: string;
  pincodePrefix: string;
  areaName: string | null;
  deliveryChargeMinorUnits: number;
  deliveryCharge: string;
  currency: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
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

export interface ServiceabilityQuote {
  pincode: string;
  serviceable: boolean;
  message: string;
  deliveryChargeMinorUnits: number;
  deliveryCharge: string;
  currency: string;
}

export interface CustomerAddress {
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

export interface CustomerView {
  id: string;
  fullName: string;
  email: string;
  mobile: string;
  emailVerified: boolean;
  mobileVerified: boolean;
  fullyVerified: boolean;
  active: boolean;
  registeredAt: string;
  addresses: CustomerAddress[];
}

/** A row in the seller's paged customer list: no addresses, no credential material. */
export interface CustomerSummaryView {
  id: string;
  fullName: string;
  email: string;
  mobile: string;
  emailVerified: boolean;
  mobileVerified: boolean;
  fullyVerified: boolean;
  active: boolean;
  registeredAt: string;
}
