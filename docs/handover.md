# Final handover

Single-seller, made-to-order chocolate e-commerce platform. This document is the close-out record for
the project: what was built, how it is verified, how to run it, what is deliberately out of scope, and
what remains as an external dependency before a real launch. It is written so a new maintainer can
take over without reading the whole repository first.

Everything below is drawn from an actual run on the final commit, not from the plan. Where a claim is
an external dependency rather than a finished control, it says so.

## 1. What this is

One seller sells made-to-order chocolate to customers in India. Customers register, verify an email
and a mobile number, browse products and variants, place orders, pay online through Razorpay, and
receive chocolate that the seller makes and delivers personally. There is no marketplace, no
third-party courier integration and no cash on delivery. Preparation begins only after a verified
payment.

## 2. Repository and final state

| | |
|---|---|
| Repository | `github.com/yadavapanulu143/MADE-TO-ORDER-CHOCOLATE` |
| Branch | `main` (the only branch) |
| Starting commit this session | `6543995` |
| Final commit | `6939c51` |
| Working tree | clean |
| Local vs remote | identical (`6939c51`) |
| History | 58 commits, linear, no merges, no force-pushes |

`main` is the only branch. Development happens directly on it; there are no feature branches and no
pull requests. Every milestone was committed and pushed on its own.

## 3. Architecture at a glance

Eight backend deployables behind one gateway, plus two frontend apps and the supporting
infrastructure. Each backend service owns one PostgreSQL database and no service writes another's.

| Component | Port | Role |
|---|---|---|
| storefront (React/Vite) | 5173 dev / 80 nginx | customer website |
| admin (React/Vite) | 5174 | seller dashboard |
| api-gateway | 8080 | single entry point, JWT validation, rate limiting, circuit breaker |
| identity-service | 8081 | customers, admins, addresses, OTP, tokens, audit |
| catalog-service | 8082 | products, variants, prices, images, website settings |
| order-service | 8083 | carts, orders, invoices, cancellations, refund requests |
| payment-service | 8084 | Razorpay orders, verification, webhooks, refunds |
| fulfilment-service | 8085 | production, packaging, serviceability, delivery |
| notification-service | 8086 | email and SMS fan-out |
| reporting-service | 8087 | read models, dashboards, CSV/XLSX exports |

Infrastructure: PostgreSQL 17, Redis, RabbitMQ, plus an optional `observability` profile
(Prometheus, Grafana, Loki, Promtail, Tempo).

Every backend service is a five-module Maven project with the same shape — `-domain`,
`-application`, `-adapters-in`, `-adapters-out`, `-app` — and dependencies point inward only. The
pure domain modules set `skipEnforcer=false`, so the build fails if a domain module ever acquires
Spring, JPA, Jackson, Flyway, PostgreSQL or the Razorpay SDK. This is enforcement, not convention.

`docs/architecture.md` holds the full design; `AGENTS.md` holds the working notes and the list of
pitfalls already hit (52 of them) so they are not repeated.

## 4. What is implemented, by module

Every roadmap milestone is complete. `docs/development-progress.md` carries the requirement-by-
requirement status: **131 requirement rows — 130 DONE, 1 recorded as out of scope (Kubernetes), 0
open.**

- **Identity** — customer and administrator registration, email and mobile OTP, Argon2id/BCrypt-15
  password hashing, RS256 JWT with session-bound `jti`, refresh rotation with replay detection,
  logout that revokes the access token as well as the refresh token, addresses, audit log, rate
  limiting and account lockout as separate controls.
- **API Gateway** — routing for every service, edge token validation, revocation-aware decoding,
  per-path rate limits, circuit breaker with an RFC 9457 fallback, CORS, proxy-aware HSTS.
- **Catalog** — products, variants, attributes, images, website settings, optimistic locking, escaped
  search.
- **Order** — cart, checkout, frozen order snapshot, three independent status tracks
  (payment/preparation/delivery) with an explicit transition table, invoices, cancellations, refund
  requests.
- **Payment** — Razorpay order creation, checkout-signature verification, provider re-fetch before a
  payment is marked paid, webhook verification over the raw body, idempotency by database unique
  index, refunds.
- **Fulfilment** — production jobs opened only by `order.confirmed`, ingredient tracking, packaging,
  serviceability quotes, seller-managed delivery with explicit status machines.
- **Notification** — see §5.
- **Reporting** — read models fed by events, seller dashboard, sales/order/payment reports,
  CSV/XLSX exports with masked contact details and permission gating.
- **Storefront and admin dashboard** — both React/Vite/TypeScript, both built and tested.

## 5. Notification service

The notification service (port 8086, database `choco_notification`) is a complete module.

- **Event coverage.** Durable queues `choco.notification-service.events` and
  `choco.notification-service.contacts`, each bound to the shared topic exchange with its own
  dead-letter routing key. Bound routing keys cover customer registered, verified and
  contact-changed; order placed, confirmed and cancelled; invoice issued; payment captured, failed
  and pending; refund requested and processed; dispute raised; production started and completed;
  ready for dispatch; delivery assigned, out for delivery, delivered, failed and rescheduled; and low
  stock. Nineteen `NotificationType` values in total.
