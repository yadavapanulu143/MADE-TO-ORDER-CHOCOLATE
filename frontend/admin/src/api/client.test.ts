import { describe, expect, it, vi } from 'vitest';
import { ApiError, api, onSessionExpired, request } from './client';
import { writeTokens } from '../auth/tokenStore';
import { ADMIN_TOKENS } from '../test/helpers';

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

describe('api client', () => {
  it('maps a problem detail to an ApiError', async () => {
    globalThis.fetch = vi.fn(async () =>
      jsonResponse(422, { detail: 'Price must be positive', code: 'VALIDATION_FAILED', errors: { price: 'must be positive' } }),
    ) as unknown as typeof fetch;

    await expect(api.post('/api/v1/admin/catalog/products', {})).rejects.toBeInstanceOf(ApiError);
    await api.post('/api/v1/admin/catalog/products', {}).catch((error: ApiError) => {
      expect(error.status).toBe(422);
      expect(error.code).toBe('VALIDATION_FAILED');
      expect(error.fieldErrors.price).toBe('must be positive');
    });
  });

  it('refreshes once on a 401 and retries the request with the new token', async () => {
    writeTokens(ADMIN_TOKENS);
    let attempt = 0;
    globalThis.fetch = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/v1/auth/admin/refresh')) {
        return jsonResponse(200, {
          tokens: { ...ADMIN_TOKENS, accessToken: 'access-2', refreshToken: 'refresh-2' },
        });
      }
      attempt += 1;
      if (attempt === 1) return jsonResponse(401, { detail: 'expired' });
      return jsonResponse(200, { ok: true });
    }) as unknown as typeof fetch;

    const result = await request<{ ok: boolean }>('/api/v1/admin/orders');
    expect(result.ok).toBe(true);
    expect(attempt).toBe(2);
  });

  it('notifies listeners and drops the session when a refresh is refused', async () => {
    writeTokens(ADMIN_TOKENS);
    const listener = vi.fn();
    onSessionExpired(listener);
    globalThis.fetch = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/v1/auth/admin/refresh')) {
        return jsonResponse(401, { detail: 'refresh refused' });
      }
      return jsonResponse(401, { detail: 'expired' });
    }) as unknown as typeof fetch;

    await expect(request('/api/v1/admin/orders')).rejects.toBeInstanceOf(ApiError);
    expect(listener).toHaveBeenCalled();
  });

  it('does not attach an Authorization header to an anonymous request', async () => {
    writeTokens(ADMIN_TOKENS);
    const seen: Array<Record<string, string>> = [];
    globalThis.fetch = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      seen.push((init?.headers as Record<string, string>) ?? {});
      return jsonResponse(200, { ok: true });
    }) as unknown as typeof fetch;

    await api.get('/api/v1/settings', true);
    expect(seen[0]?.Authorization).toBeUndefined();
  });
});
