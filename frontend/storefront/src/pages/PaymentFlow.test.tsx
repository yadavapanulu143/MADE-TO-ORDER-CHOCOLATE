import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { OrderDetailPage } from './OrderDetailPage';
import { renderAtRoute, stubFetch } from '../test/helpers';
import { writeTokens } from '../auth/tokenStore';
import { CUSTOMER, ORDER, SETTINGS, TOKENS } from '../test/fixtures';
import type { RazorpayOptions } from '../lib/razorpay';

// A stand-in for the provider's checkout: it records the options the storefront
// passed and lets the test drive the success handler, exactly as the real widget
// would when the customer pays.
class FakeRazorpay {
  static last: FakeRazorpay | null = null;
  options: RazorpayOptions;
  constructor(options: RazorpayOptions) {
    this.options = options;
    FakeRazorpay.last = this;
  }
  open() {
    /* the test calls the handler directly */
  }
  on() {
    /* no-op */
  }
}

beforeEach(() => {
  FakeRazorpay.last = null;
});

function orderRoutes(overrides: Record<string, { body?: unknown; status?: number }> = {}) {
  return {
    'GET /api/v1/customers/me': { body: CUSTOMER },
    'GET /api/v1/carts/me': { status: 404, body: { detail: 'no cart' } },
    'GET /api/v1/settings': { body: SETTINGS },
    'GET /api/v1/orders/me/o1': { body: ORDER },
    'GET /api/v1/fulfilment/me/orders/o1/preparation': { body: [] },
    'GET /api/v1/fulfilment/me/orders/o1/delivery': { status: 404, body: { detail: 'none' } },
    'GET /api/v1/payments/me/orders/o1': { status: 404, body: { detail: 'none' } },
    'GET /api/v1/invoices/me/by-order/o1': { status: 404, body: { detail: 'none' } },
    ...overrides,
  };
}

