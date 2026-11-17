import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CheckoutPage } from './CheckoutPage';
import { renderWithProviders, stubFetch } from '../test/helpers';
import { writeTokens } from '../auth/tokenStore';
import type { TokenPair } from '../api/types';

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

const ADDRESS = {
  id: 'a1',
  recipientName: 'Asha Rao',
  mobile: '9876543210',
  line1: '1 Cocoa Lane',
  line2: null,
  city: 'Bengaluru',
  state: 'Karnataka',
  pincode: '560001',
  deliveryInstructions: null,
  isDefault: true,
};

const CART = {
  cartId: 'cart-1',
  lines: [
    {
      productId: 'p1',
      variantId: 'v1',
      productName: 'Dark Chocolate Bar',
      variantLabel: '100 g',
      unitPriceMinorUnits: 45000,
      unitPrice: '₹450.00',
      quantity: 2,
      lineTotalMinorUnits: 90000,
      lineTotal: '₹900.00',
    },
  ],
  totalItems: 2,
  estimatedTotalMinorUnits: 90000,
  estimatedTotal: '₹900.00',
  currency: 'INR',
  updatedAt: '2026-01-01T00:00:00Z',
};

const QUOTE = {
  serviceable: true,
  serviceabilityMessage: 'We deliver here',
  lines: CART.lines,
  totalItems: 2,
  itemsTotalMinorUnits: 90000,
  itemsTotal: '₹900.00',
  deliveryChargeMinorUnits: 4900,
  deliveryCharge: '₹49.00',
  totalMinorUnits: 94900,
  total: '₹949.00',
  currency: 'INR',
};

function baseRoutes(overrides: Record<string, { body?: unknown; status?: number }> = {}) {
  return {
    'GET /api/v1/customers/me': { body: CUSTOMER },
    'GET /api/v1/carts/me': { body: CART },
    'GET /api/v1/customers/me/addresses': { body: [ADDRESS] },
    'GET /api/v1/checkout/quote': { body: QUOTE },
    ...overrides,
  };
}

describe('CheckoutPage', () => {
  it('prices the cart against the selected address', async () => {
    signIn();
    stubFetch(baseRoutes());

    renderWithProviders(<CheckoutPage />);

    expect(await screen.findByText('₹949.00')).toBeInTheDocument();
    expect(screen.getByText('₹49.00')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Place order and pay' })).toBeInTheDocument();
  });

  it('refuses to place an order for an unserviceable PIN code', async () => {
    signIn();
    stubFetch(
      baseRoutes({
        'GET /api/v1/checkout/quote': {
          body: { ...QUOTE, serviceable: false, serviceabilityMessage: 'We do not deliver to 999999' },
        },
      }),
    );

    renderWithProviders(<CheckoutPage />);

    expect(await screen.findByText('We do not deliver to 999999')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Place order and pay' })).not.toBeInTheDocument();
  });

  it('places the order with an idempotency key so a double-tap cannot double-charge', async () => {
    signIn();
    const calls = stubFetch(
      baseRoutes({
        'POST /api/v1/checkout': { body: { id: 'o1', orderNumber: 'CHOCO-1001' } },
      }),
    );
    const user = userEvent.setup();

    renderWithProviders(<CheckoutPage />);
    await screen.findByRole('button', { name: 'Place order and pay' });
    await user.click(screen.getByRole('button', { name: 'Place order and pay' }));

    await waitFor(() => {
      const placed = calls.find((call) => call.method === 'POST' && call.url.endsWith('/checkout'));
      expect(placed?.body).toEqual({ addressId: 'a1' });
      expect(placed?.headers['Idempotency-Key']).toBeTruthy();
    });
  });

  it('shows an empty state when there is nothing to check out', async () => {
    signIn();
    stubFetch(
      baseRoutes({
        'GET /api/v1/carts/me': { body: { ...CART, lines: [], totalItems: 0 } },
      }),
    );

    renderWithProviders(<CheckoutPage />);

    expect(await screen.findByText('Nothing to check out')).toBeInTheDocument();
  });
});
