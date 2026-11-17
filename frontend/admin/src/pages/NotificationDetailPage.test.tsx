import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { NotificationDetailPage } from './NotificationDetailPage';
import { fakeAdminToken, renderAtRoute, stubFetch } from '../test/helpers';

function signInWith(permissions: string[]) {
  const tokens = {
    accessToken: fakeAdminToken({ sub: 'a1', role: 'ADMIN', permissions }),
    refreshToken: 'r1',
    accessTokenExpiresAt: new Date(Date.now() + 60_000).toISOString(),
    refreshTokenExpiresAt: new Date(Date.now() + 86_400_000).toISOString(),
    tokenType: 'Bearer',
  };
  window.localStorage.setItem('choco.admin.tokens', JSON.stringify(tokens));
}

const FAILED = {
  id: 'n1',
  type: 'ORDER_CONFIRMED',
  channel: 'EMAIL',
  recipientType: 'CUSTOMER',
  recipientMasked: 'a***a@example.com',
  subject: 'Your order is confirmed',
  status: 'FAILED',
  attempts: 3,
  lastError: 'SMTP timeout',
  referenceId: 'o1',
  createdAt: '2026-03-01T10:00:00Z',
  sentAt: null,
};

describe('NotificationDetailPage', () => {
  it('offers a resend for a failed notification when the administrator may write', async () => {
    signInWith(['notification:read', 'notification:write']);
    const calls = stubFetch({
      'GET /api/v1/admin/notifications/n1': { body: FAILED },
      'POST /api/v1/admin/notifications/n1/resend': { body: { ...FAILED, status: 'PENDING' } },
    });

    renderAtRoute(<NotificationDetailPage />, {
      route: '/notifications/n1',
      path: '/notifications/:id',
    });

    await userEvent.click(await screen.findByRole('button', { name: /re-send this message/i }));

    await waitFor(() => {
      expect(calls.some((call) => call.method === 'POST')).toBe(true);
    });
  });

  it('does not offer a resend to an administrator without notification:write', async () => {
    signInWith(['notification:read']);
    stubFetch({ 'GET /api/v1/admin/notifications/n1': { body: FAILED } });

    renderAtRoute(<NotificationDetailPage />, {
      route: '/notifications/n1',
      path: '/notifications/:id',
    });

    await screen.findByText('Your order is confirmed');
    expect(screen.queryByRole('button', { name: /re-send/i })).not.toBeInTheDocument();
  });

  it('refuses to resend a message that is already sent', async () => {
    signInWith(['notification:read', 'notification:write']);
    stubFetch({
      'GET /api/v1/admin/notifications/n1': {
        body: { ...FAILED, status: 'SENT', sentAt: '2026-03-01T10:01:00Z' },
      },
    });

    renderAtRoute(<NotificationDetailPage />, {
      route: '/notifications/n1',
      path: '/notifications/:id',
    });

    expect(await screen.findByText(/only a failed or dead-lettered message can be re-sent/i)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /re-send/i })).not.toBeInTheDocument();
  });
});
