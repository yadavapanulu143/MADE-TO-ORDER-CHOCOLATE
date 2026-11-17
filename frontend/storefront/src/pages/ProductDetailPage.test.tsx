import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ProductDetailPage } from './ProductDetailPage';
import { renderAtRoute, stubFetch } from '../test/helpers';
import { writeTokens } from '../auth/tokenStore';
import { CUSTOMER, TOKENS } from '../test/fixtures';
import type { Product } from '../api/types';

const PRODUCT: Product = {
  id: 'p1',
  slug: 'dark-bar',
  name: 'Dark Chocolate Bar',
  shortDescription: '70% cocoa',
  description: 'Single-origin dark chocolate.',
  preparationInfo: 'Made to order within two days.',
  deliveryInfo: 'Delivered by us.',
  fromPriceMinorUnits: 45000,
  fromPrice: '₹450.00',
  currency: 'INR',
  status: 'PUBLISHED',
  available: true,
  variants: [
    { id: 'v1', label: '100 g', sku: 'DARK-100', priceMinorUnits: 45000, price: '₹450.00', currency: 'INR', minOrderQuantity: 1, maxOrderQuantity: 5, active: true },
    { id: 'v2', label: '250 g', sku: 'DARK-250', priceMinorUnits: 99000, price: '₹990.00', currency: 'INR', minOrderQuantity: 1, maxOrderQuantity: 3, active: true },
    { id: 'v3', label: '500 g', sku: 'DARK-500', priceMinorUnits: 180000, price: '₹1,800.00', currency: 'INR', minOrderQuantity: 1, maxOrderQuantity: 2, active: false },
  ],
  images: [],
  attributes: [{ id: 'at1', name: 'Cocoa', value: '70%', displayOrder: 0 }],
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

function routes(overrides: Record<string, { body?: unknown; status?: number }> = {}) {
  return {
    'GET /api/v1/products/dark-bar': { body: PRODUCT },
    'GET /api/v1/settings': { body: { sellerName: 'Choco', updatedAt: '2026-01-01T00:00:00Z' } },
    'GET /api/v1/carts/me': { status: 404, body: { detail: 'no cart' } },
    ...overrides,
  };
}

describe('ProductDetailPage', () => {
  it('shows the product, its attributes and the first variant price', async () => {
    stubFetch(routes());

    renderAtRoute(<ProductDetailPage />, { route: '/products/dark-bar', path: '/products/:slug' });

    expect(await screen.findByRole('heading', { level: 1, name: 'Dark Chocolate Bar' })).toBeInTheDocument();
    expect(screen.getByText('Single-origin dark chocolate.')).toBeInTheDocument();
    expect(screen.getByText('70%')).toBeInTheDocument();
    // The price appears both as the selected-variant price and in the variant list.
    expect(screen.getAllByText('₹450.00').length).toBeGreaterThan(0);
  });

  it('asks a signed-out visitor to sign in rather than offering add-to-cart', async () => {
    stubFetch(routes());

    renderAtRoute(<ProductDetailPage />, { route: '/products/dark-bar', path: '/products/:slug' });

    expect(await screen.findByText(/to add this to your cart/i)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Add to cart' })).not.toBeInTheDocument();
  });

  it('blocks ordering for a customer who has not verified both channels', async () => {
    writeTokens(TOKENS);
    stubFetch(routes({ 'GET /api/v1/customers/me': { body: { ...CUSTOMER, fullyVerified: false } } }));

    renderAtRoute(<ProductDetailPage />, { route: '/products/dark-bar', path: '/products/:slug' });

    expect(
      await screen.findByText(/please verify your email and mobile number before ordering/i),
    ).toBeInTheDocument();
  });

  it('adds the chosen variant and quantity to the cart', async () => {
    writeTokens(TOKENS);
    const calls = stubFetch(
      routes({
        'GET /api/v1/customers/me': { body: CUSTOMER },
        'POST /api/v1/carts/me/lines': { body: { cartId: 'c', lines: [], totalItems: 2 } },
      }),
    );
    const user = userEvent.setup();

    renderAtRoute(<ProductDetailPage />, { route: '/products/dark-bar', path: '/products/:slug' });
    await screen.findByRole('button', { name: 'Add to cart' });
    await user.click(screen.getByRole('radio', { name: /250 g/ }));
    await user.click(screen.getByLabelText('Increase quantity'));
    await user.click(screen.getByRole('button', { name: 'Add to cart' }));

    await waitFor(() => {
      const add = calls.find((call) => call.url.endsWith('/carts/me/lines'));
      expect(add?.body).toEqual({ variantId: 'v2', quantity: 2 });
    });
  });

  it('does not offer a disabled variant', async () => {
    writeTokens(TOKENS);
    stubFetch(routes({ 'GET /api/v1/customers/me': { body: CUSTOMER } }));

    renderAtRoute(<ProductDetailPage />, { route: '/products/dark-bar', path: '/products/:slug' });
    await screen.findByRole('button', { name: 'Add to cart' });

    expect(screen.getByRole('radio', { name: /500 g/ })).toBeDisabled();
  });

  it('reports a missing product with a way back to the shop', async () => {
    stubFetch(routes({ 'GET /api/v1/products/dark-bar': { status: 404, body: { detail: 'Product not found' } } }));

    renderAtRoute(<ProductDetailPage />, { route: '/products/dark-bar', path: '/products/:slug' });

    expect(await screen.findByText('Product not found')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to the shop' })).toBeInTheDocument();
  });
});
