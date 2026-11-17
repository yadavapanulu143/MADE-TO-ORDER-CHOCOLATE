import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ProfilePage } from './ProfilePage';
import { AddressesPage } from './AddressesPage';
import { HomePage } from './HomePage';
import { PoliciesPage } from './PoliciesPage';
import { NotFoundPage } from './NotFoundPage';
import { renderWithProviders, stubFetch } from '../test/helpers';
import { writeTokens } from '../auth/tokenStore';
import { ADDRESS, CUSTOMER, SETTINGS, TOKENS } from '../test/fixtures';

function sessionRoutes(overrides: Record<string, { body?: unknown; status?: number }> = {}) {
  return {
    'GET /api/v1/customers/me': { body: CUSTOMER },
    'GET /api/v1/carts/me': { status: 404, body: { detail: 'no cart' } },
    'GET /api/v1/settings': { body: SETTINGS },
    ...overrides,
  };
}

describe('ProfilePage', () => {
  it('shows the account with its verified badges', async () => {
    writeTokens(TOKENS);
    stubFetch(sessionRoutes());

    renderWithProviders(<ProfilePage />, { route: '/profile' });

    expect(await screen.findByText('asha@example.com')).toBeInTheDocument();
    expect(screen.getAllByText('Verified')).toHaveLength(2);
  });

  it('prompts verification for a customer who has not verified both channels', async () => {
    writeTokens(TOKENS);
    stubFetch(sessionRoutes({ 'GET /api/v1/customers/me': { body: { ...CUSTOMER, mobileVerified: false, fullyVerified: false } } }));

    renderWithProviders(<ProfilePage />, { route: '/profile' });

    expect(await screen.findByText('Verify both your email and mobile number to place orders.')).toBeInTheDocument();
  });

  it('warns that a contact change needs re-verification', async () => {
    writeTokens(TOKENS);
    stubFetch(
      sessionRoutes({
        'PATCH /api/v1/customers/me/contact': {
          body: { ...CUSTOMER, email: 'new@example.com', emailVerified: false, fullyVerified: false },
        },
      }),
    );
    const user = userEvent.setup();

    renderWithProviders(<ProfilePage />, { route: '/profile' });
    const email = await screen.findByLabelText('Email address');
    await user.clear(email);
    await user.type(email, 'new@example.com');
    // The mobile field is required, so fill it as a real customer would; jsdom
    // blocks a submit that fails native constraint validation.
    const mobile = screen.getByLabelText('Mobile number');
    await user.clear(mobile);
    await user.type(mobile, '9876543210');
    await user.click(screen.getByRole('button', { name: 'Save contact details' }));

    expect(await screen.findByText(/please re-verify your email/i)).toBeInTheDocument();
  });

  it('sends a password change and clears the fields on success', async () => {
    writeTokens(TOKENS);
    const calls = stubFetch(sessionRoutes({ 'POST /api/v1/customers/me/password': { status: 204 } }));
    const user = userEvent.setup();

    renderWithProviders(<ProfilePage />, { route: '/profile' });
    await screen.findByLabelText(/^Current password/);
    await user.type(screen.getByLabelText(/^Current password/), 'old-secret-1');
    await user.type(screen.getByLabelText(/^New password/), 'new-secret-12');
    await user.click(screen.getByRole('button', { name: 'Change password' }));

    await waitFor(() => {
      const change = calls.find((call) => call.url.endsWith('/customers/me/password'));
      expect(change?.body).toEqual({ currentPassword: 'old-secret-1', newPassword: 'new-secret-12' });
    });
  });
});

describe('AddressesPage', () => {
  it('lists addresses and can make one default', async () => {
    writeTokens(TOKENS);
    const calls = stubFetch(
      sessionRoutes({
        'GET /api/v1/customers/me/addresses': { body: [{ ...ADDRESS, isDefault: false }] },
        'POST /api/v1/customers/me/addresses/a1/default': { body: ADDRESS },
      }),
    );
    const user = userEvent.setup();

    renderWithProviders(<AddressesPage />, { route: '/addresses' });
    await screen.findByRole('button', { name: 'Make default' });
    await user.click(screen.getByRole('button', { name: 'Make default' }));

    await waitFor(() => {
      expect(calls.some((call) => call.url.endsWith('/addresses/a1/default'))).toBe(true);
    });
  });

  it('creates an address from the form', async () => {
    writeTokens(TOKENS);
    const calls = stubFetch(
      sessionRoutes({
        'GET /api/v1/customers/me/addresses': { body: [] },
        'POST /api/v1/customers/me/addresses': { status: 201, body: ADDRESS },
      }),
    );
    const user = userEvent.setup();

    renderWithProviders(<AddressesPage />, { route: '/addresses' });
    await user.click(await screen.findByRole('button', { name: 'Add a new address' }));
    await user.type(screen.getByLabelText('Recipient name'), 'Asha Rao');
    await user.type(screen.getByLabelText('Mobile number'), '9876543210');
    await user.type(screen.getByLabelText('Address line 1'), '1 Cocoa Lane');
    await user.type(screen.getByLabelText('City'), 'Bengaluru');
    await user.type(screen.getByLabelText('State'), 'Karnataka');
    await user.type(screen.getByLabelText('PIN code'), '560001');
    await user.click(screen.getByRole('button', { name: 'Save address' }));

    await waitFor(() => {
      const created = calls.find((call) => call.method === 'POST' && call.url.endsWith('/addresses'));
      expect(created?.body).toMatchObject({ recipientName: 'Asha Rao', pincode: '560001' });
    });
  });
});

describe('HomePage', () => {
  it('shows the seller name and featured products', async () => {
    stubFetch({
      'GET /api/v1/settings': { body: SETTINGS },
      'GET /api/v1/products': {
        body: {
          content: [
            {
              id: 'p1',
              slug: 'dark-bar',
              name: 'Dark Chocolate Bar',
              shortDescription: '70% cocoa',
              fromPriceMinorUnits: 45000,
              fromPrice: '₹450.00',
              currency: 'INR',
              primaryImageUrl: null,
              variantCount: 1,
              status: 'PUBLISHED',
              available: true,
            },
          ],
          page: 0,
          size: 6,
          totalElements: 1,
        },
      },
    });

    renderWithProviders(<HomePage />, { route: '/' });

    expect(await screen.findByText('Dark Chocolate Bar')).toBeInTheDocument();
    expect(screen.getByText('Refunds are available for defective goods.')).toBeInTheDocument();
  });
});

describe('PoliciesPage', () => {
  it('shows the published policies', async () => {
    stubFetch({ 'GET /api/v1/settings': { body: SETTINGS } });

    renderWithProviders(<PoliciesPage />, { route: '/policies' });

    expect(await screen.findByText('Delivered by us within 3 days.')).toBeInTheDocument();
    expect(screen.getByText('Refunds are available for defective goods.')).toBeInTheDocument();
  });

  it('says so when no policies are published', async () => {
    stubFetch({
      'GET /api/v1/settings': {
        body: { ...SETTINGS, shippingPolicy: null, refundPolicy: null, termsAndConditions: null, privacyPolicy: null },
      },
    });

    renderWithProviders(<PoliciesPage />, { route: '/policies' });

    expect(await screen.findByText('The seller has not published policies yet.')).toBeInTheDocument();
  });
});

describe('NotFoundPage', () => {
  it('offers a way back to the shop', () => {
    renderWithProviders(<NotFoundPage />, { route: '/nope' });
    expect(screen.getByText('Page not found')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to the shop' })).toBeInTheDocument();
  });
});
