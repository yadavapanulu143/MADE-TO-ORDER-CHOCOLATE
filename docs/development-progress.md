# Development Progress Checklist

Tracks every requirement from the master prompt. Statuses: **DONE**, **IN PROGRESS**, **TODO**.
A phase is only marked DONE when its tests run green and the behaviour has been verified.

**Last verified:** `mvn clean verify` in `backend/` — **1269 tests, 0 failures, 0 errors, 0 skipped**
(all modules SUCCESS; Phase 11: invoice issuance from a verified payment, the customer and seller
invoice surfaces, the `order.invoice-issued` notification, and the new `reporting-service` read models,
dashboards and CSV/XLSX exports; plus access-token revocation enforced on logout, refresh rotation,
replay detection and password change). Integration tests (`*IT`) run under the
`verify` phase via the failsafe plugin; unit tests (`*Test`) run under `test`.
The identity service has full-stack integration tests that start real PostgreSQL, Redis and
RabbitMQ containers plus a GreenMail SMTP sink, and drive the service over HTTP: Flyway/Hibernate
schema agreement and database constraints (`SchemaAndPersistenceIT`), the complete customer journey
including OTP verification and token rotation (`CustomerJourneyIT`), administrator authentication and
the customer/admin boundary (`AdminJourneyIT`), and outbox-to-broker delivery (`OutboxRelayIT`). The
catalog service has integration tests on the same containers: schema/constraint agreement
(`SchemaAndPersistenceIT`) and the full admin-and-storefront journey over real HTTP
(`CatalogueJourneyIT`). The payment service has integration tests on the same containers: the
Flyway migration, Hibernate `validate` agreement and every database constraint
(`SchemaAndPersistenceIT`), and the full payment journey over real HTTP with real HMAC signature
verification (`PaymentJourneyIT`). The fulfilment service has integration tests on the same
containers plus a real RabbitMQ consumer: the Flyway migration, Hibernate `validate` agreement and
every database constraint (`SchemaAndPersistenceIT`), and the made-to-order hand-over over a real
broker — a confirmed order opens a kitchen job, a redelivery opens none, the seller walks it to ready
for dispatch (which opens the delivery), and a cancelled order abandons its jobs
(`FulfilmentJourneyIT`). The Redis rate limiter is additionally exercised against a real
`redis:7.4-alpine` container.

**Correction (Phase 5 session).** The previously recorded figure was 446, with the gateway counted as
35 unit tests. That was a documentation error, not lost code: `GatewayPropertiesTest` has always held
14 `@Test` methods (13 when first added in `a69f8f2`, 14 after the admin-route test), no gateway test
file was ever deleted in the repository history, and no test is skipped. The verified cumulative total
is **425**. This correction is recorded here and in `AGENTS.md` so the number is not re-inflated.

## GitHub push status — RESOLVED

Everything is on `main`. The Phase 4 catalog milestone was merged into `main` (PR #2, merge commit
`3ab3f13`) and every working branch was then deleted, so `main` is the only branch locally and on
`origin`. Local `main` tracks `origin/main` at the same commit.

A first push attempt was refused (`403 Permission ... denied`), the same class of blocker recorded in
earlier sessions: the credential in the environment had **read-only contents** permission for
`yadavapanulu143/MADE-TO-ORDER-CHOCOLATE` (`POST /git/refs` returned `403 Resource not accessible by
integration`), even though `$GITHUB_TOKEN` authenticated and the REST API reported
`permissions.push: true` for the repository — that field reflects the *user*, not the token. The
token was refreshed in the environment and the push then succeeded, so the earlier blocker is again
resolved by a token refresh rather than by any code change.

If it blocks again: grant the GitHub App `contents: write` on `yadavapanulu143/MADE-TO-ORDER-CHOCOLATE`,
or supply a user PAT with `repo` scope as `$GITHUB_TOKEN`. No force-push, history rewrite or
credential handling was performed.

Latest milestone (Phases 7 and 8, fulfilment-service: made-to-order production, ingredients and
seller-managed delivery) is pushed to `origin/main` at commit `6c86892`; the remote hash was verified
equal to the local hash after the push. The push succeeded on the first attempt this session, so no
token refresh was needed. The previous milestone (Phase 6, payment-service: Razorpay orders,
verification, webhooks, refunds and disputes) is at `707f193`.

**Phase 14 security hardening pushed.** The F-2 (fail-closed production secrets) and F-3
(proxy-aware HSTS) fixes are on `origin/main` at commit `8c996e1`; the remote hash was verified equal
to the local hash after the push and the working tree is clean. The push succeeded on the first
attempt this session.

**Reporting milestone pushed.** The reporting service (read models, dashboards, CSV/XLSX exports) and
the gateway routing coverage for its surface are pushed to `origin/main` at commit `105fb6d`; the
remote hash was verified equal to the local hash (`git rev-parse HEAD` == `git rev-parse origin/main`)
and the working tree is clean. The push succeeded on the first attempt; no token refresh, force-push
or history rewrite was needed. The preceding milestone (invoice issuance and access) is at `4a708c7`.

**Delivery-rescheduled notification completed (Phase 10).** Auditing the notification service against
the requirement list ("Delivery failed or rescheduled") found a real gap, not a missing test:
`Delivery.reschedule()` moved the status but raised **no domain event**, so a customer who asked for a
redelivery was never told one had been arranged — the one delivery outcome the requirement names
explicitly, silently unreachable. The fix raises a new `fulfilment.delivery-rescheduled` event on every
reschedule, adds the `DELIVERY_RESCHEDULED` event type, notification type and template, and binds the
queue to the new routing key. `FulfilmentJourneyIT.reschedulingPublishesAnEvent` proves the event
reaches the broker and `NotificationJourneyIT.deliveryRescheduledReachesTheCustomer` proves it becomes a
message in the SMTP sink.

**Delivery notifications now key the dedupe on the event id, not the delivery id (Phase 10).** The
listener's `deliveryEvent` derived its dedupe key from the `deliveryId`, so every step of the same type
on one delivery collapsed into a single message: a second failed attempt, or a reschedule after a
failed attempt, was suppressed and the customer heard nothing. The key is now the payload's `eventId`
(with a `deliveryId` fallback for an older payload), which is stable across a broker redelivery — the
idempotency the dedupe actually needs — while a genuinely new event gets its own key.
`NotificationJourneyIT.aSecondRescheduleIsNotSwallowedByTheDedupeKey` and
`aRedeliveredRescheduleIsDeduplicated` pin both directions — a new event sends, the same event does
not. `fulfilment-domain` moves 88 → 90, `fulfilment-app` 28 → 31, `notification-application` 34 → 35
and `notification-app` 22 → 27 tests; the cumulative total moves 1262 → **1269**.

**Serviceability public-path mismatch fixed (Phase 11.3).** Driving the whole journey with
`scripts/e2e-journey.py` surfaced a real defect the per-service integration tests had missed: the
gateway's public-path list and the storefront treat `/api/v1/serviceability/**` as unauthenticated —
the "Delivery areas" page sits outside `ProtectedRoute` — but the fulfilment service's own
`choco.security.public-paths` was empty, so the service answered `401` for an anonymous visitor. The
edge's public list is a forwarding decision; the owning service still makes the authorisation
decision, and the two lists must agree. `FulfilmentJourneyIT.anonymousIsAnswered` now pins the
service end (proven meaningful: it returns `401` and fails when the fulfilment `public-paths` is
emptied) and `FulfilmentJourneyIT.anonymousCannotReachAdmin` proves the administrative kitchen stayed
closed. `fulfilment-app` moves 28 → 30 tests; the cumulative total moves 1260 → **1262**.

**End-to-end harness hardened and wired into CI (Phase 11.3).** The same harness that found the
serviceability defect assumed a catalogue and a serviceable PIN code already existed, so it only ran
against a warm, hand-seeded stack. On a genuinely fresh volume — which is exactly what CI starts from
— the catalogue is empty and no PIN code is covered, so the journey stopped at the first checkout
step. The harness now seeds a published product and covers the delivery PIN through the seller API
when it finds the stack empty, using a run-stamped product name so a rerun against a partly-populated
volume cannot collide with a leftover draft. It was then proven against a fresh `down -v` → `up`
cycle: **33 steps, 0 failures, 0 skipped**. A new `.github/workflows/e2e.yml` boots the compose stack,
waits for every service to report healthy (not only the gateway, because the journey drives the
services the gateway proxies to), runs the harness, dumps the service logs on failure and tears the
stack down. This is the check that exercises every service together; the per-service `*IT` suites
still prove each seam in isolation.

---

## Distributed consistency review — the order↔payment saga

An ACID and cross-service consistency review of every completed module found two defects on the
order↔payment boundary. Both are now fixed and covered by tests that fail without the fix.

| # | Defect | Why it mattered | Fix |
|---|---|---|---|
| 1 | The order service never consumed payment events. `payment.captured`, `payment.failed`, `payment.pending` and `payment.refund-processed` were published to a topic exchange no order-service queue was bound to. | The whole saga was dead: a customer could pay, the payment service would record `PAID`, and the order would stay `PAYMENT_PENDING` forever. Preparation never started and the fulfilment service never received `order.confirmed` — a paid order that is never made. `OrderCommandService.markPaid` was reachable only from a test. | `OrderMessagingConfiguration` declares a durable queue on `choco.events` bound to the four routing keys, with a retry queue and dead-letter queue; `PaymentEventsListener` dispatches each to `OrderCommandService`. `PaymentEventsIT` proves the behaviour over a real broker. |
| 2 | The order accepted a payment for any amount. `InternalPaymentController.initiate` takes a caller-supplied `amountMinorUnits`, the payment service verifies a capture only against the amount it was *told* to collect, and `Order.markPaid` did not look at the amount at all. | A customer could initiate a payment for ₹1 against a ₹5,000 order, pay it, and have the order confirmed and the chocolate made and shipped. Direct money loss on every order. | `Order.markPaid` now compares the collected amount to the order's frozen `total()` and throws `PAYMENT_AMOUNT_MISMATCH` on any difference; the listener threads the amount from the event through `OrderCommandService`. `PaymentEventsIT.a_capture_for_the_wrong_amount_is_refused` and four `OrderTest` cases pin it. |

Both fixes were verified by a negative control: with the `@RabbitListener` commented out, four of the
five saga integration tests fail, which proves the tests exercise the real consumer rather than a
mock. The listener was then restored.

**A latent hot-loop was also fixed.** Neither `order-service` nor `fulfilment-service` set
`default-requeue-rejected: false`, so a message that always threw was redelivered in a tight loop
forever — pegging a consumer and hiding the fault. Both now use a bounded retry (3 attempts, 1s→2s
backoff, 10s cap) and dead-letter on exhaustion.