- **Persistence and state.** `NotificationStatus` is `PENDING → SENT | FAILED | DEAD_LETTER`, with
  `SUPPRESSED` for a deliberately skipped message. `SENT` and `DEAD_LETTER` are terminal. `FAILED` is
  a **resting** state: a failed attempt stays `FAILED` so it is visible in the seller's log and can be
  resent, and a retry can move `FAILED → SENT` on success or `FAILED → DEAD_LETTER` once the budget is
  spent. The `FAILED → PENDING` transition exists only for an administrator resend and resets the
  attempt counter, so a resend gets a full budget rather than immediately dead-lettering again.
- **Idempotency.** Every notification carries a `dedupe_key` derived from the producing event id and
  type; the database enforces it unique. A broker redelivery therefore cannot send a second copy.
- **Rendering.** The subject and body are rendered to finished text before they are stored, so a retry
  re-sends exactly what the customer would have received and a later template change cannot rewrite
  history. Variable substitution is validated; a missing variable is handled, not injected.
- **Retry.** Bounded retries with exponential backoff and a maximum attempt count; the retry query
  `findDueForAttempt` selects both `PENDING` and `FAILED` rows due for an attempt. A poison message
  dead-letters instead of looping (`default-requeue-rejected: false`).
- **A provider failure is never a business failure.** The consumer records the failure and returns;
  nothing on this path writes order or payment state, so a mail outage cannot un-pay an order.
- **Administration.** `/api/v1/admin/notifications` is gated by `notification:read` /
  `notification:write` and routed by the gateway. A customer token is refused, an unknown id is a 404,
  a delivered notification cannot be resent (409), and a resend writes an audit row naming the actor.
  The public path list never opens the admin surface.

## 6. Security posture

`docs/security-review.md` is the living security record: architecture, findings by severity with
evidence, fixes, residual risks and the maintenance procedure. The findings fixed to date are F-1
through F-10; the open items are low severity and documented (admin MFA, registration enumeration
wording, local image file permissions).

The controls that matter most, and where they are proven:

- Passwords are hashed with Argon2id or BCrypt cost 15 and never logged, serialised or exported.
- Access tokens are self-contained but session-bound; a `RevocationAwareJwtDecoder` refuses a revoked
  token and **fails closed** when the revocation store is unavailable. Logout, refresh rotation,
  replay detection and password change all revoke.
- Customer and administrator authentication are separate aggregates and separate endpoints; a
  customer credential is refused at the admin endpoint because the admin service never reads the
  customers table.
- Payment state is a projection of a verified provider fact. The webhook signature is verified over
  the exact bytes received; the checkout redirect only triggers a server-side verify; a capture must
  be for the order's exact frozen total or it is refused.
- Exports never contain a password, hash, OTP, token or secret, and mask the customer's contact
  details.
- Production refuses to start on a development credential default (`ProductionConfigurationGuard`),
  and HSTS is emitted behind a trusted TLS-terminating proxy (`TrustedProxyHttpsMatcher`).

Never claim zero risk. The remaining risks and external dependencies are in §11 of the security
review and repeated in §9 below.

## 7. Test evidence (final commit)

Run on the final commit, from a clean state.

| Suite | Result |
|---|---|
| `mvn clean verify` at `backend/` | **BUILD SUCCESS — 1273 tests, 0 failures, 0 errors, 0 skipped** |
| Gateway | 16 unit + 53 integration (real Redis) |
| Backend integration tests | real PostgreSQL, Redis and RabbitMQ through Testcontainers, plus a GreenMail SMTP sink; no mocked test is presented as integration |
| Storefront (Vitest) | 105 tests, 17 files, all pass |
| Admin dashboard (Vitest) | 34 tests, 10 files, all pass |
| End-to-end journey against the compose stack | **33 passed, 0 failed, 0 skipped** |

The end-to-end journey (`scripts/e2e-journey.py`) drives the whole business flow over the gateway:
registration → email and mobile OTP verification → sign in → address → catalogue and serviceability
quote → place order with an idempotency key → cross-customer isolation checks → verified capture
confirms the order → fulfilment opens a job → the seller walks production and delivery → the customer
sees delivery → notifications sent → invoice issued and readable → reports and exports. It also
exercises the refusals: a wrong password, a duplicate registration, a wrong OTP, an uncovered PIN, a
capture for the wrong amount, an illegal production jump, a forged admin token, and an unauthenticated
call to each protected surface.

## 8. Defect found and fixed during this final verification

One defect survived every unit test and every warm end-to-end run and was found only by exercising the
**cold-start** path.

**Symptom.** The first request after a cold start returned `503` from the gateway's circuit-breaker
fallback, while the identity service had actually completed and committed the registration (the audit
log recorded `CUSTOMER_REGISTERED SUCCESS`). Every later request succeeded, so it read as a flaky
service rather than a gateway limit. It reproduced on three of three fresh cold starts.

