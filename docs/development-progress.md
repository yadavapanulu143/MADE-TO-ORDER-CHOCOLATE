# Development Progress Checklist

Tracks every requirement from the master prompt. Statuses: **DONE**, **IN PROGRESS**, **TODO**.
A phase is only marked DONE when its tests run green and the behaviour has been verified.

**Last verified:** `mvn verify` in `backend/` — 316 tests, 0 failures, 0 errors. Integration tests
(`*IT`) run under the `verify` phase via the failsafe plugin; unit tests (`*Test`) run under `test`.
The identity service now has full-stack integration tests that start real PostgreSQL, Redis and
RabbitMQ containers plus a GreenMail SMTP sink, and drive the service over HTTP: Flyway/Hibernate
schema agreement and database constraints (`SchemaAndPersistenceIT`), the complete customer journey
including OTP verification and token rotation (`CustomerJourneyIT`), administrator authentication and
the customer/admin boundary (`AdminJourneyIT`), and outbox-to-broker delivery (`OutboxRelayIT`). The
Redis rate limiter is additionally exercised against a real `redis:7.4-alpine` container.

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
| 2.2 | API Gateway routing | TODO | `gateway/` module not created |
| 2.3 | Flyway migrations per service | DONE | `identity-app/src/main/resources/db/migration/V1__identity_schema.sql` |
| 2.4 | Redis integration (cache, rate limiting) | DONE | `shared/common-redis`; `RedisRateLimiterIT` (4 tests) green against a real Redis |
| 2.5 | Inter-service REST client with timeouts/retries | TODO | No consumer yet; identity is self-contained |
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
| 4.5 | Product catalogue (multi-product ready) | TODO | |
| 4.6 | Product images | TODO | |
| 4.7 | Pricing + variants/sizes | TODO | |
| 4.8 | Admin product management | TODO | |

## Phase 5 — Shopping and orders

| # | Requirement | Status |
|---|---|---|
| 5.1 | Shopping cart | TODO |
| 5.2 | Quantity management + configurable limits | TODO |
| 5.3 | Checkout validation (availability, quantity, verification, address, PIN) | TODO |
| 5.4 | Order creation with immutable price snapshot | TODO |
| 5.5 | Order history (own orders only) | TODO |
| 5.6 | Order status transitions + history | TODO |

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
| **Total** | **316** | **green** |

### Identity integration tests (`mvn verify`)

| Class | Tests | What it proves |
|---|---|---|
| SchemaAndPersistenceIT | 7 | Flyway applied V1–V3; Hibernate `validate` matches the schema; DB-level unique, check and FK constraints reject bad rows |
| CustomerJourneyIT | 14 | Register → verify email and mobile → sign in → profile/contact/addresses, refresh rotation and replay detection, logout, login rate limiting, ownership isolation |
| AdminJourneyIT | 10 | Bootstrap administrator, admin token authority, no public admin sign-up, customer credentials and refresh tokens refused at admin endpoints, independent admin rate limiting |
| OutboxRelayIT | 3 | A real registration publishes `customer.registered` to a bound RabbitMQ queue and marks the outbox row published |