**The residual gap is now closed.** There is an order-service path that starts a payment:
`POST /api/v1/orders/me/{orderId}/payment` reads the order's own frozen total and calls the payment
service's idempotent `POST /api/v1/payments/orders/{orderId}/initiate` through a `PaymentPort`, and
returns the provider order id and publishable key for the storefront to open Razorpay checkout. The
amount is never taken from the request, so the same frozen total that `markPaid` checks on the way
back is the figure the provider is asked to collect on the way out — the two ends of the wire agree
on one number. `OrderPaymentIT` (11 cases) proves the path over real HTTP and a real PostgreSQL,
including ownership, verification gating, the paid-order refusal, a downstream outage, and two
simultaneous starts.

**Push status.** The saga fixes are pushed to `origin/main` at commit `98a9f36`, and the payment
initiation milestone at commit `f7f135c`; both remote hashes were verified equal to the local hash
after the push, and both pushes succeeded on the first attempt.

---

## Phase 1 — Project foundation

| # | Requirement | Status | Evidence |
|---|---|---|---|
| 1.1 | Monorepo structure | DONE | `backend/`, `frontend/`, `infra/`, `docs/` |
| 1.2 | Toolchain provisioned | DONE | JDK 21.0.13, Maven 3.9.9, Node 24, Docker 29.9.3 |
| 1.3 | Parent POM with pinned versions | DONE | `backend/pom.xml` — Boot 3.5.16, Cloud 2025.0.3 |
| 1.4 | `common-core` (framework-free domain primitives) | DONE | 26 tests green |
| 1.5 | `common-web` (RFC 9457 errors, correlation ids, OpenAPI) | DONE | 7 tests green |
| 1.6 | `common-security` (Argon2id/BCrypt-15, JWT, RBAC) | DONE | 23 tests green |
| 1.7 | `common-events` (envelope, transactional outbox, RabbitMQ) | DONE | 6 tests green |
| 1.8 | `common-test` (Testcontainers bases, fakes) | DONE | Compiles, installed |
| 1.9 | Clean Architecture enforced by build | DONE | Enforcer rule proven to fail on a Spring dependency |
| 1.10 | Docker Compose infra stack | DONE | 8 containers verified healthy |
| 1.11 | Per-service PostgreSQL databases + roles | DONE | 7 DBs, 7 roles created |
| 1.12 | **Cross-service DB isolation** | DONE | `permission denied for database "choco_identity"` verified |
| 1.13 | `.env.example` (no real secrets) | DONE | Committed; `.env` gitignored |
| 1.14 | Observability stack | DONE | Prometheus/Grafana/Loki/Tempo all verified responding |
| 1.15 | Documentation | DONE | `docs/architecture.md`, `docs/local-development.md`, this file |

---

## Phase 2 — Backend foundation

| # | Requirement | Status | Evidence |
|---|---|---|---|
| 2.1 | Service module skeleton (5-module Clean Architecture each) | DONE | identity-service: domain / application / adapters-out / adapters-in / app |
| 2.2 | API Gateway routing | DONE | `gateway/` module on port 8080; one route per service from `RoutingConfiguration`; edge JWT validation via the shared `JwtDecoder`; per-IP Redis-backed throttle (`EdgeRateLimitFilter`); retry (GET only) + Resilience4j circuit breaker per service with an RFC 9457 503 fallback; CORS from the shared allow-list. 34 tests green (13 unit + 21 IT: Routing, RateLimit, CircuitBreaker, GatewaySecurity) |
| 2.3 | Flyway migrations per service | DONE | `identity-app/src/main/resources/db/migration/V1__identity_schema.sql` |
| 2.4 | Redis integration (cache, rate limiting) | DONE | `shared/common-redis`; `RedisRateLimiterIT` (4 tests) green against a real Redis |
| 2.5 | Inter-service REST client with timeouts/retries | DONE | Gateway proxy client timeouts (`spring.http.client.*`), GET-only retry and a circuit breaker per route; a business service client will reuse the same policy when the first consumer appears |
| 2.6 | Health checks + readiness probes | DONE | `/actuator/health` public; probes enabled |

## Phase 3 — Authentication and security

| # | Requirement | Status | Evidence |
|---|---|---|---|
| 3.1 | Customer registration (name, email, mobile, password) | DONE | `AuthController#register`; `RegistrationServiceTest` (13 tests) |
| 3.2 | Email OTP verification | DONE | `POST /api/v1/auth/customers/{id}/verifications?channel=EMAIL` |
| 3.3 | Mobile OTP verification | DONE | Same endpoint, `channel=MOBILE`; `VerificationServiceTest` (10 tests) |
| 3.4 | OTP expiry, resend limits, max attempts | DONE | Enforced in the `OtpChallenge` aggregate; `AbuseGuard` adds per-customer resend/verify budgets |
| 3.5 | Login / logout | DONE | `AuthenticationServiceTest` (14 tests); refresh-token rotation with replay detection. Logout now also revokes the session's **access** token: the token names its session as `jti`, and every service's `RevocationAwareJwtDecoder` refuses a revoked session. `AdminAuthenticationServiceTest` proves the same for administrators |
| 3.6 | Password reset | DONE | `PasswordResetServiceTest` (9 tests) |
| 3.7 | Argon2id hashing (BCrypt-15 fallback) | DONE | `common-security`; 23 tests green |
| 3.8 | Admin authentication | DONE | Separate `AdminAccount` aggregate and `admin_accounts` table (`V3__admin_accounts.sql`); `POST /api/v1/auth/admin/login`, `/refresh`, `/logout`; role (`ADMIN`/`SUPER_ADMIN`) read from the row, never from input; first admin seeded once from `choco.admin.bootstrap.*` (idempotent, refuses a weak password, never overwrites); admin rate limits use separate counters; `AdminAccountTest` (8) + `AdminAuthenticationServiceTest` (13) + `AdminProvisioningServiceTest` (5) + `AdminAuthControllerTest` (5) + `AbuseGuardTest` (+3); verified over HTTP (16 checks) including customer/admin boundary isolation |
| 3.9 | Role-based authorization | DONE | `role`/`permissions` JWT claims → authorities; `@EnableMethodSecurity` |
| 3.10 | Login rate limiting / brute-force protection | DONE | `AbuseGuard` + `RedisRateLimiter`; 10 unit tests + 4 integration tests + verified over HTTP |
| 3.11 | Verification codes never logged in production | DONE | `CHOCO_LOG_OTP` defaults to `false` in both the shipped config and the fail-closed `@Value` fallback; a loud startup WARN fires when it is enabled. `NotificationAdapterOtpLoggingTest` (6) + `OtpLoggingDefaultTest` (2), both confirmed to fail on a regression. Verified at runtime: 0 codes logged by default, 2 when opted in |

## Phase 4 — Customer and product management

| # | Requirement | Status | Evidence |
|---|---|---|---|
| 4.1 | Customer profile view/update | DONE | `GET /me`, `PUT /me` (name), `PATCH /me/contact` (email/mobile, re-verifies the changed channel); `ProfileServiceTest` (20); verified over HTTP (22 checks) |
| 4.2 | Delivery address management | DONE | `AddressController` (CRUD + default); `AddressTest` (13) + `AddressServiceTest` (25); `V2__customer_addresses.sql`; one-default-per-customer enforced by a partial unique index and verified in PostgreSQL; ownership isolation verified over HTTP (21 checks) |
| 4.3 | Verification status display | DONE | `CustomerView` carries `emailVerified`/`mobileVerified`/`fullyVerified` |
| 4.4 | Password change from profile | DONE | `POST /me/password`; requires the current password, applies the shared password policy, rejects reuse, revokes every refresh token; `PasswordChangeServiceTest` (12); verified over HTTP (10 checks) |
| 4.5 | Product catalogue (multi-product ready) | DONE | New `catalog-service` (domain / application / adapters-out / adapters-in / app); `Product` aggregate with `ProductVariant`, `ProductImage`, `ProductAttribute`; public storefront `GET /api/v1/products` and `GET /api/v1/products/{slug}` (published only); admin CRUD under `/api/v1/admin/catalog/**` guarded by `product:write`. `ProductTest` (19) + `ProductCatalogServiceTest` (15) + `ProductQueryServiceTest` (9) green |
| 4.6 | Product images | DONE | Upload via `POST /api/v1/admin/catalog/products/{id}/images` (multipart, allow-listed content types, size-capped), served immutably from `GET /api/v1/catalog/images/**` with `nosniff`; `LocalImageStorageAdapter` confines writes to a configured root; exercised over HTTP in `CatalogueJourneyIT` |
| 4.7 | Pricing + variants/sizes | DONE | Variants carry an exact minor-unit price, per-variant min/max order quantity and SKU uniqueness; storefront derives a "from" price; `VariantPriceChanged` published on a price edit |
| 4.8 | Admin product management | DONE | `AdminCatalogController`: create/edit, add/edit/remove variants, upload/edit/remove images and set the primary image, attributes, publish/unpublish/archive, and `PUT /api/v1/settings` for seller details and policies (`settings:write`); website settings readable publicly at `GET /api/v1/settings` |

## Phase 5 — Shopping and orders

| # | Requirement | Status |
|---|---|---|
| 5.1 | Shopping cart | DONE |
| 5.2 | Quantity management + configurable limits | DONE |
| 5.3 | Checkout validation (availability, quantity, verification, address, PIN) | DONE |
| 5.4 | Order creation with immutable price snapshot | DONE |
| 5.5 | Order history (own orders only) | DONE |
| 5.6 | Order status transitions + history | DONE |

**Order Management Service (Phase 5).** A new five-module Clean Architecture service under
`backend/services/order-service/` (`order-domain`, `order-application`, `order-adapters-out`,
`order-adapters-in`, `order-app`), added to the parent reactor and to `dependencyManagement`.

Milestone 5a — domain layer (done, framework-free; enforcer rule passes):

- `Order` aggregate: priced lines, frozen delivery address and customer snapshot, and three separate
  tracks (payment, preparation, delivery) folded into one `OrderStatus`. The single invariant that
  matters most is enforced in one place — `transitionTo` consults `OrderStatus.canTransitionTo`, so an
  order can never be prepared before a verified payment confirms it. Product names, labels, SKUs and
  unit prices are snapshots taken at checkout and never re-read, so a later catalogue change cannot
  rewrite what a customer agreed to pay.
- `Cart` aggregate: one active cart per customer; the same variant added twice increments one line;
  the per-line ceiling and line count are bounded; display prices are refreshed from the catalogue.
