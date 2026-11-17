import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RegisterPage } from './RegisterPage';
import { VerifyPage } from './VerifyPage';
import { LoginPage } from './LoginPage';
import { PasswordResetPage } from './PasswordResetPage';
import { renderWithProviders, stubFetch } from '../test/helpers';
import { readTokens } from '../auth/tokenStore';
import type { TokenPair } from '../api/types';

const TOKENS: TokenPair = {
  accessToken: 'access-1',
  refreshToken: 'refresh-1',
  accessTokenExpiresAt: new Date(Date.now() + 60_000).toISOString(),
  refreshTokenExpiresAt: new Date(Date.now() + 86_400_000).toISOString(),
  tokenType: 'Bearer',
};

const CUSTOMER = {
  id: 'c1',
  fullName: 'Asha Rao',
  email: 'asha@example.com',
  mobile: '9876543210',
  emailVerified: true,
  mobileVerified: true,
  fullyVerified: true,
  active: true,
  registeredAt: '2026-01-01T00:00:00Z',
  addresses: [],
};

describe('RegisterPage', () => {
  it('registers and hands the customer id to the verification step', async () => {
    const calls = stubFetch({
      'POST /api/v1/auth/register': { status: 201, body: { customerId: 'c1', email: 'asha@example.com', mobile: '9876543210' } },
    });
    const user = userEvent.setup();

    renderWithProviders(<RegisterPage />, { route: '/register' });
    await user.type(screen.getByLabelText('Full name'), 'Asha Rao');
    await user.type(screen.getByLabelText('Email address'), 'asha@example.com');
    await user.type(screen.getByLabelText('Mobile number'), '9876543210');
    await user.type(screen.getByLabelText(/^Password/), 'supersecret1');
    await user.click(screen.getByRole('button', { name: 'Create account' }));

    await waitFor(() => {
      const register = calls.find((call) => call.url.endsWith('/auth/register'));
      expect(register?.body).toEqual({
        fullName: 'Asha Rao',
        email: 'asha@example.com',
        mobile: '9876543210',
        password: 'supersecret1',
      });
    });
  });

  it('warns before submitting when the mobile number is malformed', async () => {
    stubFetch({});
    const user = userEvent.setup();

    renderWithProviders(<RegisterPage />, { route: '/register' });
    await user.type(screen.getByLabelText('Mobile number'), '12345');

    expect(screen.getByText('Enter a valid 10-digit Indian mobile number')).toBeInTheDocument();
  });

  it('shows the server validation message when registration is refused', async () => {
    stubFetch({
      'POST /api/v1/auth/register': {
        status: 409,
        body: { detail: 'An account already exists for this email', code: 'EMAIL_ALREADY_REGISTERED' },
      },
    });
    const user = userEvent.setup();

    renderWithProviders(<RegisterPage />, { route: '/register' });
    await user.type(screen.getByLabelText('Full name'), 'Asha Rao');
    await user.type(screen.getByLabelText('Email address'), 'asha@example.com');
    await user.type(screen.getByLabelText('Mobile number'), '9876543210');
    await user.type(screen.getByLabelText(/^Password/), 'supersecret1');
    await user.click(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByText('An account already exists for this email')).toBeInTheDocument();
  });
});

describe('VerifyPage', () => {
  it('sends the code with the chosen channel as a query parameter', async () => {
    const calls = stubFetch({
      'POST /api/v1/auth/customers/c1/verifications': { status: 204 },
    });
    const user = userEvent.setup();

    renderWithProviders(<VerifyPage />, { route: '/verify' });
    await user.type(screen.getByLabelText('Customer id'), 'c1');
    await user.click(screen.getByRole('radio', { name: /^Mobile/ }));
    await user.type(screen.getByLabelText('Verification code'), '123456');
    await user.click(screen.getByRole('button', { name: 'Verify' }));

    await waitFor(() => {
      const verify = calls.find((call) => call.url.includes('/verifications'));
      expect(verify?.url).toContain('channel=MOBILE');
      expect(verify?.body).toEqual({ code: '123456' });
    });
  });

  it('offers a resend that uses the same channel', async () => {
    const calls = stubFetch({
      'POST /api/v1/auth/customers/c1/verifications/resend': { status: 202 },
    });
    const user = userEvent.setup();

    renderWithProviders(<VerifyPage />, { route: '/verify' });
    await user.type(screen.getByLabelText('Customer id'), 'c1');
    await user.click(screen.getByRole('button', { name: 'Resend code' }));

    await waitFor(() => {
      const resend = calls.find((call) => call.url.endsWith('/resend'));
      expect(resend?.body).toEqual({ channel: 'EMAIL' });
    });
  });
});

describe('LoginPage', () => {
  it('stores the tokens and reports the session as signed in', async () => {
    stubFetch({
      'POST /api/v1/auth/login': { body: { customer: CUSTOMER, tokens: TOKENS } },
    });
    const user = userEvent.setup();

    renderWithProviders(<LoginPage />, { route: '/login' });
    await user.type(screen.getByLabelText('Email or mobile number'), 'asha@example.com');
    await user.type(screen.getByLabelText(/^Password/), 'supersecret1');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    await waitFor(() => {
      expect(readTokens()?.accessToken).toBe('access-1');
    });
  });

  it('shows the actionable lockout message the backend sends', async () => {
    stubFetch({
      'POST /api/v1/auth/login': {
        status: 423,
        body: { detail: 'Your account is locked. Try again later.', code: 'ACCOUNT_LOCKED' },
      },
    });
    const user = userEvent.setup();

    renderWithProviders(<LoginPage />, { route: '/login' });
    await user.type(screen.getByLabelText('Email or mobile number'), 'asha@example.com');
    await user.type(screen.getByLabelText(/^Password/), 'wrong-password');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByText('Your account is locked. Try again later.')).toBeInTheDocument();
  });
});

describe('PasswordResetPage', () => {
  it('requests a reset without confirming whether the email exists', async () => {
    const calls = stubFetch({ 'POST /api/v1/auth/password-reset/request': { status: 202 } });
    const user = userEvent.setup();

    renderWithProviders(<PasswordResetPage />, { route: '/password-reset' });
    await user.type(screen.getByLabelText('Email address'), 'asha@example.com');
    await user.click(screen.getByRole('button', { name: 'Send reset link' }));

    await waitFor(() => {
      expect(calls.some((call) => call.url.endsWith('/password-reset/request'))).toBe(true);
    });
    expect(await screen.findByRole('status')).toHaveTextContent(/if that email is registered/i);
  });

  it('completes a reset with the token from the link', async () => {
    const calls = stubFetch({ 'POST /api/v1/auth/password-reset/complete': { status: 204 } });
    const user = userEvent.setup();

    renderWithProviders(<PasswordResetPage />, { route: '/password-reset?token=reset-token-1' });
    expect(screen.getByLabelText('Reset token')).toHaveValue('reset-token-1');
    await user.type(screen.getByLabelText(/^New password/), 'brand-new-secret');
    await user.click(screen.getByRole('button', { name: 'Reset password' }));

    await waitFor(() => {
      const complete = calls.find((call) => call.url.endsWith('/password-reset/complete'));
      expect(complete?.body).toEqual({ token: 'reset-token-1', newPassword: 'brand-new-secret' });
    });
  });
});
