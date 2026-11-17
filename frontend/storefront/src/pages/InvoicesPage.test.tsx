import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { InvoicesPage } from './InvoicesPage';
import { InvoiceDetailPage } from './InvoiceDetailPage';
import { renderAtRoute, renderWithProviders, stubFetch } from '../test/helpers';
import { writeTokens } from '../auth/tokenStore';
import { CUSTOMER, INVOICE, SETTINGS, TOKENS } from '../test/fixtures';

describe('InvoicesPage', () => {
  it('lists the customer\'s invoices', async () => {
    writeTokens(TOKENS);
    stubFetch({
      'GET /api/v1/customers/me': { body: CUSTOMER },
      'GET /api/v1/carts/me': { status: 404, body: { detail: 'no cart' } },
      'GET /api/v1/invoices/me': { body: [INVOICE] },
    });

    renderWithProviders(<InvoicesPage />, { route: '/invoices' });

    expect(await screen.findByText('INV-2026-0001')).toBeInTheDocument();
    expect(screen.getByText('Order CHOCO-1001')).toBeInTheDocument();
  });

  it('shows an empty state with no invoices', async () => {
    writeTokens(TOKENS);
    stubFetch({
      'GET /api/v1/customers/me': { body: CUSTOMER },
      'GET /api/v1/carts/me': { status: 404, body: { detail: 'no cart' } },
      'GET /api/v1/invoices/me': { body: [] },
    });

    renderWithProviders(<InvoicesPage />, { route: '/invoices' });

    expect(await screen.findByText('No invoices yet')).toBeInTheDocument();
  });
});

describe('InvoiceDetailPage', () => {
  it('renders the invoice with the seller details and the totals', async () => {
    writeTokens(TOKENS);
    stubFetch({
      'GET /api/v1/customers/me': { body: CUSTOMER },
      'GET /api/v1/carts/me': { status: 404, body: { detail: 'no cart' } },
      'GET /api/v1/settings': { body: SETTINGS },
      'GET /api/v1/invoices/me/inv-1': { body: INVOICE },
    });

    renderAtRoute(<InvoiceDetailPage />, { route: '/invoices/inv-1', path: '/invoices/:invoiceId' });

    expect(await screen.findByText('INV-2026-0001')).toBeInTheDocument();
    expect(screen.getByText('Dark Chocolate Bar')).toBeInTheDocument();
    expect(screen.getByText('DARK-100')).toBeInTheDocument();
    expect(screen.getAllByText('₹949.00').length).toBeGreaterThan(0);
  });
});
