import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, api, onSessionExpired } from './client';
import { clearTokens, readTokens, writeTokens } from '../auth/tokenStore';
import type { TokenPair } from './types';

const ORIGINAL_FETCH = globalThis.fetch;

const asFetch = (mock: unknown) => mock as unknown as typeof fetch;

function tokens(access: string, refresh = 'refresh-1'): TokenPair {
  return {
    accessToken: access,
    refreshToken: refresh,
    accessTokenExpiresAt: new Date(Date.now() + 60_000).toISOString(),
    refreshTokenExpiresAt: new Date(Date.now() + 86_400_000).toISOString(),
    tokenType: 'Bearer',
  };
}

describe('api client', () => {
  beforeEach(() => {
    clearTokens();
    globalThis.fetch = ORIGINAL_FETCH;
  });

  it('attaches the bearer token to a protected request', async () => {
    writeTokens(tokens('access-1'));
    const fetchMock = vi.fn<(input: RequestInfo | URL, init?: RequestInit) => Promise<Response>>().mockResolvedValue(
      new Response(JSON.stringify({ ok: true }), { status: 200, headers: { 'content-type': 'application/json' } }),
    );
    globalThis.fetch = asFetch(fetchMock);

    await api.get('/api/v1/customers/me');

    const [, init] = fetchMock.mock.calls[0]!;
    expect(init?.headers).toMatchObject({ Authorization: 'Bearer access-1' });
  });

  it('sends no Authorization header for an anonymous read', async () => {
    writeTokens(tokens('access-1'));
    const fetchMock = vi.fn<(input: RequestInfo | URL, init?: RequestInit) => Promise<Response>>().mockResolvedValue(
      new Response(JSON.stringify([]), { status: 200, headers: { 'content-type': 'application/json' } }),
    );
    globalThis.fetch = asFetch(fetchMock);

    await api.get('/api/v1/products', true);

    const [, init] = fetchMock.mock.calls[0]!;
    expect(init?.headers).not.toHaveProperty('Authorization');
  });

  it('refreshes once on a 401 and replays the request with the new token', async () => {
    writeTokens(tokens('stale-access'));
    const responses = [
      new Response(null, { status: 401 }),
      new Response(JSON.stringify({ tokens: tokens('fresh-access', 'refresh-2') }), {
        status: 200,
        headers: { 'content-type': 'application/json' },
      }),
      new Response(JSON.stringify({ ok: true }), { status: 200, headers: { 'content-type': 'application/json' } }),
    ];
    const fetchMock = vi.fn<(input: RequestInfo | URL, init?: RequestInit) => Promise<Response>>(
      async () => responses.shift()!,
    );
    globalThis.fetch = asFetch(fetchMock);

    const result = await api.get<{ ok: boolean }>('/api/v1/customers/me');

    expect(result.ok).toBe(true);
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(readTokens()?.accessToken).toBe('fresh-access');
    const [, replayInit] = fetchMock.mock.calls[2]!;
    expect(replayInit?.headers).toMatchObject({ Authorization: 'Bearer fresh-access' });
  });

  it('shares one refresh across concurrent 401s so the rotated refresh token is not consumed twice', async () => {
    writeTokens(tokens('stale-access'));
    let refreshCalls = 0;
    const fetchMock = vi.fn<(input: RequestInfo | URL, init?: RequestInit) => Promise<Response>>(async (input, init) => {
      const url = String(input);
      if (url.includes('/auth/refresh')) {
        refreshCalls += 1;
        return new Response(JSON.stringify({ tokens: tokens('fresh-access', 'refresh-2') }), {
          status: 200,
          headers: { 'content-type': 'application/json' },
        });
      }
      const auth = (init?.headers as Record<string, string> | undefined)?.Authorization;
      if (auth === 'Bearer fresh-access') {
        return new Response(JSON.stringify({ ok: true }), {
          status: 200,
          headers: { 'content-type': 'application/json' },
        });
      }
      return new Response(null, { status: 401 });
    });
    globalThis.fetch = asFetch(fetchMock);

    const [a, b] = await Promise.all([
      api.get<{ ok: boolean }>('/api/v1/orders/me'),
      api.get<{ ok: boolean }>('/api/v1/carts/me'),
    ]);

    expect(a.ok).toBe(true);
    expect(b.ok).toBe(true);
    expect(refreshCalls).toBe(1);
  });

  it('drops the session and notifies listeners when the refresh is refused', async () => {
    writeTokens(tokens('stale-access'));
    const expired = vi.fn();
    const unsubscribe = onSessionExpired(expired);
    const fetchMock = vi.fn<(input: RequestInfo | URL, init?: RequestInit) => Promise<Response>>(async (input) => {
      if (String(input).includes('/auth/refresh')) return new Response(null, { status: 401 });
      return new Response(null, { status: 401 });
    });
    globalThis.fetch = asFetch(fetchMock);

    await expect(api.get('/api/v1/customers/me')).rejects.toBeInstanceOf(ApiError);

    expect(expired).toHaveBeenCalledTimes(1);
    expect(readTokens()).toBeNull();
    unsubscribe();
  });

  it('maps a problem-detail body onto the error, including field errors', async () => {
    const fetchMock = vi.fn<(input: RequestInfo | URL, init?: RequestInit) => Promise<Response>>().mockResolvedValue(
      new Response(
        JSON.stringify({
          title: 'Validation failed',
          detail: 'Enter a valid email address',
          code: 'VALIDATION_FAILED',
          errors: { email: 'must be a well-formed address' },
        }),
        { status: 422, headers: { 'content-type': 'application/problem+json' } },
      ),
    );
    globalThis.fetch = asFetch(fetchMock);

    await expect(api.post('/api/v1/auth/register', {})).rejects.toMatchObject({
      status: 422,
      code: 'VALIDATION_FAILED',
      detail: 'Enter a valid email address',
      fieldErrors: { email: 'must be a well-formed address' },
    });
  });

  it('returns undefined for a 204 with no body', async () => {
    const fetchMock = vi.fn<(input: RequestInfo | URL, init?: RequestInit) => Promise<Response>>().mockResolvedValue(new Response(null, { status: 204 }));
    globalThis.fetch = asFetch(fetchMock);

    await expect(api.post('/api/v1/auth/logout', { refreshToken: 'x' })).resolves.toBeUndefined();
  });

  it('does not attempt a refresh for an anonymous request', async () => {
    writeTokens(tokens('stale-access'));
    const fetchMock = vi.fn<(input: RequestInfo | URL, init?: RequestInit) => Promise<Response>>().mockResolvedValue(new Response(null, { status: 401 }));
    globalThis.fetch = asFetch(fetchMock);

    await expect(api.get('/api/v1/products', true)).rejects.toBeInstanceOf(ApiError);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('carries an idempotency key through to the request headers', async () => {
    writeTokens(tokens('access-1'));
    const fetchMock = vi.fn<(input: RequestInfo | URL, init?: RequestInit) => Promise<Response>>().mockResolvedValue(
      new Response(JSON.stringify({ id: 'o1' }), { status: 200, headers: { 'content-type': 'application/json' } }),
    );
    globalThis.fetch = asFetch(fetchMock);

    await api.post('/api/v1/checkout', { addressId: 'a1' }, { 'Idempotency-Key': 'key-1' });

    const [, init] = fetchMock.mock.calls[0]!;
    expect(init?.headers).toMatchObject({ 'Idempotency-Key': 'key-1' });
  });
});
