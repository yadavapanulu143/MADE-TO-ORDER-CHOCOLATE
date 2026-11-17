import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { CustomersPage } from './CustomersPage';
import { renderWithProviders, signedIn, stubFetch } from '../test/helpers';

const CUSTOMER = {
  id: 'c1',
  fullName: 'Asha Rao',
  email: 'asha@example.com',
  mobile: '9876543210',
  emailVerified: true,
  mobileVerified: true,
  fullyVerified: true,
  active: true,
  registeredAt: '2026-03-01T10:00:00Z',
};

describe('CustomersPage', () => {
  it('lists customers with their verification state', async () => {
    signedIn();
    stubFetch({
      'GET /api/v1/admin/customers': {
        body: { content: [CUSTOMER], page: 0, size: 20, totalElements: 1 },
      },
    });

    renderWithProviders(<CustomersPage />, { route: '/customers' });

    expect(await screen.findByText('Asha Rao')).toBeInTheDocument();
    expect(screen.getByText('asha@example.com')).toBeInTheDocument();
    expect(screen.getByText('9876543210')).toBeInTheDocument();
    expect(screen.getByText('Email + mobile')).toBeInTheDocument();
  });

  it('shows an empty state when nothing matches', async () => {
    signedIn();
    stubFetch({
      'GET /api/v1/admin/customers': { body: { content: [], page: 0, size: 20, totalElements: 0 } },
    });

    renderWithProviders(<CustomersPage />, { route: '/customers' });

    expect(await screen.findByText('No customers match')).toBeInTheDocument();
  });

  it('reports a partly verified account honestly', async () => {
    signedIn();
    stubFetch({
      'GET /api/v1/admin/customers': {
        body: {
          content: [{ ...CUSTOMER, mobileVerified: false, fullyVerified: false }],
          page: 0,
          size: 20,
          totalElements: 1,
        },
      },
    });

    renderWithProviders(<CustomersPage />, { route: '/customers' });

    expect(await screen.findByText('Partly verified')).toBeInTheDocument();
  });

  it('never renders a password field', async () => {
    signedIn();
    stubFetch({
      'GET /api/v1/admin/customers': {
        body: { content: [CUSTOMER], page: 0, size: 20, totalElements: 1 },
      },
    });

    renderWithProviders(<CustomersPage />, { route: '/customers' });

    await screen.findByText('Asha Rao');
    // No credential material of any kind, and no control that could edit one.
    expect(document.body.textContent).not.toMatch(/argon2id|\$2a\$/);
    expect(document.querySelector('input[type="password"]')).toBeNull();
  });
});
