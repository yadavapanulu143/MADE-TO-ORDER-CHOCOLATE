import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { MemoryRouter } from 'react-router-dom';
import { render } from '@testing-library/react';
import { Layout } from './Layout';
import { ProtectedRoute } from './ProtectedRoute';
import { AuthProvider } from '../auth/AuthContext';
import { CartProvider } from '../cart/CartContext';
import { stubFetch } from '../test/helpers';
import { writeTokens } from '../auth/tokenStore';
import { CUSTOMER, SETTINGS, TOKENS } from '../test/fixtures';

function renderShell(initialPath = '/') {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <AuthProvider>
        <CartProvider>
          <Routes>
            <Route element={<Layout />}>
              <Route path="/" element={<p>Home body</p>} />
              <Route path="/login" element={<p>Login body</p>} />
              <Route element={<ProtectedRoute />}>
                <Route path="/orders" element={<p>Orders body</p>} />
              </Route>
            </Route>
          </Routes>
        </CartProvider>
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('Layout', () => {
  it('shows sign-in links to a visitor with no session', async () => {
    stubFetch({
      'GET /api/v1/settings': { body: SETTINGS },
      'GET /api/v1/carts/me': { status: 404, body: { detail: 'no cart' } },
    });

    renderShell();

    expect(await screen.findByText('Home body')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Sign in' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Register' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Orders' })).not.toBeInTheDocument();
  });

  it('shows the customer and a sign-out control once signed in', async () => {
    writeTokens(TOKENS);
    stubFetch({
      'GET /api/v1/settings': { body: SETTINGS },
      'GET /api/v1/customers/me': { body: CUSTOMER },
      'GET /api/v1/carts/me': { body: { cartId: 'c', lines: [], totalItems: 3, estimatedTotalMinorUnits: 0, estimatedTotal: '₹0.00', currency: 'INR', updatedAt: '2026-01-01T00:00:00Z' } },
    });

    renderShell();

    expect(await screen.findByText('Asha Rao')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Sign out' })).toBeInTheDocument();
    expect(screen.getByLabelText('Cart, 3 items')).toBeInTheDocument();
  });

  it('signs the customer out and returns to the home page', async () => {
    writeTokens(TOKENS);
    const calls = stubFetch({
      'GET /api/v1/settings': { body: SETTINGS },
      'GET /api/v1/customers/me': { body: CUSTOMER },
      'GET /api/v1/carts/me': { status: 404, body: { detail: 'no cart' } },
      'POST /api/v1/auth/logout': { status: 204 },
    });
    const user = userEvent.setup();

    renderShell();
    await user.click(await screen.findByRole('button', { name: 'Sign out' }));

    await waitFor(() => {
      expect(calls.some((call) => call.url.endsWith('/auth/logout'))).toBe(true);
    });
    expect(await screen.findByText('Home body')).toBeInTheDocument();
  });
});

describe('ProtectedRoute', () => {
  it('redirects a signed-out visitor to sign in', async () => {
    stubFetch({
      'GET /api/v1/settings': { body: SETTINGS },
      'GET /api/v1/carts/me': { status: 404, body: { detail: 'no cart' } },
    });

    renderShell('/orders');

    expect(await screen.findByText('Login body')).toBeInTheDocument();
    expect(screen.queryByText('Orders body')).not.toBeInTheDocument();
  });

  it('lets a signed-in customer through', async () => {
    writeTokens(TOKENS);
    stubFetch({
      'GET /api/v1/settings': { body: SETTINGS },
      'GET /api/v1/customers/me': { body: CUSTOMER },
      'GET /api/v1/carts/me': { status: 404, body: { detail: 'no cart' } },
    });

    renderShell('/orders');

    expect(await screen.findByText('Orders body')).toBeInTheDocument();
  });
});