describe('payment initiation and verification', () => {
  it('opens checkout with the publishable key and the server-set amount', async () => {
    writeTokens(TOKENS);
    stubFetch(
      orderRoutes({
        'POST /api/v1/orders/me/o1/payment': {
          body: {
            provider: 'razorpay',
            providerOrderId: 'order_provider_1',
            amountMinorUnits: 94900,
            amount: '₹949.00',
            currency: 'INR',
            receipt: 'CHOCO-1001',
            publicKey: 'rzp_test_public_key',
          },
        },
      }),
    );
    (window as { Razorpay?: unknown }).Razorpay = FakeRazorpay;
    const user = userEvent.setup();

    renderAtRoute(<OrderDetailPage />, { route: '/orders/o1', path: '/orders/:orderId' });
    await user.click(await screen.findByRole('button', { name: 'Pay now' }));

    await waitFor(() => {
      expect(FakeRazorpay.last?.options.key).toBe('rzp_test_public_key');
    });
    // The amount is the order's own total from the server, never a browser number.
    expect(FakeRazorpay.last?.options.amount).toBe(94900);
    expect(FakeRazorpay.last?.options.order_id).toBe('order_provider_1');
  });

  it('sends the provider response to the server, which verifies it', async () => {
    writeTokens(TOKENS);
    const calls = stubFetch(
      orderRoutes({
        'POST /api/v1/orders/me/o1/payment': {
          body: {
            provider: 'razorpay',
            providerOrderId: 'order_provider_1',
            amountMinorUnits: 94900,
            amount: '₹949.00',
            currency: 'INR',
            receipt: 'CHOCO-1001',
            publicKey: 'rzp_test_public_key',
          },
        },
        'POST /api/v1/payments/me/orders/o1/verify': {
          body: { id: 'pay-1', status: 'PAID', amountMinorUnits: 94900, currency: 'INR' },
        },
      }),
    );
    (window as { Razorpay?: unknown }).Razorpay = FakeRazorpay;
    const user = userEvent.setup();

    renderAtRoute(<OrderDetailPage />, { route: '/orders/o1', path: '/orders/:orderId' });
    await user.click(await screen.findByRole('button', { name: 'Pay now' }));
    await waitFor(() => expect(FakeRazorpay.last).not.toBeNull());

    FakeRazorpay.last!.options.handler({
      razorpay_payment_id: 'pay_provider_1',
      razorpay_order_id: 'order_provider_1',
      razorpay_signature: 'signature-abc',
    });

    await waitFor(() => {
      const verify = calls.find((call) => call.url.endsWith('/verify'));
      expect(verify?.body).toEqual({
        providerOrderId: 'order_provider_1',
        providerPaymentId: 'pay_provider_1',
        signature: 'signature-abc',
      });
    });
  });

  it('tells the customer when online payment is not configured, without faking success', async () => {
    writeTokens(TOKENS);
    stubFetch(
      orderRoutes({
        'POST /api/v1/orders/me/o1/payment': {
          body: {
            provider: 'unconfigured',
            providerOrderId: 'order_provider_1',
            amountMinorUnits: 94900,
            amount: '₹949.00',
            currency: 'INR',
            receipt: 'CHOCO-1001',
            publicKey: null,
          },
        },
      }),
    );
    (window as { Razorpay?: unknown }).Razorpay = FakeRazorpay;
    const user = userEvent.setup();

    renderAtRoute(<OrderDetailPage />, { route: '/orders/o1', path: '/orders/:orderId' });
    await user.click(await screen.findByRole('button', { name: 'Pay now' }));

    expect(await screen.findByText(/online payment is not configured/i)).toBeInTheDocument();
    expect(FakeRazorpay.last).toBeNull();
  });

  it('reports a payment window dismissed without paying', async () => {
    writeTokens(TOKENS);
    stubFetch(
      orderRoutes({
        'POST /api/v1/orders/me/o1/payment': {
          body: {
            provider: 'razorpay',
            providerOrderId: 'order_provider_1',
            amountMinorUnits: 94900,
            amount: '₹949.00',
            currency: 'INR',
            receipt: 'CHOCO-1001',
            publicKey: 'rzp_test_public_key',
          },
        },
      }),
    );
    (window as { Razorpay?: unknown }).Razorpay = FakeRazorpay;
    const user = userEvent.setup();

    renderAtRoute(<OrderDetailPage />, { route: '/orders/o1', path: '/orders/:orderId' });
    await user.click(await screen.findByRole('button', { name: 'Pay now' }));
    await waitFor(() => expect(FakeRazorpay.last).not.toBeNull());

    FakeRazorpay.last!.options.modal?.ondismiss?.();

    expect(
      await screen.findByText('Payment window closed before it was completed.'),
    ).toBeInTheDocument();
  });

  it('submits a refund request after payment', async () => {
    writeTokens(TOKENS);
    const calls = stubFetch(
      orderRoutes({
        'GET /api/v1/orders/me/o1': { body: { ...ORDER, paymentStatus: 'PAID', status: 'PAYMENT_CONFIRMED', cancellable: false } },
        'POST /api/v1/orders/me/o1/refund-request': { body: { ...ORDER, status: 'REFUND_REQUESTED' } },
      }),
    );
    const user = userEvent.setup();

    renderAtRoute(<OrderDetailPage />, { route: '/orders/o1', path: '/orders/:orderId' });
    await user.click(await screen.findByRole('button', { name: 'Request a refund' }));
    await user.type(screen.getByLabelText('Why do you need a refund?'), 'Arrived melted');
    await user.click(screen.getByRole('button', { name: 'Submit refund request' }));

    await waitFor(() => {
      const refund = calls.find((call) => call.url.endsWith('/refund-request'));
      expect(refund?.body).toEqual({ reason: 'Arrived melted' });
    });
  });

  it('surfaces a failed payment attempt as an error rather than a paid order', async () => {
    writeTokens(TOKENS);
    stubFetch(
      orderRoutes({
        'POST /api/v1/orders/me/o1/payment': { status: 500, body: { detail: 'Payment provider unavailable' } },
      }),
    );
    const user = userEvent.setup();

    renderAtRoute(<OrderDetailPage />, { route: '/orders/o1', path: '/orders/:orderId' });
    await user.click(await screen.findByRole('button', { name: 'Pay now' }));

    expect(await screen.findByText('Payment provider unavailable')).toBeInTheDocument();
    expect(screen.queryByText(/payment received/i)).not.toBeInTheDocument();
  });
});

describe('unmount safety', () => {
  it('does not warn when a request resolves after the page is gone', async () => {
    writeTokens(TOKENS);
    stubFetch(orderRoutes());
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

    const { unmount } = renderAtRoute(<OrderDetailPage />, { route: '/orders/o1', path: '/orders/:orderId' });
    await screen.findByText('Order CHOCO-1001');
    unmount();

    expect(errorSpy).not.toHaveBeenCalled();
  });
});
