import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { OrdersPage } from './OrdersPage';
import { renderWithProviders, signedIn, stubFetch } from '../test/helpers';

const ORDER = {
  id: 'o1',
  orderNumber: 'CHOCO-1001',
  totalItems: 2,
  totalMinorUnits: 94900,
  total: '₹949.00',
  currency: 'INR',
  status: 'CONFIRMED',
  paymentStatus: 'PAID',
  createdAt: '2026-03-01T10:00:00Z',
};

describe('OrdersPage', () => {
  it('lists orders with their status and total', async () => {
    signedIn();
    stubFetch({
      'GET /api/v1/admin/orders': {
        body: { content: [ORDER], page: 0, size: 20, totalElements: 1 },
      },
    });

    renderWithProviders(<OrdersPage />, { route: '/orders' });

    expect(await screen.findByText('CHOCO-1001')).toBeInTheDocument();
    expect(screen.getByText('₹949.00')).toBeInTheDocument();
    expect(screen.getByText('Paid')).toBeInTheDocument();
  });

  it('shows an empty state when nothing matches', async () => {
    signedIn();
    stubFetch({
      'GET /api/v1/admin/orders': { body: { content: [], page: 0, size: 20, totalElements: 0 } },
    });

    renderWithProviders(<OrdersPage />, { route: '/orders' });

    expect(await screen.findByText('No orders match')).toBeInTheDocument();
  });

  it('sends the status filter to the server', async () => {
    signedIn();
    const calls = stubFetch({
      'GET /api/v1/admin/orders': { body: { content: [], page: 0, size: 20, totalElements: 0 } },
    });

    renderWithProviders(<OrdersPage />, { route: '/orders?status=PAYMENT_FAILED' });

    await screen.findByText('No orders match');
    expect(calls[0]?.url).toContain('status=PAYMENT_FAILED');
  });
});
