import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { OrdersPage } from './OrdersPage';
import { renderWithProviders, stubFetch } from '../test/helpers';
import { writeTokens } from '../auth/tokenStore';
import { CUSTOMER, ORDER_SUMMARY, TOKENS } from '../test/fixtures';

describe('OrdersPage', () => {
  it('lists the customer\'s orders with their status', async () => {
    writeTokens(TOKENS);
    stubFetch({
      'GET /api/v1/customers/me': { body: CUSTOMER },
      'GET /api/v1/carts/me': { status: 404, body: { detail: 'no cart' } },
      'GET /api/v1/orders/me': { body: { content: [ORDER_SUMMARY], page: 0, size: 10, totalElements: 1 } },
    });

    renderWithProviders(<OrdersPage />, { route: '/orders' });

    expect(await screen.findByText('CHOCO-1001')).toBeInTheDocument();
    expect(screen.getByText('₹949.00')).toBeInTheDocument();
    expect(screen.getByText('Pending')).toBeInTheDocument();
  });

  it('shows an empty state when the customer has never ordered', async () => {
    writeTokens(TOKENS);
    stubFetch({
      'GET /api/v1/customers/me': { body: CUSTOMER },
      'GET /api/v1/carts/me': { status: 404, body: { detail: 'no cart' } },
      'GET /api/v1/orders/me': { body: { content: [], page: 0, size: 10, totalElements: 0 } },
    });

    renderWithProviders(<OrdersPage />, { route: '/orders' });

    expect(await screen.findByText('You have not ordered yet')).toBeInTheDocument();
  });
});
