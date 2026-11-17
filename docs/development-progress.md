# Development Progress Checklist

Tracks every requirement from the master prompt. Statuses: **DONE**, **IN PROGRESS**, **TODO**.
A phase is only marked DONE when its tests run green and the behaviour has been verified.

**Last verified:** `mvn clean verify` in `backend/` — **604 tests, 0 failures, 0 errors, 0 skipped**
(Phase 5c: order-service persistence, app bootstrap and downstream clients). Integration tests
(`*IT`) run under the `verify` phase via the failsafe plugin; unit tests (`*Test`) run under `test`.
The identity service has full-stack integration tests that start real PostgreSQL, Redis and
RabbitMQ containers plus a GreenMail SMTP sink, and drive the service over HTTP: Flyway/Hibernate
schema agreement and database constraints (`SchemaAndPersistenceIT`), the complete customer journey
including OTP verification and token rotation (`CustomerJourneyIT`), administrator authentication and
the customer/admin boundary (`AdminJourneyIT`), and outbox-to-broker delivery (`OutboxRelayIT`). The
catalog service has integration tests on the same containers: schema/constraint agreement
(`SchemaAndPersistenceIT`) and the full admin-and-storefront journey over real HTTP
(`CatalogueJourneyIT`). The Redis rate limiter is additionally exercised against a real
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

Latest milestone (Phase 5c, order-service persistence and app bootstrap) is pushed to `origin/main`
at commit `1014bd4`; the remote hash was verified equal to the local hash after the push. The push
succeeded on the first attempt this session, so no token refresh was needed.

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
| 3.5 | Login / logout | DONE | `AuthenticationServiceTest` (14 tests); refresh-token rotation with replay detection |
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
| 6.1 | Razorpay order creation | TODO |
| 6.2 | Payment initiation | TODO |
| 6.3 | Signature verification (backend only) | TODO |
| 6.4 | Webhook signature verification | TODO |
| 6.5 | Idempotent payment processing | TODO |
| 6.6 | Duplicate event protection | TODO |
| 6.7 | Payment success/failure/pending handling | TODO |
| 6.8 | Payment transaction history | TODO |
| 6.9 | Refunds and disputes | TODO |

## Phase 7 — Made-to-order production

| # | Requirement | Status |
|---|---|---|
| 7.1 | Preparation queue from confirmed orders | TODO |
| 7.2 | Production statuses (Received→Awaiting→Started→Completed→Packaging→Ready) | TODO |
| 7.3 | Production notes | TODO |
| 7.4 | Optional ingredient/raw-material inventory | TODO |
| 7.5 | Low-stock alerts | TODO |
| 7.6 | Concurrent order handling | TODO |

## Phase 8 — Seller-managed delivery

| # | Requirement | Status |
|---|---|---|
| 8.1 | Serviceable PIN-code management | TODO |
| 8.2 | Configurable delivery charges | TODO |
| 8.3 | Delivery assignment | TODO |
| 8.4 | Delivery workflow (10 states) | TODO |
| 8.5 | Delivery attempts + failure reasons | TODO |
| 8.6 | Customer delivery tracking | TODO |
| 8.7 | No fabricated GPS/courier data | TODO |

## Phase 9 — Admin dashboard

| # | Requirement | Status |
|---|---|---|
| 9.1 | Dashboard cards (sales, orders, pending, etc.) | TODO |
| 9.2 | Customer management + search | TODO |
| 9.3 | **Customer CSV export** | TODO |
| 9.4 | **Customer XLSX export** | TODO |
| 9.5 | Export authorization + audit trail | TODO |
| 9.6 | Product management | TODO |
| 9.7 | Production management | TODO |
| 9.8 | Order management | TODO |
| 9.9 | Payment management | TODO |
| 9.10 | Delivery management | TODO |
| 9.11 | Invoice generation/download | TODO |
| 9.12 | Reports (sales, revenue, customer, order, production, delivery, failed payment, refund) | TODO |
| 9.13 | Website settings | TODO |

## Phase 10 — Notifications

| # | Requirement | Status |
|---|---|---|
| 10.1 | Email provider (configurable) | TODO |
| 10.2 | SMS provider (configurable) | TODO |
| 10.3 | Registration/verification notifications | TODO |
| 10.4 | Order + payment notifications | TODO |
| 10.5 | Production + delivery notifications | TODO |
| 10.6 | Cancellation/refund notifications | TODO |

## Phase 11 — Testing and quality

| # | Requirement | Status |
|---|---|---|
| 11.1 | Unit tests (JUnit 5, Mockito where justified) | IN PROGRESS (282 green so far) |
| 11.2 | Integration tests (Testcontainers) | IN PROGRESS (identity service: real PostgreSQL, Redis, RabbitMQ, SMTP; 34 green) |
| 11.3 | End-to-end workflows | TODO |
| 11.4 | Concurrent order processing test | TODO |
| 11.5 | Payment verification test | TODO |
| 11.6 | Data privacy / export test | TODO |
| 11.7 | Security tests | TODO |

## Phase 12 — Deployment preparation