- `OrderStatus` (17 values) with an explicit transition table; `PaymentStatus`, `PreparationStatus`,
  `DeliveryStatus` as the independent tracks; `OrderLine`, `DeliveryAddress`, `CustomerSnapshot`,
  `StatusChange`, `OrderNote`, `IdempotencyRecord` value objects.
- Domain events: `order.placed`, `order.confirmed`, `order.status-changed`, `order.cancelled`,
  `order.refund-requested`.
- 98 domain unit tests, all passing.

Two bugs the domain tests caught and fixed before anything shipped:

1. `PREPARATION_STARTED → PREPARATION_ON_HOLD` was missing from the transition table, so holding a
   started order was impossible — the exact case an ingredient shortage creates.
2. `markPaid` short-circuited on the idempotency check before the conflict check, so a confirmation
   naming a *different* payment for an already-paid order was silently swallowed instead of refused.
   The conflict check now runs first; a duplicate confirmation of the same payment is still idempotent.

Milestone 5b — application layer (done):

- Use cases, framework-free and constructed by `OrderApplicationConfiguration`: `CartService`
  (add/update/remove a line, refresh display prices from the catalogue), `CheckoutService` (quote and
  place an order, snapshotting prices, delivery address and customer contact), `OrderCommandService`
  (paid confirmation, preparation and delivery transitions, notes, cancellation, refund request) and
  `OrderQueryService` (a customer's own orders, the admin listing).
- Outbound ports the use cases depend on: `CatalogPort`, `CustomerPort`, `ServiceabilityPort`,
  `OrderNumberGenerator`, `AuditLogPort`. The application layer holds no HTTP, JPA or broker import.
- Idempotency is a first-class concern: checkout consults an `IdempotencyRecord` scoped by operation
  and key, so a double-submitted checkout returns the first order rather than creating a second.
- 54 application unit tests (cart 12, checkout 20, command 15, query 7), all passing.

Milestone 5c — persistence, app bootstrap and downstream clients (done):

- **Persistence adapters** (`order-adapters-out`): JPA entities for `orders`, `order_lines`,
  `order_status_history`, `order_notes`, `carts`, `cart_lines`, `idempotency_record` and the shared
  `outbox_event`/`audit_log`; an explicit `OrderPersistenceMapper` (no reflective mapping, so a column
  rename cannot silently change the domain); and repository adapters that carry the current row
  version onto the mapped entity so optimistic locking actually fires. Reads are
  `@Transactional(readOnly = true)` because the mapper touches lazy child collections.
- **Flyway migration** `V1__order_schema.sql`: every monetary column is a `BIGINT` count of paise; the
  stored total is constrained to equal its parts; currency is constrained to `INR`; the order number
  is unique; one cart per customer and one line per variant are unique constraints; the idempotency
  key is unique per scope. The human-readable order number comes from a PostgreSQL sequence, so two
  concurrent checkouts cannot be handed the same number.
- **App bootstrap** (`order-app`): `OrderServiceApplication`, `CoreConfiguration` (the `Clock` and
  `IdGenerator` ports), `application.yml`, and a `SequenceOrderNumberGenerator` that draws the order
  number from the database sequence.
- **Downstream clients** (`order-adapters-out`): `CatalogClient`, `CustomerClient` and
  `ServiceabilityClient` built on a shared `RestClient` factory (`DownstreamClients`) that maps any
  downstream failure to `DEPENDENCY_UNAVAILABLE` without leaking the downstream message, and forwards
  the caller's own bearer token rather than holding a privileged credential.
- **Catalog internal endpoint**: `GET /api/v1/catalog/variants?ids=…` (`InternalCatalogController`)
  resolves a batch of variant ids to their current price and availability, published products only, so
  the order service can price and validate a cart in one call without ever seeing a draft.
- Tests: 11 client tests against a real HTTP server, 12 order schema/constraint integration tests
  against a real PostgreSQL, and 4 new catalogue-journey integration tests for the internal lookup.
  Full `mvn verify` — **604 tests, 0 failures, 0 errors, 0 skipped**.

Milestone 5d — REST layer and the order journey (done):

- **Controllers** (`order-adapters-in`): `CartController` (`/api/v1/carts/me`, add/update/remove/clear),
  `CheckoutController` (`/api/v1/checkout/quote` and `POST /api/v1/checkout` with an
  `Idempotency-Key`), `OrderController` (`/api/v1/orders/me`: history, one order, cancel, refund
  request) and `AdminOrderController` (`/api/v1/admin/orders`: search, counters, preparation,
  delivery assignment and progress, notes, cancellation, payment retry). The customer id always comes
  from the token, never the URL, so there is no id to tamper with; the query service still asserts
  ownership on a single-order read.
- **Gateway routes**: added `/api/v1/checkout/**` and `/api/v1/admin/orders/**` to the order route.
  Neither existed, so both the checkout and the whole seller order surface would have 404'd at the
  edge even though the service served them.
- **`OrderJourneyIT`** — 27 tests through the real filter chain, controllers, use cases and
  PostgreSQL, with the shared in-process downstream stub answering the catalogue, identity and
  delivery calls. Covers the cart, checkout (quote, placement, price snapshot, cart clearing,
  idempotent replay, key-reuse conflict, the fully-verified gate, the empty-cart refusal), customer
  order ownership, cancellation and refund requests, and the full seller lifecycle from payment
  confirmation through preparation, packaging, dispatch and delivery.

Four defects the journey tests caught, none of which a unit test could see:

1. **Order and cart lines were persisted with a null parent.** The mapper derived each line's primary
   key from the parent id but never set the owning-side `order`/`cart` reference, so the not-null FK
   was violated on the first insert. The order aggregate had never been round-tripped through the
   database before — the persistence tests inserted orders with raw SQL. Fixed by setting the parent
   on the line before it is added to the collection.
2. **A customer token could read the entire seller order listing.** The admin controller carried no
   class-level role check, so any authenticated caller reached `/api/v1/admin/orders`. Added
   `@PreAuthorize` requiring `ADMIN` or `SUPER_ADMIN` at the class level, with the per-action
   permissions still narrowing each method.
3. **Delivery assignment dispatched the order.** `assignDelivery` moved the order straight to
   `OUT_FOR_DELIVERY`, collapsing the requirement's distinct "Assigned for Delivery" and "Out for
   Delivery" steps and making the separate dispatch step unreachable. Assignment now records the
   person and sets the delivery track to `ASSIGNED` while the order stays `READY_FOR_DISPATCH`;
   `markOutForDelivery` performs the hand-off.
4. **A refund could be requested for an order that was never paid.** The test expected success where
   the domain correctly refused; the expectation was wrong, and the case is now pinned as a refusal
   alongside a paid-order success.

  Full `mvn verify` — **636 tests, 0 failures, 0 errors, 0 skipped**.

## Phase 6 — Razorpay integration

| # | Requirement | Status |
|---|---|---|
| 6.1 | Razorpay order creation | DONE — `RazorpayPaymentGateway.createOrder`; `PaymentService.initiate`; idempotent per order and per key |
| 6.2 | Payment initiation | DONE — `POST /api/v1/payments/orders/{orderId}/initiate`; returns provider order id and publishable key |
| 6.3 | Signature verification (backend only) | DONE — `RazorpayPaymentGateway.verifyCheckoutSignature` (HMAC over `order_id\|payment_id`); provider re-fetched before a payment is recorded as paid |
| 6.4 | Webhook signature verification | DONE — `POST /api/v1/webhooks/razorpay` verifies the raw body before acting; refused when no secret is configured |
| 6.5 | Idempotent payment processing | DONE — unique `order_id` and `provider_payment_id` indexes; `markCaptured` is a no-op on a repeat; `Idempotency-Key` honoured with fingerprint conflict detection |
| 6.6 | Duplicate event protection | DONE — `PaymentJourneyIT.duplicate_capture_webhook_is_idempotent` (one attempt row after two deliveries) |
| 6.7 | Payment success/failure/pending handling | DONE — `PaymentService.applyReportedState`; `authorized` is pending, not paid; provider unreachable leaves it pending |
| 6.8 | Payment transaction history | DONE — `payments`, `payment_attempts`, `refunds`, `disputes`; customer and admin read surfaces |
| 6.9 | Refunds and disputes | DONE — `RefundService` + `POST /api/v1/admin/payments/{id}/refunds` (permission-gated); dispute webhooks recorded |
| 6.10 | Unit tests | DONE — 40 domain + 31 application + 18 adapter |
| 6.11 | Integration tests | DONE — 18 `PaymentJourneyIT` + 7 `SchemaAndPersistenceIT` + 5 architecture |

**Payment service (Phase 6).** A five-module Clean Architecture service under
`backend/services/payment-service/`, on port 8084, owning the `choco_payment` database. It holds the
one rule that makes the whole shop trustworthy: a payment is only ever recorded as successful after
the *server* has confirmed it with the provider. The checkout signature is verified, then the payment
is re-fetched from the provider, and only a captured payment for the expected amount is recorded as
paid — a browser redirect to a success page is never taken as proof.

- **Domain** (`payment-domain`): `Payment` aggregate with `PaymentStatus` state machine,
  `PaymentMethod` (coarse UPI/CARD/NETBANKING classification only — never a card number or UPI id),
  `Refund`, `Dispute`, `PaymentAttempt`, and the `PaymentPending`/`PaymentCaptured`/`PaymentFailed`/
  `RefundProcessed`/`DisputeRaised` events. 40 tests.
- **Application** (`payment-application`): `PaymentService`, `PaymentWebhookService`,
  `RefundService`, `PaymentQueryService` — plain classes wired by configuration, no Spring import in
  the layer. 31 tests.
- **Adapters out** (`payment-adapters-out`): the Razorpay SDK is confined to
  `RazorpayPaymentGateway` (an ArchUnit rule enforces this), JPA persistence, and the audit log.
  18 tests including the signature algorithms.
- **Adapters in / app** (`payment-adapters-in`, `payment-app`): customer, internal and admin
  controllers, the webhook endpoint, and the Spring Boot bootstrap with the Flyway migration.

**Money never leaves the database as a float.** Every monetary column is a `BIGINT` count of paise,
with a `CHECK` constraint that the amount is positive and that the currency is INR. No column holds a
card number, CVV, UPI PIN or bank credential — the provider's own checkout collects those.

**The webhook is the authoritative path.** A UPI collect confirmed after the customer closed the tab
is only ever seen by the webhook, so it is handled with the same care as the browser callback: the
signature over the raw body is checked first, and the event is applied idempotently so a retried
delivery changes nothing.

### What the payment journey tests pinned down

| Observation | Symptom | Resolution |
|---|---|---|
| Verification is a customer action | `POST /api/v1/payments/orders/{id}/verify` returned 404 | The endpoint is `/api/v1/payments/me/orders/{id}/verify`; the journey test now asserts the real path, so the contract is recorded rather than assumed |
| A refund requires a captured payment | Refunding a merely-initiated payment returned 409 | Correct behaviour: the journey test verifies the capture first, matching the real sequence |

## Phase 7 — Made-to-order production

| # | Requirement | Status |
|---|---|---|
| 7.1 | Preparation queue from confirmed orders | DONE — `OrderEventsListener` consumes `order.confirmed` and opens one job per line; no endpoint or event can open a job, so nothing is made before payment is verified |
| 7.2 | Production statuses (Received→Awaiting→Started→Completed→Packaging→Ready) | DONE — `ProductionStatus` state machine with an explicit transition table; hold/resume and a cancellation window; illegal jumps refused |
| 7.3 | Production notes | DONE — `ProductionNote` on the job, length-capped, persisted with the aggregate |
| 7.4 | Optional ingredient/raw-material inventory | DONE — `Ingredient` aggregate (unit, on-hand, minimum, active), `RecipeLine` per variant, append-only `StockLedgerEntry`; nothing is assumed, a variant with no recipe consumes nothing |
| 7.5 | Low-stock alerts | DONE — `LowStockDetected` raised on consumption to or below the minimum; a zero minimum never alerts |
| 7.6 | Concurrent order handling | DONE — capacity cap on units in progress (`choco.fulfilment.capacity.max-units-in-progress`, zero = no cap) and optimistic locking on every aggregate |

## Phase 8 — Seller-managed delivery

| # | Requirement | Status |
|---|---|---|
| 8.1 | Serviceable PIN-code management | DONE — `ServiceableArea` (exact PIN or prefix, longest-prefix wins); admin CRUD under `/api/v1/admin/fulfilment/serviceable-areas` (`settings:write`) |
| 8.2 | Configurable delivery charges | DONE — per-area charge in paise; a quote returns the charge or a reasoned "not serviceable", never an error |
| 8.3 | Delivery assignment | DONE — `Delivery.assign`; re-assignment allowed before dispatch only |
| 8.4 | Delivery workflow (10 states) | DONE — `DeliveryStatus` state machine: pending, assigned, out for delivery, delivered, attempt failed, rescheduled, cancelled, returned |
| 8.5 | Delivery attempts + failure reasons | DONE — `DeliveryAttempt` rows numbered from one; a failed attempt carries the free-text reason the seller typed |
| 8.6 | Customer delivery tracking | DONE — `CustomerFulfilmentController` exposes the customer's own preparation and delivery progress, owner-scoped |
| 8.7 | No fabricated GPS/courier data | DONE — there is no tracking number and no position anywhere in the model; the customer sees exactly what the seller recorded |

**Fulfilment service (Phases 7 and 8).** A five-module Clean Architecture service under
`backend/services/fulfilment-service/`, on port 8085, owning the `choco_fulfilment` database. It holds
the made-to-order model end to end: a confirmed order becomes a kitchen job, the seller walks the job
through preparation, and the moment it is ready for dispatch a delivery record is opened against the
address frozen at checkout. Delivery is seller-managed, so there is no courier SDK here — the only
outbound call is to a sibling service, and no GPS position or tracking number is invented.

- **Domain** (`fulfilment-domain`): `ProductionJob` (status machine, notes, ingredient-consumption
  flag), `Ingredient`/`RecipeLine`/`StockLedgerEntry`/`StockLevel`, `ServiceableArea`, `Delivery`/
  `DeliveryAttempt`, and the production and delivery events. 86 tests.
- **Application** (`fulfilment-application`): `ProductionService`, `ProductionQueryService`,
  `DeliveryService`, `InventoryService` — plain classes wired by configuration, no Spring import in
  the layer. 36 tests.
- **Adapters out** (`fulfilment-adapters-out`): JPA persistence with optimistic locking, the audit
  log (written in its own transaction so a rejected change still leaves a record), and the shared
  outbox.
- **Adapters in / app** (`fulfilment-adapters-in`, `fulfilment-app`): the admin, serviceability and
  customer controllers, the `order.confirmed`/`order.cancelled` consumer, and the Spring Boot
  bootstrap with the Flyway migration. 27 tests (4 architecture + 23 integration).

**Nothing is made before a payment is verified.** The only way a production job is created is the
`order.confirmed` event, which the order service raises only after it has recorded a verified payment.
There is no REST endpoint that opens a job and no other event bound to the queue, so the rule is
enforced by the shape of the system rather than by a check someone could forget.

**The hand-over is idempotent by construction.** A redelivered `order.confirmed` finds the jobs it
already opened (keyed on order id and variant id) and opens nothing new; a redelivered
`order.cancelled` finds the jobs already cancelled and does nothing. A broker retry, or a consumer
restart before the acknowledgement, cannot double the kitchen's work or return ingredients twice.

**Ingredient tracking is optional by design.** The requirements are explicit that raw-material
inventory may not be configured at first and that made-to-order preparation must work without it, so
an ingredient is a separate aggregate the seller opts into: a job's preparation status advances
whether or not any ingredient exists, and consumption is only attempted when a recipe has been
configured for the variant. A variant with no recipe consumes nothing.

## Phase 10 — Notifications

The `notification-service` (port 8086, database `choco_notification`) is implemented and verified.
It consumes platform events on its own durable queue, renders the message from a template, records it
as an aggregate with a deduplication key, and hands it to a configurable email or SMS provider. The
admin delivery log is exposed at `/api/v1/admin/notifications`, permission-gated and routed by the
gateway.

| # | Requirement | Status |
|---|---|---|
| 10.1 | Email provider (configurable) | DONE — SMTP sender, host/port/credentials from environment; a test sink stands in for a provider |
| 10.2 | SMS provider (configurable) | DONE — HTTP gateway sender; with no gateway URL configured, sends are logged and reported, never fabricated |
| 10.3 | Registration/verification notifications | DONE — the identity service sends the OTP and welcome mail directly; the notification service consumes `customer.registered`/`customer.verified`/`customer.contact-changed` to keep its contact projection current, so later messages greet the customer by name |
| 10.4 | Order + payment notifications | DONE — `order.placed`, `order.confirmed`, `order.cancelled`, `payment.captured`, `payment.failed`, `payment.pending` |
| 10.5 | Production + delivery notifications | DONE — `production.started/completed`, `ready-for-dispatch`, `delivery.assigned/out-for-delivery/delivered/failed/rescheduled`. `fulfilment.delivery-assigned` is now actually raised: the fulfilment service's `Delivery.assign` drains the aggregate's events on the first `PENDING → ASSIGNED` edge, so the customer is told a delivery has been arranged. `fulfilment.delivery-rescheduled` is raised on every retry by `Delivery.reschedule` — before this the reschedule moved the status but raised nothing, so a customer who asked for another attempt was never told one was arranged. (A `packaging.completed` subscription was removed: the packaging transition raises no event, so the binding could never fire.) |
| 10.6 | Cancellation/refund notifications | DONE — `refund.requested` and `dispute.raised` to the seller; `refund.processed` to the customer |
| 10.7 | Configurable templates and provider configuration | DONE — templates in the application layer; provider settings from environment variables |
| 10.8 | Delivery status tracking | DONE — every notification's status and attempt count is visible in the admin log |
| 10.9 | Idempotency and duplicate-event protection | DONE — a `dedupe_key` derived from event id + type is unique at the database; a redelivery creates no second message |
| 10.10 | Durable RabbitMQ event handling | DONE — durable queue, manual-safe acknowledge, `prefetch: 1`, retry with backoff, dead-letter on exhaustion |
| 10.11 | Bounded retries and dead-letter handling | DONE — `FAILED` is a resting state (visible and resendable); the retry query selects `PENDING` and `FAILED`; budget exhausted → `DEAD_LETTER` |
| 10.12 | Secure logging without sensitive information | DONE — a provider failure is stored as a bounded, provider-neutral summary, never a raw transport error with the address in it |
| 10.13 | A provider failure must not reverse an order or payment | DONE — the consumer records the failure on the notification and returns; nothing in the path writes to order or payment state |

### Notification integration tests (`mvn verify`)

| Class | Tests | What it proves |
|---|---|---|
| SchemaAndPersistenceIT | 5 | Flyway applied V1; Hibernate `validate` matches; a negative attempt count, a blank dedupe key and an out-of-range status are rejected, and a duplicate dedupe key is refused by the unique index |
| NotificationJourneyIT | 11 | A real event on the broker becomes one message in the SMTP sink: the greeting is filled from the contact projection, a redelivery is suppressed by the dedupe key, a provider outage is recorded rather than thrown, a low-stock alert carries the real level and threshold, an assigned delivery reaches the customer, a failed delivery carries the reason the event gave, a rescheduled delivery tells the customer a new attempt is planned, a second reschedule on one delivery is not swallowed by the dedupe key (which is keyed on the event id, not the delivery id), and an `order.invoice-issued` emails the customer the invoice number and the billed total |
| AdminNotificationApiIT | 7 | The admin log over real HTTP: list, counters and read are permission gated; a customer is refused; an unknown id is a 404; a delivered notification cannot be resent (409); a customer cannot trigger a resend (403) |
| CleanArchitectureTest | 4 | The domain and application layers depend on no framework, adapter or provider SDK; the mail/SMS sender never leaks into the domain |

## Invoice issuance and access (Phase 11)

The invoice lives in the order service because an invoice is a statement about an order: it is the
seller's record of what was billed, and the order already holds the frozen customer, address and priced
lines it needs. Keeping it there avoids a second service reading the order's tables, which would break
the one-database-per-service rule.

- **Issued from a verified payment, never on request.** `PaymentEventsListener` calls
  `InvoiceService.issueForOrder(orderId)` in the same consumption that confirms the order. There is no
  customer endpoint that issues an invoice; a customer can only read their own.
- **Idempotent by order id.** Issuance first looks for an existing invoice for the order and returns it
  unchanged, so a redelivered `payment.captured` — a broker retry or a consumer restart — bills nothing
  a second time. The database backs this with a unique index on `order_id`, so a race cannot produce two
  documents either.
- **Only a paid order is invoiced.** An unpaid order has nothing to bill, so asking for its invoice is a
  refusal (`409`) on the admin path and a `404` on the customer path.
- **The document is a frozen snapshot.** Customer name, contact, billing address and every priced line
  are copied out of the order at issuance and never re-read, so a renamed, repriced or withdrawn product
  cannot change a past invoice. `Invoice` is a record with no update methods — a correction is a credit
  note, not a rewrite.
- **The invoice number comes from a database sequence** (`INV-<yyyyDDD>-<n>`), never a count, so two
  concurrent issuances cannot be handed the same number and a deletion cannot free a number for reuse.
- **Ownership is part of the query.** `findByIdAndCustomerId` has no unscoped twin; a cross-customer read
  by id or by order is reported as `404`, so the response does not confirm another customer's invoice
  exists.
- **The seller surface is separately gated.** `/api/v1/admin/invoices` needs an administrator role;
  issuing by hand additionally needs `order:write`. The acting administrator's id is read from the token
  for the audit trail, never from the request.
- **The fact is published, not assumed.** Issuance raises `order.invoice-issued` (through the
  transactional outbox, in the same transaction as the row), which the notification service consumes to
  email the customer their invoice number and total.

Endpoints: `GET /api/v1/invoices/me`, `GET /api/v1/invoices/me/{id}`, `GET /api/v1/invoices/me/by-order/{orderId}`
(customer, owner-scoped); `GET /api/v1/admin/invoices`, `GET /api/v1/admin/invoices/{id}`,
`POST /api/v1/admin/invoices/order/{orderId}` (seller). The gateway routes both prefixes to the order
service; the administrative prefix is never public.

## Reporting service (Phase 11 — Reporting and Customer Exports)

The reporting service is a **pure consumer**: it owns no aggregate and accepts no command, and every
table it holds is a derived read model it can rebuild from the event log. It exists so the seller's
dashboard and reports do not fan out to seven services on every page load, and so the admin dashboard
has one place to read cross-service facts from.

- **One read model per producer fact, upserted by the producer's own identifier.** `order_projection`
  is keyed on the order id, `payment_projection` on the payment id, `production_projection` on the job
  id, and so on. Every handler is therefore idempotent by construction: a redelivered event overwrites
  the row with the same values instead of adding a second, so at-least-once delivery needs no separate
  dedupe table — the read model has no append-only rows to duplicate. `ReportingJourneyIT` proves a
  redelivered `order.placed` leaves exactly one row.
- **Producer statuses are copied verbatim.** The fulfilment service's production and delivery events
  carry its own status names (`PREPARATION_STARTED`, `ATTEMPT_FAILED`, …). The projection stores the
  status the event carries rather than translating it into a reporting-local vocabulary, because a
  translation table is a second place for the two services to disagree. A real defect was fixed here:
  the projection had invented `STARTED`/`READY_FOR_DISPATCH` constants the producer never sends, so the
  status column would have been written as `null` for every real event while the unit test — which
  used the invented names — stayed green. The constants now mirror the producer, and
  `ProductionProjection`/`DeliveryProjection` assert it.
- **The consumer maps event types to use cases in the adapter, not the application.** `PlatformEventsListener`
  is the only class that knows the wire names; `ProjectionService` takes a plain map, so every
  projection rule is a unit test with no broker and no database.
- **A refund is one row, keyed on the order.** The request and its processed counterpart share the key
  `refund-<orderId>`, so a refund is not counted once as pending and again as paid — the seller's report
  would otherwise double every refund. `ProjectionService.refundProcessed` documents why the key is the
  order and not the provider's refund id.
- **Money is integer paise.** Revenue is a sum of `long` minor units, never a `double`; only a
  **captured** payment counts toward revenue, and an unpaid order counts toward nothing.
- **A report has a ceiling and says so.** `ReportLimits.MAX_REPORT_ROWS` (50 000) refuses a window that
  fills the cap rather than truncating and presenting a partial total as if it were the whole: a
  truncated total is a wrong answer that looks right, and the honest outcome is to make the seller
  narrow the period.
- **An export declares its columns.** Each `ExportType` names the columns it may emit, and the dataset
  is built from that list alone, so a query that happened to return more cannot leak more. The customer
  export masks the email and mobile (`a***a@example.test`, `******3210`) and is gated by
  `customer:export` on top of `report:read`; the operational exports need only `report:read`.
- **An export is attributable.** Every download writes an audit row naming the acting administrator,
  the dataset, the format and the row count — never the exported content, which would make the audit
  table the leak the export was meant to control. `AdminExportApiIT` asserts the row is written.
- **The download cannot be cached.** The response carries a `Content-Disposition: attachment` header
  and `Cache-Control: no-store`, so a browser does not keep a copy of customer data in its cache.
- **CSV is neutralised against formula injection.** A cell beginning with `=`, `+`, `-`, `@`, tab or
  carriage return is prefixed with `'`, because a spreadsheet opens such a cell as a formula — an export
  is a file the seller will open in Excel, and a customer whose name is `=cmd|...` must not become code.
  `CsvXlsxExportWriterTest` pins it.
- **The spreadsheet library stays in the export adapter.** `CleanArchitectureTest` fails the build if
  `org.apache.poi` appears anywhere outside `..reporting.adapter.out.export..`, so the domain and the
  use cases never depend on the writer.
- **The surface is not public.** `/api/v1/reports/**`, `/api/v1/exports/**` and `/api/v1/dashboard/**`
  are routed by the gateway and never in its public-path list; the reporting service's own method
  security requires `report:read` on every endpoint, so a customer token that the edge merely
  authenticates is refused by the service. `GatewayPropertiesTest` and `RoutingIT` both pin it.

Endpoints: `GET /api/v1/dashboard`, `GET /api/v1/reports/{type}` (`SALES`, `ORDERS`, `PAYMENTS`,
`PRODUCTION`, `DELIVERY`, `REFUNDS`, `CUSTOMERS`), `GET /api/v1/exports/{type}` (`CUSTOMERS`, `ORDERS`,
`PAYMENTS`, `INVOICES`) with `?format=CSV|XLSX` and a `?days=` or `?from=&to=` window. All require
`report:read`; the customer export additionally requires `customer:export`.

## Phase 11 — Testing and quality

| # | Requirement | Status |
|---|---|---|
| 11.1 | Unit tests (JUnit 5, Mockito where justified) | DONE — 967 unit tests green across every module (`mvn clean verify`); domain rules, use cases, state transitions, validation, authorization and retry/idempotency are exercised, with mocks only where a seam genuinely needs one |
| 11.2 | Integration tests (Testcontainers) | DONE — 302 integration tests green: real PostgreSQL, Redis, RabbitMQ and a GreenMail SMTP sink across identity, catalog, order, payment, fulfilment, notification, reporting and the gateway, driven over real HTTP with the real security filter chain |
| 11.3 | End-to-end workflows | DONE — `scripts/e2e-journey.py` drives the whole customer→seller journey against a running stack through the gateway (register → email/mobile OTP → sign-in → address → catalogue → serviceability → checkout → verified capture → kitchen job → preparation → delivery → notification fan-out → invoice → admin dashboard/export). It self-seeds the catalogue and a serviceable PIN code through the seller API when the stack is empty, so it runs against a fresh volume (which is what CI starts from) with no manual setup, and it is wired into CI by `.github/workflows/e2e.yml`, which boots the compose stack and runs it on every relevant push. It is a smoke harness, not a substitute for the per-service `*IT` suites. |
| 11.4 | Concurrent order processing test | DONE — `OrderPaymentIT` starts two simultaneous payments; `FulfilmentJourneyIT` proves idempotent consumption over a real broker |
| 11.5 | Payment verification test | DONE — `PaymentJourneyIT` (forged signature refused, signed capture paid, duplicate webhook a no-op) |
| 11.6 | Data privacy / export test | DONE — `AdminExportApiIT` proves the customer export masks the email and mobile (the raw values never appear in the file), the export needs `customer:export` beyond `report:read`, a customer token is refused, the download is `no-store` and the act is audited; `CsvXlsxExportWriterTest` proves a formula-injection cell is neutralised |
| 11.7 | Security tests | DONE — `docs/security-review.md` records the architecture, findings by severity, fixes and evidence; authz boundaries, ownership isolation, rate limiting, gateway access-token revocation, CORS and payment/webhook signature verification are each covered by a named test in the owning service's suite |

## Phase 12 — Customer storefront

The customer website is a React + Vite + TypeScript single-page app under
`frontend/storefront`. It talks only to the API gateway; it never reaches a service
database or a service port directly. It is a presentation client — every rule it appears to
enforce (verification before checkout, serviceable PIN, order cancellability) is enforced again on
the server, and the browser never decides that a payment succeeded.

| # | Requirement | Status | Evidence |
|---|---|---|---|
| 12.1 | Home page | DONE | `HomePage` — seller name and policies from `GET /api/v1/settings`, featured products from `GET /api/v1/products` |
| 12.2 | Product listing | DONE | `ProductListPage` — paged public catalogue with a search box that forwards the term to the backend (which escapes it), not a client-side filter |
| 12.3 | Product details + variants | DONE | `ProductDetailPage` — attributes, per-variant price/limits; a disabled variant cannot be chosen; the min/max order quantity is taken from the variant the server returned |
| 12.4 | Registration and login | DONE | `RegisterPage`, `LoginPage`; the register form warns before submit on a malformed mobile number and renders the server's field errors when it is refused |
| 12.5 | Email + mobile verification | DONE | `VerifyPage` — the channel is a query parameter the backend validates; resend uses the same channel |
| 12.6 | Customer profile | DONE | `ProfilePage` — name, contact change (with the re-verification warning), password change; contact and password forms are separate so a password is never sent with a profile edit |
| 12.7 | Address management | DONE | `AddressesPage` + `AddressForm` — create/edit/delete/default, PIN and mobile validated client-side as a courtesy and again on the server |
| 12.8 | Cart | DONE | `CartPage` + `CartContext` — quantity updates respect the variant limits the server returns; checkout is blocked in the UI until both channels are verified, and the server refuses regardless |
| 12.9 | Checkout | DONE | `CheckoutPage` — delivery charge quoted from `GET /api/v1/checkout/quote` before placing; an unserviceable PIN shows the seller's reason and hides the pay button; the order is placed with an `Idempotency-Key` so a double-tap cannot create two orders |
| 12.10 | Razorpay payment | DONE | `OrderDetailPage` — the publishable key and the amount come from the server's payment initiation; the checkout handler's response is POSTed to the verify endpoint, which checks the signature and re-fetches the payment. A `null` public key (no provider configured) is surfaced as "not configured", never as success |
| 12.11 | Order confirmation | DONE | `OrdersPage` / `OrderDetailPage` — the order number, frozen lines and totals as the server recorded them |
| 12.12 | Order history | DONE | `OrdersPage` — owner-scoped `GET /api/v1/orders/me` |
| 12.13 | Order tracking | DONE | `OrderDetailPage` — payment, preparation and delivery are shown from their own endpoints, each degrading gracefully when a stage does not exist yet |
| 12.14 | Invoice downloads | DONE | `InvoicesPage` / `InvoiceDetailPage` — owner-scoped invoice reads; the invoice is a frozen snapshot |
| 12.15 | Responsive layout | DONE | `styles.css` — a mobile-first grid; the header collapses to a single row, tables scroll, and forms are single-column below the breakpoint |

### What the storefront tests pin down

`npm test` runs Vitest with jsdom and Testing Library: **17 files, 105 tests, all green**, ~93% line
coverage. The tests exercise real code paths with a stubbed `fetch` (the network is the one seam that
is faked); nothing is mocked that the app itself owns.

| Area | Tests | What it proves |
|---|---|---|
| `client.test.ts` | 9 | The bearer token is attached to a protected request and not to an anonymous one; a 401 triggers exactly one refresh shared across concurrent callers (so the rotated refresh token is not spent twice); a refused refresh drops the session and notifies listeners; an RFC 9457 problem body becomes an `ApiError` with its field errors; a 204 and a body-less 202 both resolve without a parse error; an `Idempotency-Key` reaches the request |
| `tokenStore.test.ts` | 9 | A corrupt or partial stored session is treated as signed out rather than throwing; a nearly-expired token is reported as expiring |
| `format.test.ts` | 11 | Paise render as Indian-grouped rupees; a negative sign stays outside the symbol; the server-formatted amount wins over a locally computed one; dates degrade to an em dash; the PIN and mobile validators |
| `ProductListPage` / `ProductDetailPage` | 9 | The search term is sent to the backend; an empty result shows a state; a signed-out visitor is asked to sign in instead of being offered add-to-cart; an unverified customer is blocked; the chosen variant and quantity are what reach the cart; a disabled variant cannot be selected |
| `CartPage` | 7 | A signed-out visitor is prompted; an unverified customer cannot reach checkout; a quantity change sends the new quantity; remove sends a DELETE |
| `CheckoutPage` | 4 | The quote total and delivery charge render; an unserviceable PIN hides the pay button; placing an order carries an idempotency key and the chosen address id |
| `OrderDetailPage` + `PaymentFlow` | 14 | Payment is prompted only while unpaid; checkout opens with the server's key and amount; the provider response is POSTed for server verification; an unconfigured provider is reported honestly; a dismissed window is reported; a refund request carries its reason; a failed initiation shows an error and never a "paid" state |
| `AuthFlow` | 9 | Register sends the four fields and renders server validation; verify sends the channel and code; resend uses the channel; login stores the tokens; a lockout message is shown verbatim; password reset does not confirm whether an email exists and completes with the link token |
| `ProfilePage` / `AddressesPage` | 10 | Verified badges; the re-verification warning after a contact change; a password change sends both passwords; addresses list, default and create |
| `Layout` / `ProtectedRoute` / `App` | 15 | The header differs signed in and out; sign-out calls the endpoint and returns home; a protected path redirects a signed-out visitor to sign in; the router resolves each public route and the not-found page |
| `HomePage` / `PoliciesPage` / `OrdersPage` / `InvoicesPage` / `ServiceabilityPage` / `razorpay` | 17 | Settings render; an empty state appears with no data; a serviceability quote explains an uncovered PIN; the checkout script is injected once and shared, and a load failure is a rejection, not a silent success |

### Storefront deployment

`frontend/storefront/Dockerfile` is a two-stage build (Node build, then nginx). `nginx.conf` serves
the bundle, proxies `/api` and `/.well-known` to the `gateway` service so the browser sees one origin,
adds an SPA fallback, and sets `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy` and a
`Content-Security-Policy` that names the Razorpay checkout host. TLS terminates at the reverse proxy
in front of it; the app does not claim to serve HTTPS itself.

## Phase 13 — Admin dashboard

The seller's dashboard is a React + Vite + TypeScript single-page app under `frontend/admin`. Like the
storefront it talks only to the API gateway, and the permissions it reads from the access token are
used to *hide* controls the server would refuse anyway — every administrative action is authorised
again in the owning service.

| # | Requirement | Status | Evidence |
|---|---|---|---|
| 13.1 | Dashboard cards (sales, orders, pending, etc.) | DONE | `DashboardPage` — the reporting service's `GET /api/v1/dashboard` metric cards |
| 13.2 | Customer management + search | DONE | `CustomersPage` / `CustomerDetailPage` against the new `GET /api/v1/admin/customers` (paged, `?search=` over name/email/mobile, newest first) and `GET /api/v1/admin/customers/{id}`. Both require `customer:read`; the list returns a summary with no credential field, and the single read reuses the same profile shape the customer sees. The search term is escaped into a `LIKE` pattern in the adapter, so a term containing `%` or `_` matches literally. |
| 13.3 | **Customer CSV export** | DONE | `ExportsPage` → `GET /api/v1/exports/customers?format=CSV` |
| 13.4 | **Customer XLSX export** | DONE | `ExportsPage` → `GET /api/v1/exports/customers?format=XLSX` |
| 13.5 | Export authorization + audit trail | DONE | The customer export needs `customer:export` beyond `report:read`; `AdminExportApiIT` proves a customer token is refused, the download is `no-store`, the email and mobile are masked, and an export writes an audit row naming the actor |
| 13.6 | Product management | DONE | `ProductsPage` / `ProductDetailPage` — variants, prices, images, publish state |
| 13.7 | Production management | DONE | `ProductionPage` / `ProductionJobPage` — the kitchen's job queue and the status machine; `InventoryPage` / `IngredientDetailPage` for stock |
| 13.8 | Order management | DONE | `OrdersPage` / `OrderDetailPage` |
| 13.9 | Payment management | DONE | `PaymentsPage` / `PaymentDetailPage` — counters, records, refund |
| 13.10 | Delivery management | DONE | `DeliveriesPage` / `DeliveryDetailPage`, `ServiceableAreasPage` |
| 13.11 | Invoice generation/download | DONE | The order service issues an invoice from a verified `payment.captured` (idempotent by order id, one invoice per order enforced by a unique index) and serves it at `/api/v1/invoices/me` (owner-scoped) and `/api/v1/admin/invoices` (seller). The document is a frozen snapshot of the order; `order.invoice-issued` is published for the notification service. `InvoicesPage` / `InvoiceDetailPage` render it. |
| 13.12 | Reports (sales, revenue, customer, order, production, delivery, failed payment, refund) | DONE | `ReportsPage` → `GET /api/v1/reports/{type}` |
| 13.13 | Website settings | DONE | `SettingsPage` → `GET`/`PUT /api/v1/settings` |
| 13.14 | Notification management | DONE | `NotificationsPage` / `NotificationDetailPage` — the delivery log and the administrator resend |

### What the admin dashboard tests pin down

`npm test` in `frontend/admin` runs Vitest with jsdom and Testing Library: **10 files, 34 tests, all
green**, with `tsc` type-checking clean and a production `vite build` succeeding. The network is the
one seam that is faked (a stubbed `fetch`); nothing the app itself owns is mocked.

| Area | Tests | What it proves |
|---|---|---|
| `CustomersPage` | 4 | The list renders name, email, mobile and the verification state; an empty result shows a state; a partly verified account is reported as such rather than as verified; no credential material or password control is ever rendered |
| `OrdersPage` | 3 | The order number, total and status render; an empty state appears; the status filter is sent to the server |
| `DashboardPage`, `ExportsPage`, `PaymentDetailPage`, `NotificationDetailPage`, `LoginPage` | 20 | Metric cards render; an export is requested with the chosen format; a refund control is shown only with `payment:refund`; a delivered notification offers no resend while a failed one does; a sign-in failure renders the problem detail |
| `format` | 7 | Paise render as Indian-grouped rupees; a date degrades to an em dash; a status is humanised |

### New backend tests for the customer administration surface

| Class | Tests | What it proves |
|---|---|---|
| `CustomerQueryServiceTest` (identity-application, unit) | 9 | The list pages newest-first and bounds the page; the summary carries only the recognition fields; the search matches a partial name case-insensitively and by email or mobile; a term matching nothing returns an empty page; an unknown id is a `404` |
| `AdminCustomerApiIT` (identity-app, integration) | 6 | Over real HTTP against real PostgreSQL, Redis, RabbitMQ and the real filter chain: the seller lists and searches customers; a term containing a `%` is matched literally rather than as a wildcard; one customer's profile including addresses is read; an unknown id is a `404`; a customer token is refused (`403`) at both endpoints; an unauthenticated read is `401`; and no response body contains `passwordHash` |
| `RoutingIT` (gateway, integration) | +2 | An administrator's `/api/v1/admin/customers` request reaches the identity service with its token intact, and the same path with no token is refused at the edge (401) without reaching the service |

## Phase 14 — Deployment preparation

| # | Requirement | Status |
|---|---|---|
| 14.1 | Docker Compose (full app) | DONE — `docker-compose.app.yml` builds and starts all ten application containers alongside the infrastructure; the whole stack reports `(healthy)`. `docker-compose.prod.yml` overlays the production shape (required secrets, read-only root filesystems, no published infrastructure ports). |
| 14.2 | Service Dockerfiles | DONE — one parameterised `backend/Dockerfile` (build arg `SERVICE_MODULE`/`SERVICE_JAR`) builds the gateway and all seven services; `frontend/{storefront,admin}/Dockerfile` build the two React bundles behind nginx. Every image builds in CI and locally. |
| 14.3 | GitHub Actions CI/CD | DONE — `.github/workflows/{backend,frontend,images,security,e2e}.yml`: `mvn verify`, frontend typecheck/test/build, per-service image builds, gitleaks + OWASP dependency-check + Trivy, and an end-to-end job that boots the compose stack and runs `scripts/e2e-journey.py` against it. No workflow pushes to a registry yet (none is configured). |
| 14.4 | Kubernetes-ready manifests | TODO — the images are stateless and twelve-factor (config from the environment, health probes on `/actuator/health/{readiness,liveness}`), so they are manifest-ready, but no Kubernetes manifests are authored. Deliberately deferred: no cluster is in scope, and inventing manifests that nothing runs would be ceremony. |
| 14.5 | Monitoring dashboards | DONE (infra) — Prometheus scrapes `/actuator/prometheus`; Grafana with provisioned datasources and dashboards runs under the `observability` profile. |
| 14.6 | Centralized logging | DONE (infra) — structured JSON on stdout; Loki + Promtail under the `observability` profile. |
| 14.7 | Distributed tracing | DONE (infra) — Micrometer Tracing + OTLP exporter to Tempo, gated by `CHOCO_OTLP_ENABLED` so a service does not log a failed export every few seconds when no collector runs. |
| 14.8 | Deployment documentation | DONE — `docs/deployment.md`: components, local and production shapes, the signing key, HTTPS placement, durability and volumes, backup/restore, operations and troubleshooting. |
| 14.9 | Production secret hardening (F-2) | DONE — `ProductionConfigurationGuard` (a `prod`-profile listener in `common-security`) refuses to start when a configured capability's password is missing, blank or still a development default; the prod compose overlay additionally requires every credential with `${VAR:?}` and now activates the profile. Scoped per capability so the gateway (no datasource) is not blocked by a database check. `ProductionDefaultsDriftTest` reads every service `application.yml` and fails if a new `${...:choco_..._pw}` default is not guarded. |
| 14.10 | Proxy-aware HSTS (F-3) | DONE — `TrustedProxyHttpsMatcher` emits HSTS when the request is directly secure or when a configured trusted proxy (`choco.http.trusted-proxies`) reports `X-Forwarded-Proto: https`; an untrusted peer can neither force HSTS on nor suppress it. Deliberately does **not** use `server.forward-headers-strategy=framework`, which would rewrite the client address from `X-Forwarded-For` and defeat the rate-limiter's trust rule. |

**Verified locally this milestone:** all ten images built; `docker compose -f docker-compose.yml -f
docker-compose.app.yml up -d` brought fourteen containers to `(healthy)`; a registration → email OTP
verification ran end to end through the gateway; the admin login → protected read → logout →
refused-token sequence was exercised against the running stack. `docker-compose.prod.yml` resolves
with every required secret set and fails closed (naming the variable) when one is missing.

**Fixed while validating this milestone:** `.env.example` listed variable names
(`ADMIN_BOOTSTRAP_EMAIL`, `CORS_ALLOWED_ORIGINS`, `LOG_OTP`, `SMTP_HOST`, `SMTP_USERNAME`,
`SMS_API_KEY`, …) that no compose file reads, so an operator copying the template would have had
their settings silently ignored; it now lists exactly the names the compose files interpolate. The
production override read `SMS_API_KEY` where the app override reads `CHOCO_SMS_API_KEY`; unified.

**Security hardening closed while finishing this phase (F-2, F-3):** development credential defaults
were reachable in a production-shaped deployment if it did not go through the compose overlay, and
HSTS was never emitted behind a TLS-terminating proxy because the request is seen as plain HTTP.
Both are fixed and covered — `ProductionConfigurationGuardTest` (10), `ProductionDefaultsDriftTest`
(1), `TrustedProxyHttpsMatcherTest` (8) and `GatewayHstsIT` (2, over real HTTP). See
`docs/security-review.md` for the findings and evidence.

---

## Mandatory quality requirements

| Requirement | Status | Evidence |
|---|---|---|
| Never store passwords in plaintext | DONE | Argon2id/BCrypt tests assert hash ≠ plaintext |
| Argon2id or BCrypt cost 15 | DONE | Tests assert `$argon2id$` and `$2a$15$` |
| Never expose password hashes | DONE | `AuthenticatedUser` carries no credential field |
| No auth secrets in exports | DONE | `AdminExportApiIT` proves the export masks email/mobile, needs `customer:export`, refuses a customer token and audits the download; `CsvXlsxExportWriterTest` neutralises formula injection. No hash, token or OTP column exists in any export view. |
| Verify Razorpay on backend | DONE | `verifyCheckoutSignature` + provider re-fetch before a payment is marked paid; forged signature → `PAYMENT_SIGNATURE_INVALID` asserted in `PaymentJourneyIT` |
| Never trust frontend payment response | DONE | A checkout redirect only triggers a server-side verify; the payment is marked paid only from the provider's captured state or a signed webhook |
| Prevent duplicate payment processing | DONE | Unique `order_id`/`provider_payment_id` indexes; repeated webhook and repeated verify are no-ops (`PaymentJourneyIT`); `Idempotency-Key` with fingerprint conflict |
| Webhook signature verified over the raw body | DONE | `RazorpayPaymentGatewayTest` computes real HMACs; an unsigned or tampered webhook is refused |
| No card/CVV/UPI-PIN ever stored | DONE | Only identifiers and a coarse method classification exist in the schema; asserted by the migration's column set |
| Prevent incorrect order/inventory status | DONE | Explicit transition tables — `OrderStatus`, `ProductionStatus`, `DeliveryStatus` — refuse an illegal edge; a status field is never set directly, and the domain and IT suites exercise the whole lifecycle plus the refused jumps |
| Customers access only own data | DONE | `requireOwnerOrAdmin` + tests |
| Admin access protected | DONE | RBAC + permission tests; separate admin aggregate, no public admin sign-up, customer credentials rejected at the admin endpoint (verified over HTTP) |
| Never hardcode credentials | DONE | `.env.example` only, no values committed |
| Never fabricate payment/delivery data | DONE | Delivery is manual by design |
| Error handling and audit logs | DONE | `GlobalExceptionHandler` (RFC 9457) with `AccessDeniedExceptionHandler` ahead of the catch-all so a refusal is a 403, not a 500; an audit adapter and `audit_log` migration exist in identity, payment, fulfilment, notification and reporting, and a sensitive act (an export, a payment transition, a delivery step) writes a row naming the actor |

---

## Test tally

| Module | Unit | Integration | Total |
|---|---|---|---|
| common-core | 40 | – | 40 |
| common-web | 7 | – | 7 |
| common-security | 48 | – | 48 |
| common-events | 6 | – | 6 |
| common-redis | – | 9 | 9 |
| identity-domain | 56 | – | 56 |
| identity-application | 150 | – | 150 |
| identity-adapters-out | 6 | – | 6 |
| identity-adapters-in | 6 | – | 6 |
| identity-app | 6 | 42 | 48 |
| catalog-domain | 19 | – | 19 |
| catalog-application | 27 | – | 27 |
| catalog-app | 4 | 26 | 30 |
| order-domain | 117 | – | 117 |
| order-application | 78 | – | 78 |
| order-adapters-in | 9 | – | 9 |
| order-adapters-out | 14 | – | 14 |
| order-app | 4 | 75 | 79 |
| payment-domain | 40 | – | 40 |
| payment-application | 31 | – | 31 |
| payment-adapters-out | 18 | – | 18 |
| payment-app | 5 | 25 | 30 |
| gateway | 16 | 51 | 67 |
| fulfilment-domain | 90 | – | 90 |
| fulfilment-application | 37 | – | 37 |
| fulfilment-app | 4 | 27 | 31 |
| notification-domain | 13 | – | 13 |
| notification-application | 35 | – | 35 |
| notification-app | 4 | 23 | 27 |
| reporting-domain | 20 | – | 20 |
| reporting-application | 42 | – | 42 |
| reporting-adapters-out | 10 | – | 10 |
| reporting-app | 5 | 24 | 29 |
| **Total** | **967** | **302** | **1269** |

Counts come from `mvn clean verify` at `backend/`; every suite reported 0 failures, 0 errors, 0
skipped. Treat this as a map of where the tests are, not a number to keep in sync by hand. Tally from
each module's `Results:` block: a nested `@Nested` class writes its own report file, so summing report
files undercounts and a naive per-file sum is not the total.

### API Gateway integration tests (`mvn verify`)

| Class | Tests | What it proves |
|---|---|---|
| RoutingIT | 29 | A public request reaches its service unauthenticated; a protected request with a valid token arrives with the token intact; a missing, forged, expired or wrong-issuer token is refused at the edge and never forwarded; an unknown path is not routed; the correlation id is preserved; an administrator refreshes and logs out at the edge with no access token; a public serviceability lookup and a customer's own fulfilment progress reach the fulfilment service while the administrative kitchen surface is refused without a token; the administrative payment, order, invoice and notification surfaces reach their services with a token and are refused without one; the report, export and dashboard surfaces reach the reporting service with the token intact and are refused at the edge without one |
| RateLimitIT | 4 | A burst is throttled once the allowance is spent; auth paths use the tighter allowance; a throttled request never reaches the service; the auth and general buckets are counted independently |
| CircuitBreakerIT | 2 | Repeated 5xx from a service opens the breaker and the gateway answers 503 with an RFC 9457 body instead of propagating the error |
| GatewaySecurityIT | 6 | Registration and JWKS are public; the customer profile path is not; a 401 carries a problem-detail body; an allowed-origin preflight is answered with that origin and an unknown origin is never wildcarded |
| GatewayHstsIT | 2 | A request over plain HTTP gets no `Strict-Transport-Security`; a trusted proxy's `X-Forwarded-Proto: https` does; a forged one from an untrusted peer does not |
| GatewayRevocationIT | 6 | The edge consults the Redis revocation store before forwarding: a live token is proxied, a revoked session is a 401 that never reaches the downstream, a logged-out administrator cannot reach `/api/v1/admin/notifications`, revocation is scoped to one session, and a subject cutoff refuses only tokens issued before it |

### Identity integration tests (`mvn verify`)

| Class | Tests | What it proves |
|---|---|---|
| SchemaAndPersistenceIT | 7 | Flyway applied V1–V3; Hibernate `validate` matches the schema; DB-level unique, check and FK constraints reject bad rows |
| CustomerJourneyIT | 15 | Register → verify email and mobile → sign in → profile/contact/addresses, refresh rotation and replay detection, logout (which now retires the **access** token, not only the refresh token), login rate limiting, ownership isolation |
| AdminJourneyIT | 11 | Bootstrap administrator, admin token authority, no public admin sign-up, customer credentials and refresh tokens refused at admin endpoints, independent admin rate limiting, and admin logout retiring the **access** token |
| OutboxRelayIT | 3 | A real registration publishes `customer.registered` to a bound RabbitMQ queue and marks the outbox row published |

### Catalog integration tests (`mvn verify`)

| Class | Tests | What it proves |
|---|---|---|
| SchemaAndPersistenceIT | 8 | Flyway applied V1; Hibernate `validate` matches the schema; the slug unique constraint spans every status; a zero price and an orphan variant are rejected by the database; one primary image per product; a product delete cascades; creating a product over HTTP writes an outbox row |
| CatalogueJourneyIT | 18 | Full admin lifecycle over real HTTP (create → price → publish → read); publish refused without a variant; admin listing includes drafts; image upload then fetch with `nosniff`; public settings read and permission-gated write; a published product is public by slug while a draft is 404; unpublish hides without deleting; anonymous/customer/under-permission tokens refused with 401/403; the internal variant lookup resolves a published variant with its price and availability, hides a draft product's variant, omits an unknown id rather than erroring, and refuses an anonymous caller |

### Order integration tests (`mvn verify`)

| Class | Tests | What it proves |
|---|---|---|
| SchemaAndPersistenceIT | 12 | Flyway applied V1 against a real PostgreSQL; Hibernate `validate` matches the migration; the order-number sequence issues distinct values; the stored total must equal items plus delivery charge; a negative amount and a foreign currency are rejected; the order number is unique; an orphan order line is rejected; a product delete cascades to lines, history and notes; one cart per customer; an idempotency key is unique per scope and reusable across scopes |
| OutboundClientTest | 11 | The catalogue, identity and delivery clients against a real HTTP server: the exact request each sends (batch variant query, customer profile path, PIN-code path), a missing variant is absent rather than an error, an unknown customer or foreign address id is empty, and a downstream 5xx becomes `DEPENDENCY_UNAVAILABLE` rather than a business error or a silent "not serviceable" |
| PaymentEventsIT | 6 | The order side of the payment saga over a real RabbitMQ broker: a `payment.captured` for the exact total confirms the order and releases it into preparation by emitting `order.confirmed`; a capture for the wrong amount is refused (`PAYMENT_AMOUNT_MISMATCH`) and the order stays `PAYMENT_PENDING` with no `order.confirmed`; a redelivered capture is idempotent (one confirmation, not two); a `payment.failed` records the failure without un-paying; a refund event marks the order refunded; an event for an unknown order is retried and dead-lettered rather than silently acked |
| PaymentEventsListenerTest | 8 | The listener's dispatch on its own, with no broker: each of the four routing keys calls the right command method, a capture passes the provider ids and the amount through, a malformed body is rejected, a missing or non-UUID order id is refused, and an unknown routing key is ignored — the boundary between "this is our event" and "this is not" is pinned without a container |
| OrderPaymentIT | 11 | Starting a payment over real HTTP and a real PostgreSQL: the order's own frozen total is what is sent, never a caller-supplied figure; the response carries the provider order id and publishable key and nothing that reads as "paid"; an unauthenticated or not-fully-verified customer is refused (401/403); one customer cannot pay for another's order (403); an unknown order is a 404; a paid order cannot be charged again (`PAYMENT_ALREADY_CAPTURED`); starting a payment leaves the order `PAYMENT_PENDING`; a payment-service 5xx or a response with no provider order id becomes a 503 and the order stays payable; and two simultaneous starts both succeed without corrupting the order — the payment service owns deduplication |
| CleanArchitectureTest | 4 | The order domain depends on no framework and no adapter; the application layer depends on no adapter, persistence framework or web framework; inbound adapters do not reach into persistence; nothing depends on the wiring configuration |

### Invoice integration tests (`mvn verify`)

| Class | Tests | What it proves |
|---|---|---|
| InvoiceJourneyIT | 10 | Invoice issuance and access over a real broker, a real database and real HTTP: a verified `payment.captured` issues exactly one invoice and emits `order.invoice-issued` for the notification service; a redelivered capture issues none (the unique index on `order_id` holds); a customer reads their own invoice but a cross-customer read by invoice id or by order id is a `404`, never the document; an unauthenticated read is `401`; the customer's list is scoped to their own; the seller sees every invoice and can read one; a customer is refused the administrative surface (`403`); the seller can issue an invoice by hand for a confirmed order and the second call returns the same document; an unpaid order has no invoice and asking for one is a `404` |

### Payment integration tests (`mvn verify`)

| Class | Tests | What it proves |
|---|---|---|
| SchemaAndPersistenceIT | 7 | Flyway applied V1 against a real PostgreSQL; Hibernate `validate` matches the migration; one payment per order and one payment per provider order are enforced by unique indexes; a non-positive amount and a non-INR currency are rejected by `CHECK` constraints; a refund must reference a real payment |
| PaymentJourneyIT | 18 | The full payment journey over real HTTP: unauthenticated initiate refused; a customer starts a payment and receives the publishable key; initiation is idempotent per order and a replayed key for a different order is a 409; a captured payment with a valid signature is paid while a forged one is refused and leaves the payment unpaid; a customer cannot verify another's payment; `authorized` is pending, not paid; a signed capture webhook marks the payment paid, an unsigned one is refused, a duplicate delivery is a no-op with a single attempt row, and a webhook for an unknown payment is acknowledged; payment history and reads are scoped to the owner; a customer cannot reach the seller surface; an administrator sees the counters and refunds a captured payment |
| CleanArchitectureTest | 5 | The domain depends on no framework and no provider SDK; the application layer depends on no adapter, persistence framework or provider SDK; only the gateway adapter may reference the Razorpay SDK; inbound adapters do not reach into persistence; nothing depends on the wiring configuration |

### Adapter unit tests (`mvn test`)

| Class | Tests | What it proves |
|---|---|---|
| RazorpayPaymentGatewayTest | 11 | The checkout signature is accepted only when it is the real HMAC over `order_id\|payment_id` with the account secret — computed independently with `javax.crypto`, so a wrong-field or wrong-secret bug cannot pass; a blank signature is refused; the webhook signature is checked over the raw body, a tampered body is refused, and every webhook is refused when no secret is configured; the webhook body parses a captured payment, a failure with its reason, and a refund that carries only a payment id; the publishable key is the key id and never the secret |
| UnconfiguredPaymentGatewayTest | 7 | With no credentials the service boots but refuses every money-moving call with the missing-setting message, never verifies a signature, and exposes no publishable key — so a misconfigured deployment cannot report a fake success |

### Fulfilment integration tests (`mvn verify`)

| Class | Tests | What it proves |
|---|---|---|
| SchemaAndPersistenceIT | 11 | Flyway applied V1 against a real PostgreSQL; Hibernate `validate` matches the migration; every database constraint holds — a non-positive quantity, a negative stock level, a negative delivery charge and an out-of-range status are all rejected, and one delivery per order is enforced by a unique index |
| FulfilmentJourneyIT | 16 | The made-to-order hand-over over a real RabbitMQ broker and real HTTP: a confirmed order opens one kitchen job per line and a redelivered event opens none (idempotent consumption); the seller walks a job received → awaiting → preparing → completed → packaging → ready for dispatch, which opens exactly one delivery at the frozen address; an illegal jump is refused; a cancelled order abandons its jobs and returns any consumed ingredients; a serviceability quote returns the configured charge for a covered PIN and a reasoned "not serviceable" for an uncovered one, **and answers an anonymous visitor** (the gateway lists the path as public and the storefront's "Delivery areas" page is unauthenticated) while an anonymous visitor still cannot reach the administrative kitchen; the customer sees only their own progress; the admin surface is closed to customers; the delivery-assigned and delivery-rescheduled events actually reach the broker (a throwaway queue is bound and the envelope read, so a use case that saves but never drains its events fails the test) |
| CleanArchitectureTest | 4 | The domain depends on no framework; the application layer depends on no adapter or persistence framework; inbound adapters do not reach into persistence; nothing depends on the wiring configuration |

### Reporting integration tests (`mvn verify`)

| Class | Tests | What it proves |
|---|---|---|
| SchemaAndPersistenceIT | 5 | Flyway applied V1 against a real PostgreSQL; Hibernate `validate` matches the migration; every projection table exists; a projection is keyed on the owning service's id, so a second row with the same id is refused; a required column cannot be null; and no column anywhere could hold a password, hash, OTP, token, secret, CVV or PIN |
| ReportingJourneyIT | 11 | The projection over a real RabbitMQ broker and real PostgreSQL: an order is projected from `order.placed`, advanced by `order.confirmed` and never duplicated; a capture becomes a `PAID` payment keyed on the payment id; a production event stores the kitchen's own status name verbatim; a delivery is advanced in place by `delivery.failed`; a redelivered event leaves one row; a refund request and its processing are one row (not counted twice); the seller's report over HTTP reports the real figures; an event this service does not project is ignored; the window is echoed back; the dashboard is served to a report reader and refused to a customer; every report type is served |
| AdminExportApiIT | 8 | The export surface over real HTTP: an operational export is an attachment with `no-store`; XLSX is served as a spreadsheet; the customer export needs `customer:export`, not just `report:read`; a customer token is refused; an unauthenticated export is refused; the customer export masks the email and mobile so the raw values never appear in the file; an export writes an audit row naming the actor; an unknown format is refused rather than defaulting silently |
| CleanArchitectureTest | 5 | The domain depends on no framework; the application layer depends on no adapter, persistence or export framework; inbound adapters do not reach into persistence; the spreadsheet library never leaves the export adapter; nothing depends on the wiring configuration |

### Bugs the reporting integration tests caught

| Bug | Symptom | Fix |
|---|---|---|
| Producer status names invented in the projection | The fulfilment events carry `PREPARATION_STARTED` / `ATTEMPT_FAILED`, but the projection had defined its own `STARTED` / `READY_FOR_DISPATCH` constants. Every real event would have written a status the reports did not recognise, while the unit test — which used the invented names — stayed green | `ProductionProjection` / `DeliveryProjection` constants now mirror the producer verbatim; `ProjectionService` prefers the status carried by the event |
| A refund counted twice | Keying the processed refund on the provider's refund id made a request and its processed counterpart two rows, so the seller's refund report counted one refund once as pending and again as paid | Key both on `refund-<orderId>`, the order the request named |

### Bugs the catalog integration tests caught

| Bug | Symptom | Fix |
|---|---|---|
| Missing transaction on repository reads | `LazyInitializationException` on every admin read — the mapper touched lazy child collections after the session closed | `@Transactional(readOnly = true)` on the adapter's read methods |
| Broken optimistic locking | `StaleObjectStateException` on the second edit of any product — the mapper built a fresh entity at version 0, so the update matched no row | Carry the current version onto the mapped entity before saving |
| Ambiguous nullable search parameter | `function lower(bytea) does not exist` — PostgreSQL inferred the `:search is null` parameter as `bytea` | Build an escaped `LIKE` pattern in the adapter instead of a nullable parameter |
| `@PreAuthorize` denial reported as 500 | A permission refusal returned `500 INTERNAL_ERROR` instead of `403` | `AccessDeniedExceptionHandler` in `common-security`; the shared catch-all advice lowered to `LOWEST_PRECEDENCE` so the specific handler wins |
| Gateway did not route the admin catalogue | `/api/v1/admin/catalog/**` fell through to a 404 at the edge; the public settings path pattern did not match the real endpoint | Added the admin route and corrected the public-path list |
