# Security Review

This document is the project's security record. It states the security architecture, the design
decisions behind it, the findings from the assessment, the fixes with their verification evidence,
the risks that remain, and the procedure for keeping the assessment current. It is updated as each
module lands and as the deployment configuration is built; it is never declared "complete".

Scope of the review so far: the backend services, the API gateway, the shared modules, the database
migrations, the payment integration, the file-upload path, the build and dependency configuration,
the environment and secret configuration, the Git history, and the local Docker stack. The React
frontends and the production deployment configuration do not exist yet and are reviewed when they do.

---

## 1. Security architecture

- **The API gateway is the only public entry point.** A browser talks to the gateway; the services
  listen on their own ports on the internal network only. The gateway authenticates every request
  against the identity service's public keys before it forwards anything.
- **The gateway is not the only enforcement point.** Every service re-verifies the token itself and
  enforces its own authorization with `@PreAuthorize`. A request that somehow bypassed the gateway
  still meets a service that checks the signature, the issuer, the role and the permission. The
  gateway's checks are a fast rejection at the edge, not a substitute for the service's own.
- **Stateless bearer authentication.** RS256 access tokens are short-lived (15 minutes) and carry the
  role, the fine-grained permissions and the verification flags. Refresh tokens are opaque random
  values, stored only as a SHA-256 hash, so they are revocable and replay-detectable in a way a
  self-contained JWT refresh token could not be.
- **Asymmetric signing.** Only the identity service holds the RSA private key. Every other service
  verifies against the published JWKS, so a compromised downstream service cannot mint a token.
- **Service-owned data.** No service reads or writes another service's database. Cross-service reads
  go through versioned REST; state changes travel as events through the transactional outbox.

## 2. Authentication and authorization design

**Customers.** Register with name, email, mobile and password. Both email and mobile must be verified
by OTP before the account is treated as fully verified; the access token carries `email_verified` and
`mobile_verified`, and a downstream service refuses a checkout from an unverified customer without a
synchronous call back to identity. OTPs expire, have attempt limits and resend limits, and are stored
hashed.

**Administrators.** A separate aggregate with a separate sign-in endpoint. There is no public admin
registration: administrators are provisioned from configuration, so an open sign-up cannot exist. A
customer credential is refused at the admin endpoint and vice versa. The admin token carries an admin
role and explicit permissions (`customer:read`, `customer:export`, `order:write`, `payment:refund`,
`notification:read`, and so on), and each admin endpoint requires the specific permission it needs.
The seller's customer surface (`GET /api/v1/admin/customers`, `GET /api/v1/admin/customers/{id}`) is
gated by `customer:read`; it lists a summary with no credential field, and its `?search=` term is
escaped into a `LIKE` pattern so a `%` or `_` in the term is matched literally instead of widening the
query.

**Ownership.** A single `CurrentUserProvider.requireOwnerOrAdmin` performs the customer-ownership
check, so one missed comparison cannot expose another customer's order, address or invoice. This is
the IDOR control, and it is exercised by per-service isolation tests.

**Tokens.** Access tokens expire in 15 minutes. Refresh tokens rotate on use; a reused (already
rotated) refresh token is treated as replay, and the whole chain is revoked. Logout revokes the
refresh token.

**Token revocation is enforced, not implied.** An access token names the session that issued it (the
refresh-token row id) in its `jti`, and every service wraps its `JwtDecoder` in a
`RevocationAwareJwtDecoder` that refuses a token whose session has been revoked — so logout, rotation
and replay detection retire the *access* token too, not only the refresh token. Two scopes exist: a
**session** (logout, refresh rotation, replay) and a **subject up to a cutoff** (password change or
reset, which retires every token issued before the change while leaving a fresh sign-in working).
The store is Redis (`revoked:session:*`, `revoked:subject:*`), read on every authenticated request,
each entry carrying a TTL equal to the access-token lifetime so it self-expires. The decoder
**fails closed**: if the store cannot be consulted it refuses the token rather than assuming it is
live, and a token with no `jti` or subject is refused. Password reset tokens are single-use and
time-bounded.

## 3. Secret-management rules

- **No secret in source.** Credentials, keys and passwords come from environment variables with
  development defaults only. `.env` is git-ignored; `.env.example` holds placeholders, never values.
- **Razorpay secrets never leave the server.** The publishable key id is sent to the browser; the key
  secret and the webhook secret are never logged, serialised or returned.
- **The JWT private key is a file or secret mount,** not a property value. Production must supply it;
  the service warns loudly when it generates an ephemeral key.
- **Password hashes never leave the database.** No API returns one, no export includes one, no log
  line contains one.

