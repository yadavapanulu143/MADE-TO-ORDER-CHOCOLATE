# Architecture

Single-seller, made-to-order chocolate e-commerce platform. India-only, seller-managed delivery,
online prepayment through Razorpay.

## 1. Deployment topology

Eight backend deployables behind one gateway, plus two frontend apps.

| Component | Port (local) | Role |
|---|---|---|
| storefront (React/Vite) | 5173 | customer website |
| admin (React/Vite) | 5174 | seller dashboard |
| api-gateway | 8080 | single entry point, JWT validation, rate limiting |
| identity-service | 8081 | customers, admins, addresses, OTP, tokens, audit |
| catalog-service | 8082 | products, variants, prices, website settings |
| order-service | 8083 | carts, orders, invoices, cancellations, refund requests |
| payment-service | 8084 | Razorpay orders, verification, webhooks, refunds |
| fulfilment-service | 8085 | production, packaging, serviceability, delivery |
| notification-service | 8086 | email and SMS fan-out |
| reporting-service | 8087 | read models, dashboards, exports |

### Why production and delivery share one service

Production and delivery are two consecutive phases of fulfilling one paid order, performed by the
same single operator, in one physical sequence. Splitting them would put a distributed state machine
across a single nine-state workflow and buy nothing. The service is internally divided into
`production` and `delivery` packages, so a future split is a module move rather than a rewrite.

### Why "Admin" is not a service

Admin is a role plus a UI, not a bounded context. Admin *writes* go to the owning service
(product edits to catalog, status changes to fulfilment). Admin *reads* of cross-service data come
from reporting-service read models. This avoids a service whose only job is to call seven others.

## 2. Clean Architecture

Every service is a Maven multi-module project with the same five modules:

```
<service>-domain/        plain Java: aggregates, value objects, invariants, domain events, ports
<service>-application/   use cases: command/query handlers, transaction boundaries, authz policy
<service>-adapters-in/   REST controllers, RabbitMQ listeners, webhook endpoints, validation
<service>-adapters-out/  JPA entities, Flyway, Redis, RabbitMQ publisher, provider clients
<service>-app/           Spring Boot main, wiring, configuration, actuator
```

Dependency direction is inward only: `domain ← application ← adapters ← app`.

**Enforcement, not convention.** The parent POM configures `maven-enforcer-plugin` with a
`bannedDependencies` rule. Pure domain modules set `skipEnforcer=false` and thereby fail the build
if they ever acquire Spring, JPA, Jackson, Flyway, PostgreSQL or the Razorpay SDK. This was verified
by adding `spring-core` to `common-core` and confirming the build fails with an explicit
Clean Architecture message.

## 3. Service boundaries and data ownership

One PostgreSQL instance, one **database and role per service**. PostgreSQL cannot join across
databases, so a service cannot read another's tables even by mistake.

`CONNECT` is revoked from `PUBLIC` and granted back only to the owning role. Without this, any role
could connect to every database. Verified: `choco_order` receives
`permission denied for database "choco_identity"`.

| Service | Owns | Emits |
|---|---|---|
| identity | customers, admin users, roles, addresses, OTP challenges, refresh tokens, audit log | customer.registered, customer.verified, customer.password-changed |
| catalog | products, variants, images, price history, settings | catalog.product-created/updated, catalog.variant-price-changed |
| order | carts, orders, order items, status history, invoices, cancellation/refund requests, idempotency keys | order.placed/confirmed/cancelled, order.refund-requested, order.invoice-issued |
| payment | payments, attempts, events, webhook events, refunds, disputes | payment.captured/failed/pending, payment.refund-processed, payment.dispute-raised |
| fulfilment | production jobs, ingredients, stock ledger, serviceability, delivery charge rules, deliveries | fulfilment.* |
| notification | templates, notifications, preferences | notification.sent/failed |
| reporting | read models, export audit | — |

## 4. Synchronous vs asynchronous communication

**Synchronous REST** is used only where the caller needs an answer to proceed:
- checkout quoting a price from catalog
- checkout checking PIN serviceability and delivery charge from fulfilment
- order-service creating a payment through payment-service

**RabbitMQ** carries everything else. One durable topic exchange `choco.events`. Each consumer has a
durable queue, a retry queue with TTL, and a shared dead-letter queue.

**Transactional outbox.** Every producer writes the event to an `outbox_event` row *inside the same
transaction* as the state change. A relay publishes it and marks it sent. This removes the dual-write
window where a commit fails but the message is already gone. The relay query uses
`FOR UPDATE SKIP LOCKED`, which makes it safe to run on several replicas.

**The order→payment→production saga is closed by events, not by HTTP.** `order-service` consumes
`payment.captured`, `payment.failed`, `payment.pending` and `payment.refund-processed` on its own
durable queue and moves the order's payment state from the event alone; `fulfilment-service` opens a
production job from `order.confirmed`. There is deliberately no synchronous "mark paid" endpoint, so a
browser cannot confirm its own payment and a payment can never be confirmed by anything but a verified
provider fact. The consumer refuses a capture whose amount is not the order's exact frozen total
(`PAYMENT_AMOUNT_MISMATCH`), because the initiate call carries a caller-supplied amount.

