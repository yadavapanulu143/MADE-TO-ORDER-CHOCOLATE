# Choco Storefront

The customer-facing web app for the made-to-order chocolate shop. React + Vite +
TypeScript, talking only to the API gateway.

## What it does

- Browse the catalogue and a product's variants, with the price shown for the
  chosen variant.
- Register, then verify both the email address and the mobile number with OTPs.
- Sign in, refresh a session transparently, and sign out.
- Manage the profile, contact details (which re-verify on change), password, and
  delivery addresses.
- Keep a cart and check out against a chosen address, with the delivery charge
  quoted from the PIN code before anything is placed.
- Pay online through Razorpay; the browser response is only ever sent to the
  backend, which verifies the signature and re-fetches the true payment state.
- Track an order's payment, preparation and delivery progress, cancel while it is
  still cancellable, and request a refund after payment.
- Download invoices.

## Architecture

- `src/api` — the wire layer. `types.ts` mirrors the backend DTOs, `client.ts` is
  the fetch wrapper (bearer token, one shared refresh on 401, RFC 9457 problem
  mapping), `endpoints.ts` groups the calls by resource.
- `src/auth` — the session. `tokenStore.ts` holds the tokens; `AuthContext.tsx`
  resolves the customer on boot and drops the session when a refresh is refused.
- `src/cart` — cart state, refetched when the signed-in customer changes.
- `src/pages` — one component per route; `src/components` — the shared shell.
- `src/lib` — formatting, settings cache, and the Razorpay script loader.

Nothing in the browser decides a payment succeeded. The storefront sends the
provider's response to the server and renders whatever the server records.

## Running it

```bash
npm install
npm run dev          # http://localhost:5173, proxies /api to the gateway on :8080
npm run build        # type-check and produce dist/
npm test             # unit and component tests (vitest + testing-library)
npm run test:coverage
```

`CHOCO_GATEWAY_URL` overrides the dev proxy target (default `http://localhost:8080`).
`VITE_API_BASE_URL` sets an absolute API base for a deployment that does not serve
the API from the same origin; leave it empty behind the bundled nginx config.

## Deployment

`Dockerfile` builds the bundle and serves it with nginx, which also proxies `/api`
and `/.well-known` to the `gateway` service so the browser sees a single origin.
`nginx.conf` sets a CSP that names the Razorpay checkout host, plus HSTS-adjacent
headers (`X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`); terminate
TLS at the reverse proxy in front of it.
