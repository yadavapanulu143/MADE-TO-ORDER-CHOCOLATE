import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CartPage } from './CartPage';
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

describe('CartPage', () => {
  it('asks a signed-out visitor to sign in', async () => {
    stubFetch({});
    renderWithProviders(<CartPage />);

    expect(await screen.findByText('Sign in to see your cart')).toBeInTheDocument();
  });

  it('lists the cart lines with their totals', async () => {
    signIn();
    stubFetch({
      'GET /api/v1/customers/me': { body: CUSTOMER },
      'GET /api/v1/carts/me': { body: CART },
    });

    renderWithProviders(<CartPage />);

    expect(await screen.findByText('Dark Chocolate Bar')).toBeInTheDocument();
    expect(screen.getByText('₹900.00')).toBeInTheDocument();
  });

  it('shows an empty state for an empty cart', async () => {
    signIn();
    stubFetch({
      'GET /api/v1/customers/me': { body: CUSTOMER },
      'GET /api/v1/carts/me': { body: { ...CART, lines: [], totalItems: 0 } },
    });

    renderWithProviders(<CartPage />);

    expect(await screen.findByText('Your cart is empty')).toBeInTheDocument();
  });

  it('sends a quantity update to the backend', async () => {
    signIn();
    const calls = stubFetch({
      'GET /api/v1/customers/me': { body: CUSTOMER },
      'GET /api/v1/carts/me': { body: CART },
      'PUT /api/v1/carts/me/lines/v1': {
        body: { ...CART, lines: [{ ...CART.lines[0], quantity: 3, lineTotalMinorUnits: 135000 }], totalItems: 3 },
      },
    });
    const user = userEvent.setup();

    renderWithProviders(<CartPage />);
    await screen.findByText('Dark Chocolate Bar');
    await user.click(screen.getByRole('button', { name: 'Increase quantity of Dark Chocolate Bar' }));

    await waitFor(() => {
      const update = calls.find((call) => call.method === 'PUT');
      expect(update?.body).toEqual({ quantity: 3 });
    });
  });

  it('removes a line from the cart', async () => {
    signIn();
    const calls = stubFetch({
      'GET /api/v1/customers/me': { body: CUSTOMER },
      'GET /api/v1/carts/me': { body: CART },
      'DELETE /api/v1/carts/me/lines/v1': { body: { ...CART, lines: [], totalItems: 0 } },
    });
    const user = userEvent.setup();

    renderWithProviders(<CartPage />);
    await screen.findByText('Dark Chocolate Bar');
    await user.click(screen.getByRole('button', { name: 'Remove' }));

    await waitFor(() => {
      expect(calls.some((call) => call.method === 'DELETE')).toBe(true);
    });
  });

  it('blocks checkout for a customer who has not verified both channels', async () => {
    signIn();
    stubFetch({
      'GET /api/v1/customers/me': { body: { ...CUSTOMER, mobileVerified: false, fullyVerified: false } },
      'GET /api/v1/carts/me': { body: CART },
    });

    renderWithProviders(<CartPage />);

    expect(await screen.findByText('Verify your email and mobile number to check out.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Checkout' })).not.toBeInTheDocument();
  });

  it('offers checkout to a fully verified customer', async () => {
    signIn();
    stubFetch({
      'GET /api/v1/customers/me': { body: CUSTOMER },
      'GET /api/v1/carts/me': { body: CART },
    });

    renderWithProviders(<CartPage />);

    expect(await screen.findByRole('button', { name: 'Checkout' })).toBeInTheDocument();
  });
});
