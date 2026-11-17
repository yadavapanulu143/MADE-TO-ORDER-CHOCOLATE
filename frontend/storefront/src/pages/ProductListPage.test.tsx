import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ProductListPage } from './ProductListPage';
import { renderWithProviders, stubFetch } from '../test/helpers';

const PRODUCTS = {
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
      variantCount: 2,
      status: 'PUBLISHED',
      available: true,
    },
  ],
  page: 0,
  size: 12,
  totalElements: 1,
};

describe('ProductListPage', () => {
  it('shows products returned by the public catalogue endpoint', async () => {
    stubFetch({ 'GET /api/v1/products': { body: PRODUCTS } });

    renderWithProviders(<ProductListPage />);

    expect(await screen.findByText('Dark Chocolate Bar')).toBeInTheDocument();
    expect(screen.getByText('From ₹450.00')).toBeInTheDocument();
  });

  it('shows an empty state when nothing matches', async () => {
    stubFetch({ 'GET /api/v1/products': { body: { ...PRODUCTS, content: [], totalElements: 0 } } });

    renderWithProviders(<ProductListPage />);

    expect(await screen.findByText('No chocolate matched your search')).toBeInTheDocument();
  });

  it('sends the search term to the backend', async () => {
    const calls = stubFetch({ 'GET /api/v1/products': { body: PRODUCTS } });
    const user = userEvent.setup();

    renderWithProviders(<ProductListPage />);
    await screen.findByText('Dark Chocolate Bar');

    await user.type(screen.getByLabelText('Search products'), 'dark');
    await user.click(screen.getByRole('button', { name: 'Search' }));

    await waitFor(() => {
      expect(calls.some((call) => call.url.includes('search=dark'))).toBe(true);
    });
  });
});