## 4. Database security

- **One database and one role per service.** Each service connects with a role that owns only its own
  database, so a compromised service cannot read another's tables.
- **Constraints are the last line of defence.** Primary keys, foreign keys, unique, check and not-null
  constraints are declared in the Flyway migrations and proven by each `SchemaAndPersistenceIT`.
- **Parameterised access only.** All data access is through JPA/JPQL with bound parameters; there is
  no string-concatenated SQL anywhere in the codebase. `SchemaAndPersistenceIT` and the search
  endpoints exercise the bound-parameter paths.
- **Flyway owns the schema.** Hibernate runs `ddl-auto: validate`, so an entity that drifts from the
  migration fails the build rather than silently altering a production schema.

## 5. API security

- **Consistent error model.** RFC 9457 problem details with a stable machine-readable code. An
  unexpected exception returns a generic 500; the internal message never reaches the client and is
  logged with the correlation id instead.
- **Input validation.** Jakarta Bean Validation on every request body, plus domain-level validation in
  the aggregates. A malformed or out-of-range value is a 400 with a specific code.
- **Pagination is bounded.** `PageResult` caps the page size, so a caller cannot request an unbounded
  result set.
- **Idempotency on money paths.** Payment initiation takes an `Idempotency-Key`; a replay returns the
  original result, and a reused key with a different body is a conflict.
- **CORS is an allow-list,** never a wildcard with credentials. Only the two frontend origins are
  listed, from configuration.