**Every consumer queue rejects rather than requeues on repeated failure.** A bounded retry
(3 attempts, 1s→2s backoff, 10s cap) followed by `default-requeue-rejected: false` sends a poison
message to the dead-letter queue instead of redelivering it in a tight loop that pegs a consumer and
hides the fault.

## 5. Money and time

- **Money** is an integer count of paise (`Money.inr(49900)`), never a `double`. Currency mismatch on
  arithmetic is rejected.
- **Time** comes from an injected `Clock` port, so OTP expiry and delivery timestamps are tested
  deterministically instead of with sleeps.
- **Timestamps** are stored and serialised as UTC; the storefront formats to IST.

## 6. Security posture

- **Passwords**: Argon2id by default (OWASP parameters: 19 MiB, 2 iterations, p=1), with a BCrypt
  cost-factor-15 fallback. A delegating hasher verifies old BCrypt hashes and flags them for upgrade
  on next login, so the algorithm can be migrated without forcing resets.
- **Tokens**: RS256 JWTs. Only identity-service holds the private key; every other service verifies
  through the JWKS endpoint, so a compromised downstream service cannot mint tokens.
- **Sessions**: stateless. No cookies, therefore CSRF tokens are unnecessary and are disabled with
  that reasoning documented in the code.
- **Ownership**: a single `CurrentUserProvider.requireOwnerOrAdmin` performs the customer ownership
  check, so one missed comparison cannot expose another customer's order.
- **CORS**: explicit origin allow-list, never a wildcard with credentials.
- **Errors**: RFC 9457 problem details with a stable machine-readable `code`. Unexpected exceptions
  return a generic 500 and are logged with the correlation id; internal messages never reach the
  client.
- **Correlation**: every request gets a `X-Correlation-Id`, validated against a safe pattern so it
  cannot be used for header injection or log forging.

### 6.1 Payment security (Phase 6)

The payment service is the one place where a mistake costs real money, so its security decisions are
recorded here rather than left to the code.

- **A payment is confirmed by the server, never by the browser.** The checkout redirect only triggers
  a server-side verification: the checkout signature is checked, then the payment is re-fetched from
  the provider, and only a captured payment for the expected amount is recorded as paid. A redirect to
  a success page is not evidence, and a forged one is not either.
- **Two signatures, both real HMACs.** The checkout signature is verified over `order_id|payment_id`
  with the account secret; the webhook signature over the raw request body with the separate webhook
  secret. The body is passed on unchanged, because re-serialising it would change whitespace and key
  order and every signature would fail. The provider SDK's constant-time comparison is used rather
  than a hand-rolled one, so a timing side channel cannot forge a signature byte by byte.
- **No secret, no trust.** When no webhook secret is configured, every webhook is refused. A
  webhook endpoint is client-reachable and the provider has no bearer token, so accepting an unsigned
  event would let anyone confirm an order for free.
- **Idempotent by construction.** One payment per order and one payment per provider order are
  enforced by unique indexes, not by application code alone. A repeated capture is a no-op in the
  aggregate, so a retried webhook delivery adds no second attempt and raises no second event.
- **Nothing sensitive is ever stored.** The schema holds identifiers and a coarse method
  classification (UPI, CARD, NETBANKING). There is no column for a card number, CVV, UPI PIN or bank
  credential; the provider's own checkout collects those. The account and webhook secrets are read
  from the environment and are never logged or returned.
- **A misconfigured service fails closed.** With no credentials the service still boots but refuses
  every money-moving call, and never reports a fake success. It does not fall back to a stub that
  pretends a payment worked.

## 7. Observability

- **Metrics**: Spring Boot Actuator `/actuator/prometheus`, scraped by Prometheus.
- **Traces**: Micrometer Tracing with the OTLP bridge to an OpenTelemetry collector, then Tempo.
- **Logs**: structured, correlated by trace id, shipped to Loki.
- **Grafana**: all three datasources auto-provisioned and cross-linked (log line → trace).

## 8. Version matrix

| Component | Version | Reason |
|---|---|---|
| Java | 21 LTS | current LTS, virtual threads available |
| Spring Boot | 3.5.16 | latest 3.5.x; pairs with Spring Cloud 2025.0.x |
| Spring Cloud | 2025.0.3 | release train built for Boot 3.5.x |
| PostgreSQL | 17.6 | current stable; `SKIP LOCKED`, `jsonb`, partial indexes |
| RabbitMQ | 4.1 | per-message routing, retry queues, dead-lettering |
| Redis | 7.4 | OTP counters, rate limits, idempotency cache |
| Flyway | (Boot-managed) | versioned, reviewable migrations |

Boot 4.x was deliberately **not** chosen: Spring Cloud 2025.1.x targets Boot 4.0.x, and the pairing
of a brand-new Boot major with a brand-new Cloud train is not what a one-person operation should run.
