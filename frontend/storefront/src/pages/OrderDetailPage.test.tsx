import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { OrderDetailPage } from './OrderDetailPage';
import { renderAtRoute, stubFetch } from '../test/helpers';
import { writeTokens } from '../auth/tokenStore';
import type { Order, TokenPair } from '../api/types';

function signIn() {
  const tokens: TokenPair = {
    accessToken: 'access-1',
    refreshToken: 'refresh-1',
    accessTokenExpiresAt: new Date(Date.now() + 60_000).toISOString(),
    refreshTokenExpiresAt: new Date(Date.now() + 86_400_000).toISOString(),
    tokenType: 'Bearer',
  };
  writeTokens(tokens);
}

const CUSTOMER = {
  id: 'c1',
  fullName: 'Asha Rao',
  email: 'asha@example.com',
  mobile: '9876543210',
  emailVerified: true,
  mobileVerified: true,
  fullyVerified: true,
  active: true,
  registeredAt: '2026-01-01T00:00:00Z',
  addresses: [],
};

const ORDER: Order = {
  id: 'o1',
  orderNumber: 'CHOCO-1001',
  customerId: 'c1',
  customerName: 'Asha Rao',
  customerEmail: 'asha@example.com',
  customerMobile: '9876543210',
  deliveryAddress: {
    recipientName: 'Asha Rao',
    mobile: '9876543210',
    line1: '1 Cocoa Lane',
    line2: null,
    city: 'Bengaluru',
    state: 'Karnataka',
    pincode: '560001',
    deliveryInstructions: null,
    formatted: '1 Cocoa Lane, Bengaluru, Karnataka — 560001',
  },
  lines: [
    {
      productId: 'p1',
      variantId: 'v1',
      productName: 'Dark Chocolate Bar',
      variantLabel: '100 g',
      sku: 'DARK-100',
      unitPriceMinorUnits: 45000,
      unitPrice: '₹450.00',
      quantity: 2,
      lineTotalMinorUnits: 90000,
      lineTotal: '₹900.00',
    },
  ],
  totalItems: 2,
  itemsTotalMinorUnits: 90000,
  itemsTotal: '₹900.00',
  deliveryChargeMinorUnits: 4900,
  deliveryCharge: '₹49.00',
  totalMinorUnits: 94900,
  total: '₹949.00',
  currency: 'INR',
  status: 'PLACED',
  paymentStatus: 'PENDING',
  preparationStatus: 'NOT_STARTED',
  deliveryStatus: 'NOT_STARTED',
  paymentId: null,
  providerOrderId: 'order_provider_1',
  deliveryPersonName: null,
  dispatchedAt: null,
  deliveredAt: null,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
  statusHistory: [{ fromStatus: null, toStatus: 'PLACED', reason: null, occurredAt: '2026-01-01T00:00:00Z' }],
  notes: [],
  cancellable: true,
};

function routes(overrides: Record<string, { body?: unknown; status?: number }> = {}) {
  return {
    'GET /api/v1/customers/me': { body: CUSTOMER },
    'GET /api/v1/orders/me/o1': { body: ORDER },
    'GET /api/v1/fulfilment/me/orders/o1/preparation': { body: [] },
    'GET /api/v1/fulfilment/me/orders/o1/delivery': { status: 404, body: { detail: 'no delivery yet' } },
    'GET /api/v1/payments/me/orders/o1': { status: 404, body: { detail: 'no payment yet' } },
    'GET /api/v1/invoices/me/by-order/o1': { status: 404, body: { detail: 'no invoice yet' } },
    ...overrides,
  };
}