- **Security headers.** `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, a no-referrer
  policy and HSTS are set on every service and at the gateway.
- **Reports and exports are permission-gated and minimised.** The reporting service's read surface
  (`/api/v1/reports/**`, `/api/v1/exports/**`, `/api/v1/dashboard/**`) requires `report:read` on every
  endpoint, and the customer export additionally requires `customer:export` — checked again inside the
  use case, not only on the controller. An export declares its columns, so a wider query cannot widen
  the file; the customer export masks the email and mobile, and every export writes an audit row naming
  the acting administrator, the dataset, the format and the row count. The download is an attachment
  with `Cache-Control: no-store` so customer data is not cached by the browser, and CSV cells beginning
  with `=`, `+`, `-`, `@`, tab or carriage return are neutralised against formula injection. No export
  can reach a password, hash, OTP, token or secret: the export column list is a closed set and the
  `ExportColumn` guard refuses any column naming an authentication artefact.

## 6. Payment security

- **The amount is decided by the backend, twice.** The order service sends the order's own frozen
  total to payment-service; payment-service verifies a capture against the amount it was asked to
  collect; the order service then refuses a `payment.captured` whose amount is not the order's exact
  frozen total (`PAYMENT_AMOUNT_MISMATCH`). A browser cannot influence the figure at either end.
- **Signature verification.** The checkout signature is verified over `order_id|payment_id` with the
  account secret, computed independently in the test with `javax.crypto` so a wrong-field or
  wrong-secret bug cannot pass. The webhook signature is verified over the raw body; a tampered body
  is refused, and every webhook is refused when no secret is configured.
- **A payment is marked paid only from a verified provider fact** — a captured state re-fetched from
  the provider, or a signed webhook — never from a browser redirect or a checkout callback.
- **Duplicate and replay protection.** Unique `order_id` and `provider_payment_id` indexes; a repeated
  webhook or a repeated verify is a no-op with a single attempt row.
- **No card data.** Only provider identifiers and a coarse method classification are stored. CVV, PIN
  and UPI-PIN never reach the server.
- **Unconfigured is refused, not faked.** With no credentials the service boots but refuses every
  money-moving call with a missing-setting message, so a misconfigured deployment cannot report a
  fake success.

## 7. Frontend security

Not yet built. The requirements recorded for the storefront and admin dashboard: render with React's
default escaping and never `dangerouslySetInnerHTML` on untrusted content; keep secrets out of Vite
environment variables (only the Razorpay publishable key id may reach the browser); protect
customer-specific routes and rely on server-enforced authorization, never on hiding a control;
configure a Content-Security-Policy and the other headers at the reverse proxy. This section is
completed with evidence when the frontends land.

## 8. HTTPS and deployment security

Not yet configured. Production must terminate TLS with a trusted certificate (Let's Encrypt or the
platform), serve HTTPS only, disable debug and development endpoints, restrict database, Redis,
RabbitMQ and internal service ports to the private network, and supply secrets through a secret
manager. The local stack exposes the infrastructure ports on localhost only, for development. This
section is completed with evidence when the deployment configuration lands; HTTPS is not claimed
until it is verified.

---

## 9. Findings and severity

Severity uses the conventional scale (Critical / High / Medium / Low / Informational).

### Fixed

| # | Severity | Finding | Fix | Evidence |
|---|---|---|---|---|
| F-1 | High | **The gateway's public-path allow-list omitted `/api/v1/auth/admin/refresh` and `/api/v1/auth/admin/logout`.** The identity service serves both without a token (a refresh token is not an access token), but the edge required a bearer token for them, so an administrator's session could never be renewed or revoked through the gateway — every admin session died after 15 minutes and could not be logged out at the edge. | Added both paths to `GatewayProperties.publicPaths`, alongside the customer refresh/logout pair. | `GatewayPropertiesTest.opensAdminRefreshAndLogoutWithoutAnAccessToken` (unit) and `RoutingIT.adminRefreshAndLogoutReachIdentityWithoutAToken` (integration: both paths reach identity with no token) |
| F-1b | Medium | **`/api/v1/admin/payments/**` was served by the payment service but never routed at the edge.** Every administrative payment call — the seller's counters and refund surface — 404'd through the gateway while working when the payment service was called directly. Availability, not confidentiality (the route is authenticated when present, and the downstream still enforces `payment:read`/`payment:write`), but the seller's dashboard could not reach it. | Added the admin payments prefix to the payment route in `RoutingConfiguration`. | `RoutingIT.adminPaymentsReachPaymentService` and `RoutingIT.adminPaymentsAreNotPublic` (integration: the route is proxied with a token and refused without one) |
| F-1c | High | **Logout did not revoke the access token, only the refresh token.** A bearer access token is self-contained: revoking the refresh token stopped the session being *renewed*, but the access token already in the client's hands kept working for the rest of its 15-minute life — and for an administrator that token authorises refunds, catalogue edits and customer exports. Rotation and replay detection had the same gap. There was no mechanism anywhere to invalidate an unexpired token. | Access tokens now carry the session id as their `jti`; the identity service revokes the session on logout, on refresh rotation and on replay detection, and revokes the *subject up to a cutoff* on a password change or reset. Every service wraps its `JwtDecoder` in a `RevocationAwareJwtDecoder` that refuses a revoked token and **fails closed** when the store is unavailable; the store is Redis with a self-expiring TTL per entry. | `RevocationAwareJwtDecoderTest` (6 unit: live accepted, revoked session refused, subject cutoff refused, post-cutoff accepted, store failure fails closed, missing `jti` refused); `RedisTokenRevocationRegistryIT` (5 integration, real Redis: presence-based session, time-bounded subject cutoff, later cutoff wins, expired window ignored); `AuthenticationServiceTest` and `AdminAuthenticationServiceTest` assert logout and rotation retire the session's access token; `CustomerJourneyIT`/`AdminJourneyIT` exercise the path over real HTTP; `GatewayRevocationIT` (6 integration, real Redis) proves the **edge** refuses a revoked token before forwarding: a live token is proxied, a revoked session is a 401 that never reaches the downstream, a logged-out administrator cannot reach `/api/v1/admin/notifications`, revocation is scoped to one session, and a subject cutoff refuses only tokens issued before it |
| F-2 | High | **Development defaults were usable in a production-shaped configuration.** Each service fell back to `choco_*_pw` for PostgreSQL, Redis and RabbitMQ when the environment variable was unset. Compose's `${VAR:?}` overlay was the only guard, and it covers only deployments that go through that file — a bare `java -jar`, a Kubernetes manifest or a forgotten `-f` flag bypassed it and started on a published password. | Two independent lines: the prod compose overlay requires every credential with `${VAR:?}`, and `ProductionConfigurationGuard` (a `prod`-profile listener in `common-security`, registered in `spring.factories`) reads the *resolved* environment at startup and refuses to boot when a configured capability's password is missing, blank or still its development default. It is scoped per capability, so a service without a database or broker is not blocked by a check for a dependency it lacks, and it runs at `LOWEST_PRECEDENCE` so `application.yml` is already loaded. | `ProductionConfigurationGuardTest` (10 unit: real secret passes, a service without a datasource is not blocked, each `choco_*_pw` refused, Redis and RabbitMQ defaults refused, missing/blank refused, prod-profile gating); `ProductionDefaultsDriftTest` (reads every service `application.yml` and fails if a `${...:choco_..._pw}` default is not in the guard's list, so a new default cannot slip past) |
| F-3 | Medium | **HSTS depended on the request being seen as secure, which behind a TLS-terminating proxy it is not.** Spring's `HstsHeaderWriter` writes the header only for a request its `SecureRequestMatcher` considers secure (verified in `spring-security-web` 6.5.11), and behind a proxy terminating TLS every request arrives over plain HTTP — so HSTS was never emitted in production. The obvious remedy, `server.forward-headers-strategy=framework`, rewrites `remoteAddr` from the client-supplied `X-Forwarded-For` and would have undone the deliberate rule in `ClientIpResolver` (trust the forwarded address only when the immediate peer is a configured proxy), which is what keeps the rate limiter from being bypassed. | `TrustedProxyHttpsMatcher` (in `common-security`) is the HSTS request matcher for both the shared filter chain and the gateway. It treats a request as HTTPS when the container already says so, or when the immediate peer is in `choco.http.trusted-proxies` and *that* proxy sent `X-Forwarded-Proto: https`. The header is read only from a trusted peer, so an untrusted client can neither force HSTS on nor suppress it. The scheme rule and the address rule share one property, so they cannot drift. | `TrustedProxyHttpsMatcherTest` (8 unit: direct secure, forged header ignored with no proxy, trusted proxy https/http, untrusted peer ignored, missing header, case/whitespace, null list); `GatewayHstsIT` (2 integration over real HTTP: a trusted-proxy `https` request carries `Strict-Transport-Security`, an `http` one does not) |
| F-4 | Medium | **No `Content-Security-Policy` was set for the browser surfaces.** The API returns JSON, so the backend risk is low, but the storefront and admin dashboard are served through the same edge and a page with no CSP lets any injected script run. | The two frontend nginx configurations set a CSP naming only what each page loads. `frontend/storefront/nginx.conf` allows `'self'` plus the Razorpay checkout host in `script-src`/`frame-src` (the checkout script is a third-party origin); `frontend/admin/nginx.conf` is stricter — `script-src 'self'`, no external frame origin — because the dashboard loads no third-party script. Both also set `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY` and `Referrer-Policy: no-referrer`. | `frontend/{storefront,admin}/nginx.conf`; the CSP is served by the nginx container that fronts each bundle |
| F-9 | Low | **The gateway and the fulfilment service disagreed on whether the serviceability probe is public.** The gateway forwards `/api/v1/serviceability/**` without a token (the storefront's "Delivery areas" page is unauthenticated by design), but the fulfilment service's own `public-paths` was empty and answered `401`. Confidentiality was not affected — the endpoint exposes only whether a PIN is covered and at what charge, the same answer for everyone — but the public page was broken for every visitor, and the mismatch was invisible to `RoutingIT` because its downstream is a MockWebServer that never enforces auth. | Added `/api/v1/serviceability/**` to `choco.security.public-paths` in the fulfilment service's `application.yml`, so the edge and the service agree. The administrative kitchen surface is unchanged and still requires a token plus the specific permission. | `FulfilmentJourneyIT.anonymousIsAnswered` (integration: an anonymous quote is a `200`; fails with `401` when the fulfilment `public-paths` is emptied) and `FulfilmentJourneyIT.anonymousCannotReachAdmin` (the admin production and serviceable-area endpoints still refuse an anonymous caller) |
| F-7 | Low | **No dependency, secret or container scanning in CI, and the frontend dependencies were unscanned.** The frontend build installs with `npm ci --no-audit`, so nothing checked them. | `.github/workflows/security.yml` runs gitleaks (secret scan), OWASP dependency-check (backend), Trivy filesystem and config scans (containers/Dockerfiles/compose), and — new — a `frontend-audit` job that runs `npm audit --omit=dev --audit-level=high` for both the storefront and the dashboard. `npm audit` on production dependencies is the threshold that matters: the shipped bundle is what a browser executes. Running it caught a real advisory: `react-router-dom` 6.30.6 carried two moderate `react-router` advisories (open redirect, constructor injection), fixed by upgrading both frontends to `react-router-dom` 7.18.5 with all 105 storefront and 34 admin tests green. | `.github/workflows/security.yml` (`frontend-audit` job); `frontend/*/package.json` pinned to `^7.18.5`; `npm audit` now reports 0 vulnerabilities for both apps |
| F-7b | Low | **The Security workflow failed on every push, so the controls it provides were effectively absent.** Two independent causes: the Trivy action was pinned to `aquasecurity/trivy-action@0.28.0`, a version tag that no longer exists (the action publishes `v0.28.0`), so the container-scan job failed at setup with "unable to find version"; and the OWASP dependency-check job invoked the plugin with no NVD API key, which aborts the run with a fatal `NvdApiException: Invalid API Key` before analysing anything. A permanently red Security tab is worse than none — it trains everyone to ignore it. | Pinned Trivy to the released `v0.36.0` tag and verified every input the workflow passes (`scan-type`, `scan-ref`, `severity`, `ignore-unfixed`, `format`, `exit-code`) still exists in that version's `action.yaml`. The dependency-check job now reads an `NVD_API_KEY` secret: with a key it runs the real scan, and without one it emits a `::warning::` and exits `0` rather than failing, because Trivy's filesystem scan already covers the Maven and npm lockfiles. The missing key is recorded as an external dependency in §11. | `.github/workflows/security.yml`; every workflow's `run:` block passes `bash -n`, and the action references were checked against the GitHub API (only standard `@vN` tags remain) |

### Open — to address before deployment

| # | Severity | Finding | Where | Planned fix |
|---|---|---|---|---|
| F-5 | Low | **Admin MFA is not implemented.** The requirement asks for it "where supported". Admin sign-in is password-only with rate limiting and lockout. | `AdminAuthenticationService` | Add TOTP-based second factor for administrators, or document the decision and the compensating controls. |
| F-6 | Low | **Account-enumeration on registration.** Registration is rate-limited per IP but the response distinguishes an already-registered email. Login and password-reset are already enumeration-safe. | `RegistrationService` | Return a generic response for a duplicate registration and send the "already registered" notice by email, if the business accepts the UX change. |
| F-8 | Informational | **Local image storage has no explicit file permissions.** Files are written with the process umask. The generated-key and path-confinement design already prevents traversal and overwrite. | `LocalImageStorageAdapter` | Set restrictive permissions on the storage root in the deployment configuration. |

### Verified controls (no finding)

Password hashing (Argon2id, OWASP parameters; BCrypt cost 15 fallback with transparent upgrade);
RS256 token signing and issuer validation; refresh-token rotation and replay detection; **access-token
revocation on logout, rotation, replay and password change, enforced by every service's decoder and
failing closed when the store is unavailable**; rate limiting
on login, registration, OTP, password reset and password change, per identifier and per address, with
identifiers hashed before they become Redis keys; OTP expiry, attempt and resend limits; ownership
checks on customer data; admin/customer identity separation with no public admin sign-up; server-side
verification of every price, total and payment amount; Razorpay checkout and webhook signature
verification; duplicate-payment prevention; image-upload type, size and path-traversal protection;
parameterised data access; correlation-id validation against header injection and log forging;
gateway refusal of forged, expired and wrong-issuer tokens; unauthenticated requests refused at the
edge and never forwarded; reporting and export endpoints permission-gated with the customer export
additionally gated by `customer:export`, contact details masked in the export and every download
audited.

## 10. Dependency and secret scanning

- **Git history:** scanned the full history (33 commits) for private keys, AWS keys, GitHub tokens and
  Razorpay live/test keys — no matches. `.gitignore` excludes `.env`, `*.pem` and `*.key`.
- **Working tree:** scanned the notification-service additions and the whole diff for the same
  patterns — no matches.
- **Dependencies:** the Trivy filesystem scan runs on every push and covers the Maven and npm
  lockfiles. The OWASP dependency-check job is wired but gated on an `NVD_API_KEY` repository secret:
  without a key the NVD data feed cannot be updated and the plugin aborts with an "Invalid API Key"
  error, so the job would fail on every push and train people to ignore the Security tab. With no key
  it prints a warning and skips; add the secret to enable it. This is an external dependency, not a
  pass.

## 11. Remaining risks and external dependencies

- **Razorpay live verification is blocked** on valid live credentials and account configuration. Test
  mode and a signature-verifying provider stub prove the code path; no live payment has been claimed.
- **SMS provider is unselected.** With no gateway URL, SMS sends are logged and reported as sent so a
  development environment works end to end; this must be replaced with a real provider before
  production, and the "reported as sent" behaviour must not be mistaken for a real delivery.
- **HTTPS, domain, hosting and TLS certificates are not yet provisioned.** Documented as a blocker;
  unrelated work continues.
- **Backups and recovery are documented but not yet verified end to end.** Scheduled with Phase 14.

## 12. Security maintenance procedure

1. Any change to authentication, authorization, payment, upload or configuration updates this
   document in the same commit.
2. A security finding that is Critical or High takes priority over ordinary feature work and is fixed
   before the next milestone is called complete.
3. Every milestone runs the cumulative regression; a security control is only "done" when a test
   exercises it and the result is recorded here.
4. Dependency, secret and container scans run in CI once the pipeline exists; a failed scan blocks the
   merge to `main`.
5. Never claim a control is active, or a scan passed, unless it was actually run and the evidence is
   recorded here.
