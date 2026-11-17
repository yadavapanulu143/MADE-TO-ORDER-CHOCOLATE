import { render } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import type { ReactElement } from 'react';
import { AuthProvider } from '../auth/AuthContext';
import { CartProvider } from '../cart/CartContext';

/** Render a page inside the providers it needs, at a chosen route. */
export function renderWithProviders(ui: ReactElement, { route = '/' } = {}) {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <AuthProvider>
        <CartProvider>{ui}</CartProvider>
      </AuthProvider>
    </MemoryRouter>,
  );
}

/**
 * Render a page matched by a path pattern, so useParams resolves. A page that
 * reads a path parameter (an order id, an invoice id) needs a real <Route>, not
 * just an entry in history.
 */
export function renderAtRoute(
  ui: ReactElement,
  { route, path }: { route: string; path: string },
) {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <AuthProvider>
        <CartProvider>
          <Routes>
            <Route path={path} element={ui} />
          </Routes>
        </CartProvider>
      </AuthProvider>
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