| # | Requirement | Status |
|---|---|---|
| 12.1 | Docker Compose (full app) | IN PROGRESS (infra done) |
| 12.2 | Service Dockerfiles | TODO |
| 12.3 | GitHub Actions CI/CD | TODO |
| 12.4 | Kubernetes-ready manifests | TODO |
| 12.5 | Monitoring dashboards | TODO |
| 12.6 | Centralized logging | TODO |
| 12.7 | Distributed tracing | TODO |
| 12.8 | Deployment documentation | TODO |

---

## Mandatory quality requirements

| Requirement | Status | Evidence |
|---|---|---|
| Never store passwords in plaintext | DONE | Argon2id/BCrypt tests assert hash ≠ plaintext |
| Argon2id or BCrypt cost 15 | DONE | Tests assert `$argon2id$` and `$2a$15$` |
| Never expose password hashes | DONE | `AuthenticatedUser` carries no credential field |
| No auth secrets in exports | TODO | Enforced in Phase 9 export |
| Verify Razorpay on backend | TODO | Phase 6 |
| Never trust frontend payment response | TODO | Phase 6 |
| Prevent duplicate payment processing | TODO | Phase 6 idempotency |
| Prevent incorrect order/inventory status | TODO | Phase 5/7 state machines |
| Customers access only own data | DONE | `requireOwnerOrAdmin` + tests |
| Admin access protected | DONE | RBAC + permission tests; separate admin aggregate, no public admin sign-up, customer credentials rejected at the admin endpoint (verified over HTTP) |
| Never hardcode credentials | DONE | `.env.example` only, no values committed |
| Never fabricate payment/delivery data | DONE | Delivery is manual by design |
| Error handling and audit logs | IN PROGRESS | Error handling done; audit log in Phase 3 |

---

## Test tally

| Module | Tests | Status |
|---|---|---|
| common-core | 40 | green |
| common-web | 7 | green |
| common-security | 23 | green |
| common-events | 6 | green |
| common-redis | 4 (integration) | green |
| identity-domain | 52 | green |
| identity-application | 132 | green |
| identity-adapters-out | 6 | green |
| identity-adapters-in | 6 | green |
| identity-app | 40 (6 unit + 34 integration) | green |
| catalog-domain | 19 | green |
| catalog-application | 27 | green |
| catalog-app | 30 (4 unit + 26 integration) | green |
| order-domain | 98 | green |
| order-application | 54 | green |
| order-adapters-out | 11 | green |
| order-app | 12 (integration) | green |
| api-gateway | 37 (14 unit + 23 integration) | green |
| **Total** | **604** | **green** |

### API Gateway integration tests (`mvn verify`)

| Class | Tests | What it proves |
|---|---|---|
| RoutingIT | 9 | A public request reaches its service unauthenticated; a protected request with a valid token arrives with the token intact; a missing, forged, expired or wrong-issuer token is refused at the edge and never forwarded; an unknown path is not routed; the correlation id is preserved |
| RateLimitIT | 4 | A burst is throttled once the allowance is spent; auth paths use the tighter allowance; a throttled request never reaches the service; the auth and general buckets are counted independently |
| CircuitBreakerIT | 2 | Repeated 5xx from a service opens the breaker and the gateway answers 503 with an RFC 9457 body instead of propagating the error |
| GatewaySecurityIT | 6 | Registration and JWKS are public; the customer profile path is not; a 401 carries a problem-detail body; an allowed-origin preflight is answered with that origin and an unknown origin is never wildcarded |

### Identity integration tests (`mvn verify`)

| Class | Tests | What it proves |
|---|---|---|
| SchemaAndPersistenceIT | 7 | Flyway applied V1–V3; Hibernate `validate` matches the schema; DB-level unique, check and FK constraints reject bad rows |
| CustomerJourneyIT | 14 | Register → verify email and mobile → sign in → profile/contact/addresses, refresh rotation and replay detection, logout, login rate limiting, ownership isolation |
| AdminJourneyIT | 10 | Bootstrap administrator, admin token authority, no public admin sign-up, customer credentials and refresh tokens refused at admin endpoints, independent admin rate limiting |
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

### Bugs the catalog integration tests caught

| Bug | Symptom | Fix |
|---|---|---|
| Missing transaction on repository reads | `LazyInitializationException` on every admin read — the mapper touched lazy child collections after the session closed | `@Transactional(readOnly = true)` on the adapter's read methods |
| Broken optimistic locking | `StaleObjectStateException` on the second edit of any product — the mapper built a fresh entity at version 0, so the update matched no row | Carry the current version onto the mapped entity before saving |
| Ambiguous nullable search parameter | `function lower(bytea) does not exist` — PostgreSQL inferred the `:search is null` parameter as `bytea` | Build an escaped `LIKE` pattern in the adapter instead of a nullable parameter |
| `@PreAuthorize` denial reported as 500 | A permission refusal returned `500 INTERNAL_ERROR` instead of `403` | `AccessDeniedExceptionHandler` in `common-security`; the shared catch-all advice lowered to `LOWEST_PRECEDENCE` so the specific handler wins |
| Gateway did not route the admin catalogue | `/api/v1/admin/catalog/**` fell through to a 404 at the edge; the public settings path pattern did not match the real endpoint | Added the admin route and corrected the public-path list |
