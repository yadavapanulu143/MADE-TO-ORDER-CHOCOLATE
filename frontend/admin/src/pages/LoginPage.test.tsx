import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { LoginPage } from './LoginPage';
import { renderWithProviders, stubFetch } from '../test/helpers';

describe('LoginPage', () => {
  it('signs an administrator in with the admin endpoint and stores the session', async () => {
    const calls = stubFetch({
      'POST /api/v1/auth/admin/login': {
        body: {
          admin: {
            id: 'admin-1',
            fullName: 'Ada Seller',
            email: 'ada@example.com',
            role: 'SUPER_ADMIN',
            permissions: [],
            active: true,
            createdAt: '2026-01-01T00:00:00Z',
          },
          tokens: {
            accessToken: 'access-1',
            refreshToken: 'refresh-1',
            accessTokenExpiresAt: '2099-01-01T00:00:00Z',
            refreshTokenExpiresAt: '2099-01-02T00:00:00Z',
            tokenType: 'Bearer',
          },
        },
      },
    });

    renderWithProviders(<LoginPage />, { route: '/login' });

    await userEvent.type(screen.getByLabelText('Email address'), 'ada@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'correct horse battery staple');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    await waitFor(() => {
      expect(calls.some((call) => call.method === 'POST' && call.url.endsWith('/api/v1/auth/admin/login'))).toBe(true);
    });
    expect(window.localStorage.getItem('choco.admin.tokens')).toContain('access-1');
  });

  it('shows the server reason when the credentials are refused', async () => {
    stubFetch({
      'POST /api/v1/auth/admin/login': {
        status: 401,
        body: { detail: 'The email address or password is incorrect' },
      },
    });

    renderWithProviders(<LoginPage />, { route: '/login' });

    await userEvent.type(screen.getByLabelText('Email address'), 'ada@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'wrong');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByText('The email address or password is incorrect')).toBeInTheDocument();
    expect(window.localStorage.getItem('choco.admin.tokens')).toBeNull();
  });
});