describe('OrderDetailPage', () => {
  it('shows the order lines, totals and address', async () => {
    signIn();
    stubFetch(routes());

    renderAtRoute(<OrderDetailPage />, { route: '/orders/o1', path: '/orders/:orderId' });

    expect(await screen.findByText('Order CHOCO-1001')).toBeInTheDocument();
    expect(screen.getByText('Dark Chocolate Bar')).toBeInTheDocument();
    expect(screen.getByText('₹949.00')).toBeInTheDocument();
    expect(screen.getByText('1 Cocoa Lane, Bengaluru, Karnataka — 560001')).toBeInTheDocument();
  });

  it('prompts for payment while the order is unpaid', async () => {
    signIn();
    stubFetch(routes());

    renderAtRoute(<OrderDetailPage />, { route: '/orders/o1', path: '/orders/:orderId' });

    expect(
      await screen.findByText('This order is awaiting payment. Preparation has not started.'),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Pay now' })).toBeInTheDocument();
  });

  it('says payment was received once the order is paid', async () => {
    signIn();
    stubFetch(
      routes({
        'GET /api/v1/orders/me/o1': { body: { ...ORDER, status: 'PAYMENT_CONFIRMED', paymentStatus: 'PAID', cancellable: false } },
      }),
    );

    renderAtRoute(<OrderDetailPage />, { route: '/orders/o1', path: '/orders/:orderId' });

    expect(
      await screen.findByText('Payment received. Your chocolate is being made to order.'),
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Pay now' })).not.toBeInTheDocument();
  });

  it('cancels the order with the reason the customer typed', async () => {
    signIn();
    const calls = stubFetch(
      routes({
        'POST /api/v1/orders/me/o1/cancel': {
          body: { ...ORDER, status: 'CANCELLED', cancellable: false },
        },
      }),
    );
    const user = userEvent.setup();

    renderAtRoute(<OrderDetailPage />, { route: '/orders/o1', path: '/orders/:orderId' });
    await screen.findByText('Order CHOCO-1001');
    await user.click(screen.getByRole('button', { name: 'Cancel order' }));
    await user.type(screen.getByLabelText('Reason for cancelling'), 'Found a better gift');
    await user.click(screen.getByRole('button', { name: 'Confirm cancellation' }));

    await waitFor(() => {
      const cancel = calls.find((call) => call.url.endsWith('/cancel'));
      expect(cancel?.body).toEqual({ reason: 'Found a better gift' });
    });
  });

  it('shows preparation and delivery progress the seller recorded', async () => {
    signIn();
    stubFetch(
      routes({
        'GET /api/v1/orders/me/o1': { body: { ...ORDER, paymentStatus: 'PAID', status: 'IN_PREPARATION' } },
        'GET /api/v1/fulfilment/me/orders/o1/preparation': {
          body: [
            {
              id: 'job-1',
              orderId: 'o1',
              orderNumber: 'CHOCO-1001',
              variantLabel: '100 g',
              quantity: 2,
              status: 'IN_PREPARATION',
              holdReason: null,
              receivedAt: '2026-01-01T01:00:00Z',
              updatedAt: '2026-01-01T02:00:00Z',
              notes: [],
            },
          ],
        },
        'GET /api/v1/fulfilment/me/orders/o1/delivery': {
          body: {
            id: 'd1',
            orderId: 'o1',
            orderNumber: 'CHOCO-1001',
            status: 'DISPATCHED',
            deliveryPersonName: 'Ravi',
            deliveryChargeMinorUnits: 4900,
            deliveryCharge: '₹49.00',
            currency: 'INR',
            dispatchedAt: '2026-01-02T09:00:00Z',
            deliveredAt: null,
            attemptCount: 0,
            createdAt: '2026-01-02T08:00:00Z',
            updatedAt: '2026-01-02T09:00:00Z',
          },
        },
      }),
    );

    renderAtRoute(<OrderDetailPage />, { route: '/orders/o1', path: '/orders/:orderId' });

    expect(await screen.findByText('In Preparation')).toBeInTheDocument();
    expect(screen.getByText('Dispatched')).toBeInTheDocument();
    expect(screen.getByText('Handled by Ravi')).toBeInTheDocument();
  });

  it('shows the invoice link once an invoice exists', async () => {
    signIn();
    stubFetch(
      routes({
        'GET /api/v1/invoices/me/by-order/o1': {
          body: { id: 'inv-1', invoiceNumber: 'INV-2026-0001' },
        },
      }),
    );

    renderAtRoute(<OrderDetailPage />, { route: '/orders/o1', path: '/orders/:orderId' });

    expect(await screen.findByRole('link', { name: 'View invoice INV-2026-0001' })).toBeInTheDocument();
  });

  it('reports an error when the order cannot be loaded', async () => {
    signIn();
    stubFetch(routes({ 'GET /api/v1/orders/me/o1': { status: 404, body: { detail: 'Order not found' } } }));

    renderAtRoute(<OrderDetailPage />, { route: '/orders/o1', path: '/orders/:orderId' });

    expect(await screen.findByText('Order not found')).toBeInTheDocument();
  });
});
