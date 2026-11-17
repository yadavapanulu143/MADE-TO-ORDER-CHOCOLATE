import { render } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import type { ReactElement } from 'react';
import { AdminAuthProvider } from '../auth/AdminAuthContext';

/** Render a page inside the providers it needs, at a chosen route. */
export function renderWithProviders(ui: ReactElement, { route = '/' } = {}) {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <AdminAuthProvider>{ui}</AdminAuthProvider>
    </MemoryRouter>,
  );
}

/** Render a page matched by a path pattern, so useParams resolves. */
export function renderAtRoute(
  ui: ReactElement,
  { route, path }: { route: string; path: string },
) {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <AdminAuthProvider>
        <Routes>
          <Route path={path} element={ui} />
        </Routes>
      </AdminAuthProvider>
    </MemoryRouter>,
  );
}

export interface MockRoute {
  method?: string;
  status?: number;
  body?: unknown;
  headers?: Record<string, string>;
}

/**
 * A fetch stub keyed by "METHOD path" (path without query string). Returns a list
 * of the requests it saw so a test can assert what the UI actually sent.
 */
export function stubFetch(routes: Record<string, MockRoute>) {
  const calls: Array<{ method: string; url: string; body: unknown; headers: Record<string, string> }> = [];

  const fetchMock = async (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
    const url = typeof input === 'string' ? input : input instanceof URL ? input.toString() : input.url;
    const method = (init?.method ?? 'GET').toUpperCase();
    const path = url.split('?')[0] ?? url;
    const key = `${method} ${path}`;
    const body = init?.body ? JSON.parse(String(init.body)) : undefined;
    const headers = (init?.headers as Record<string, string>) ?? {};
    calls.push({ method, url, body, headers });

    const route = routes[key] ?? routes[`* ${path}`] ?? routes[`${method} *`];
    if (!route) {
      return new Response(JSON.stringify({ title: 'Not Found', status: 404 }), {
        status: 404,
        headers: { 'content-type': 'application/problem+json' },
      });
    }
    const status = route.status ?? 200;
    const payload = route.body === undefined ? null : JSON.stringify(route.body);
    return new Response(status === 204 ? null : payload, {
      status,
      headers: {
        'content-type': route.headers?.['content-type'] ?? 'application/json',
        ...route.headers,
      },
    });
  };

  globalThis.fetch = fetchMock as unknown as typeof fetch;
  return calls;
}

/** A base64url token whose payload carries the given claims, so the auth context reads them. */
export function fakeAdminToken(claims: Record<string, unknown>): string {
  const encode = (value: unknown) =>
    btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${encode({ alg: 'none' })}.${encode(claims)}.signature`;
}

export const ADMIN_TOKEN = fakeAdminToken({
  sub: 'admin-1',
  role: 'SUPER_ADMIN',
  fullName: 'Ada Seller',
  email: 'ada@example.com',
  permissions: [
    'customer:read',
    'customer:export',
    'product:write',
    'order:write',
    'payment:refund',
    'delivery:write',
    'production:write',
    'report:read',
    'settings:write',
    'notification:read',
    'notification:write',
  ],
});

export const ADMIN_TOKENS = {
  accessToken: ADMIN_TOKEN,
  refreshToken: 'admin-refresh-1',
  accessTokenExpiresAt: new Date(Date.now() + 60_000).toISOString(),
  refreshTokenExpiresAt: new Date(Date.now() + 86_400_000).toISOString(),
  tokenType: 'Bearer',
};

/** Store an administrator session so a page under test renders as signed in. */
export function signedIn() {
  window.localStorage.setItem('choco.admin.tokens', JSON.stringify(ADMIN_TOKENS));
}
