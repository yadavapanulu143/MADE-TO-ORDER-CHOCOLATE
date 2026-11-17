import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { ExportsPage } from './ExportsPage';
import { fakeAdminToken, renderWithProviders, stubFetch } from '../test/helpers';

function signInWith(permissions: string[], role = 'ADMIN') {
  const tokens = {
    accessToken: fakeAdminToken({ sub: 'a1', role, permissions }),
    refreshToken: 'r1',
    accessTokenExpiresAt: new Date(Date.now() + 60_000).toISOString(),
    refreshTokenExpiresAt: new Date(Date.now() + 86_400_000).toISOString(),
    tokenType: 'Bearer',
  };
  window.localStorage.setItem('choco.admin.tokens', JSON.stringify(tokens));
}

describe('ExportsPage', () => {
  it('offers the operational exports to an administrator with report:read', () => {
    signInWith(['report:read']);
    stubFetch({});

    renderWithProviders(<ExportsPage />, { route: '/exports' });

    expect(screen.getByText('Orders')).toBeInTheDocument();
    // The customer export needs its own permission, so it is refused here.
    expect(screen.getByText(/do not have permission to export this dataset/i)).toBeInTheDocument();
  });

  it('offers the customer export when the administrator holds customer:export', () => {
    signInWith(['report:read', 'customer:export']);
    stubFetch({});

    renderWithProviders(<ExportsPage />, { route: '/exports' });

    expect(screen.queryByText(/do not have permission to export this dataset/i)).not.toBeInTheDocument();
  });

  it('gives every export to a super administrator', () => {
    signInWith([], 'SUPER_ADMIN');
    stubFetch({});

    renderWithProviders(<ExportsPage />, { route: '/exports' });

    expect(screen.queryByText(/do not have permission to export this dataset/i)).not.toBeInTheDocument();
  });
});
