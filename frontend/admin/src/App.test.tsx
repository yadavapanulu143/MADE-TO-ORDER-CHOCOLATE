import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { App } from './App';
import { AdminAuthProvider } from './auth/AdminAuthContext';
import { signedIn, stubFetch } from './test/helpers';

function renderApp(route: string) {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <AdminAuthProvider>
        <App />
      </AdminAuthProvider>
    </MemoryRouter>,
  );
}

describe('App routing', () => {
  it('sends an unauthenticated visitor to the admin sign-in', async () => {
    stubFetch({});
    renderApp('/orders');

    expect(await screen.findByRole('heading', { name: 'Choco administration' })).toBeInTheDocument();
  });

  it('renders the dashboard at the root for a signed-in administrator', async () => {
    signedIn();
    stubFetch({
      'GET /api/v1/dashboard': { body: { metrics: [], recentOrders: [], pendingRefunds: [] } },
      'GET /api/v1/admin/orders/counters': { body: {} },
      'GET /api/v1/admin/fulfilment/production/counters': { body: {} },
      'GET /api/v1/admin/fulfilment/deliveries/counters': { body: {} },
    });

    renderApp('/');

    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument();
  });

  it('renders the products page for a signed-in administrator', async () => {
    signedIn();
    stubFetch({
      'GET /api/v1/admin/catalog/products': { body: { content: [], page: 0, size: 20, totalElements: 0 } },
    });

    renderApp('/products');

    expect(await screen.findByRole('heading', { name: 'Products' })).toBeInTheDocument();
  });

  it('renders the not-found page for an unknown route', async () => {
    signedIn();
    stubFetch({});
    renderApp('/nope');

    expect(await screen.findByText('Page not found')).toBeInTheDocument();
  });
});
