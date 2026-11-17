import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { DashboardPage } from './DashboardPage';
import { renderWithProviders, signedIn, stubFetch } from '../test/helpers';

describe('DashboardPage', () => {
  it('renders the seller metrics and operational counters', async () => {
    signedIn();
    stubFetch({
      'GET /api/v1/dashboard': {
        body: {
          metrics: [
            { key: 'revenue', label: 'Revenue', value: '₹12,000.00' },
            { key: 'orders', label: 'Orders', value: 42 },
          ],
          recentOrders: ['CHOCO-1002'],
          pendingRefunds: ['REF-1'],
        },
      },
      'GET /api/v1/admin/orders/counters': {
        body: {
          total: 42,
          paymentPending: 3,
          awaitingPreparation: 1,
          inPreparation: 2,
          preparationOnHold: 0,
          preparationCompleted: 0,
          packaging: 0,
          readyForDispatch: 1,
          outForDelivery: 1,
          delivered: 30,
          paymentFailed: 2,
          cancelled: 1,
          refundUnderReview: 1,
        },
      },
      'GET /api/v1/admin/fulfilment/production/counters': {
        body: {
          received: 1,
          awaitingPreparation: 1,
          inPreparation: 2,
          preparationCompleted: 0,
          packaging: 0,
          readyForDispatch: 1,
          onHold: 0,
          cancelled: 0,
          unitsOccupyingCapacity: 3,
          total: 5,
        },
      },
      'GET /api/v1/admin/fulfilment/deliveries/counters': {
        body: {
          pending: 1,
          assigned: 0,
          outForDelivery: 1,
          delivered: 30,
          attemptFailed: 2,
          rescheduled: 0,
          cancelled: 0,
          returned: 0,
          total: 34,
        },
      },
    });

    renderWithProviders(<DashboardPage />, { route: '/' });

    expect(await screen.findByText('Revenue')).toBeInTheDocument();
    expect(screen.getByText('₹12,000.00')).toBeInTheDocument();
    expect(screen.getByText('In the kitchen')).toBeInTheDocument();
    expect(screen.getByText('CHOCO-1002')).toBeInTheDocument();
  });

  it('shows an error notice when the dashboard cannot be loaded', async () => {
    signedIn();
    stubFetch({
      'GET /api/v1/dashboard': { status: 500, body: { detail: 'Reporting is unavailable' } },
    });

    renderWithProviders(<DashboardPage />, { route: '/' });

    expect(await screen.findByText('Reporting is unavailable')).toBeInTheDocument();
  });
});