**Root cause.** `spring-cloud-circuitbreaker-resilience4j` wraps *every* proxied call in a Resilience4j
`TimeLimiter` whose `ofDefaults()` is `Duration.ofSeconds(1)` with `cancelRunningFuture=true`. A cold
JVM's first request, or Argon2 password hashing during registration (~0.9s by design), legitimately
exceeded one second, so the gateway cancelled the response and answered from the fallback. The bound
that is meant to apply is the proxy read timeout (10s, configurable); a second, tighter, invisible one
must not exist.

**Fix.** `spring.cloud.circuitbreaker.resilience4j.disable-time-limiter: true` on the gateway. On this
synchronous stack the call is already bounded by the read timeout, and a connect or read timeout still
surfaces as an exception the breaker counts.

**Proof.**
- `GatewayCircuitBreakerTimeoutIT` (new, 2 tests) pins both halves: a 1.5s response is delivered, and a
  3s response is still refused so the route did not become unbounded. The test is proven to catch the
  defect — it fails with a `503` when the flag is removed. The delay happens before the response
  starts, as a slow handler does; a body-only delay sends the headers first and lands outside the
  guarded call, so it does not reproduce the defect.
- Against the rebuilt compose stack the first request now returns `202` in `1.10s` and the audit log
  shows the registration committed.
- The full end-to-end journey passes 33/33 on the fixed stack.

Recorded as pitfall 52 in `AGENTS.md` and finding F-10 in `docs/security-review.md`.

## 9. External dependencies and known limitations

These are real dependencies or deliberate scope decisions, not unfinished code. Do not fabricate a
value for any of them.

- **Razorpay live verification** is blocked on valid live credentials and account configuration. Test
  mode and a signature-verifying provider stub prove the code path; no live payment is claimed.
- **The SMS provider is unselected.** With no gateway URL, SMS sends are logged and reported as sent so
  a development environment works end to end. This must be replaced with a real provider before
  production, and the "reported as sent" behaviour must not be mistaken for a real delivery.
- **HTTPS, domain, hosting and TLS certificates are not provisioned.** The code is written for a
  terminating proxy and verified up to that point; HTTPS is not claimed until it is deployed and
  checked. Local development is plain HTTP on localhost.
- **Backups** are documented and the restore has been rehearsed against the local stack (all seven
  databases and their rows returned with zero `psql` errors). It has not been rehearsed against a
  production instance, which does not exist yet, and the rehearsal is manual.
- **Kubernetes is out of scope.** Docker Compose is the selected deployment approach. The images are
  twelve-factor and probe-ready, so an alternative orchestrator would need manifests only, not code
  changes.
- **CI deployment is manual.** The image workflow publishes on `main` when a `REGISTRY` secret is set;
  nothing deploys automatically, and the target host and its credentials are the owner's to provide.
- **Low-severity security items** remain open and documented: admin MFA, registration account-
  enumeration wording, and explicit permissions on the local image storage root.

## 10. How to run it

```bash
# infrastructure only
docker compose up -d postgres redis rabbitmq

# the full stack (ten application containers plus infrastructure)
docker compose -f docker-compose.yml -f docker-compose.app.yml up -d --build

# observability, optional
docker compose --profile observability up -d

# the production shape (requires every secret; fails closed naming the missing variable)
docker compose -f docker-compose.yml -f docker-compose.app.yml -f docker-compose.prod.yml up -d
```

Grafana is on `http://localhost:3000` (`admin` / `choco_grafana_pw`); RabbitMQ management is on
`http://localhost:15672` (`choco` / `choco_rabbit_pw`). A development SMTP sink on port 1025 is enough
to read OTPs; set `CHOCO_LOG_OTP=true` to also log them (off by default, and enabling it logs a
startup warning).

Build and test:

```bash
cd backend && mvn clean verify            # unit tests + integration tests + enforcer rules
cd frontend/storefront && npm ci && npm test -- --run
cd frontend/admin && npm ci && npm test -- --run
```

`docs/deployment.md` covers components, the production shape, the signing key, HTTPS placement,
durability and volumes, backup and restore, operations and troubleshooting.
`docs/local-development.md` covers the day-to-day loop.

## 11. Suggested next steps

1. Provision hosting, a domain and TLS; deploy the production compose shape behind a terminating
   proxy; then verify HSTS and the secure-cookie behaviour against the real host.
2. Obtain Razorpay live credentials and run one real end-to-end payment in a low-value test, then
   confirm the webhook path against the provider.
3. Select and configure an SMS provider, and remove the "log and report as sent" fallback for
   production.
4. Rehearse a backup and restore against the production instance and schedule it.
5. Decide on the three open low-severity security items and record the decision in
   `docs/security-review.md`.

## 12. Process closure

The development process is closed. All roadmap milestones are complete; the tracker has no open
requirement. The two immediate objectives named at the start of this session — completing the
notification service and closing the gateway admin-session security gap — were already delivered in
commit `399379c` and were re-verified here against the running stack. The one defect this session found
(the cold-start gateway timeout) is fixed, tested, committed as `6939c51`, and pushed. Further work is
maintenance against the external dependencies in §9, not unfinished development.
