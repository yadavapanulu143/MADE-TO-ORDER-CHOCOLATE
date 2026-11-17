import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { App } from './App';
import { AuthProvider } from './auth/AuthContext';
import { CartProvider } from './cart/CartContext';
import { stubFetch } from './test/helpers';
import { SETTINGS } from './test/fixtures';

function renderApp(route: string) {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <AuthProvider>
        <CartProvider>
          <App />
        </CartProvider>
      </AuthProvider>
    </MemoryRouter>,
  );
}

function publicRoutes() {
  return {
    'GET /api/v1/settings': { body: SETTINGS },
    'GET /api/v1/carts/me': { status: 404, body: { detail: 'no cart' } },
    'GET /api/v1/products': { body: { content: [], page: 0, size: 12, totalElements: 0 } },
  };
}

describe('App routing', () => {
  it('renders the home page at the root', async () => {
    stubFetch(publicRoutes());

    renderApp('/');

    expect(await screen.findByRole('heading', { name: /made only after you pay/i })).toBeInTheDocument();
  });

  it('renders the shop on /products', async () => {
    stubFetch(publicRoutes());

    renderApp('/products');

    expect(await screen.findByRole('heading', { name: 'Shop chocolate' })).toBeInTheDocument();
  });

  it('renders the policies page on /policies', async () => {
    stubFetch(publicRoutes());

    renderApp('/policies');

    expect(await screen.findByRole('heading', { name: 'Policies' })).toBeInTheDocument();
  });

  it('renders the not-found page for an unknown path', async () => {
    stubFetch(publicRoutes());

    renderApp('/does-not-exist');

    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument();
  });

  it('sends a signed-out visitor from a protected path to sign in', async () => {
    stubFetch(publicRoutes());

    renderApp('/profile');

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
  });
});
