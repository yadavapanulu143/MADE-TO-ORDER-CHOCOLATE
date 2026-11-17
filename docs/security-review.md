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
refresh token. Password reset tokens are single-use and time-bounded.

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

### Open — to address before deployment

| # | Severity | Finding | Where | Planned fix |
|---|---|---|---|---|
| F-2 | High | **Development defaults are usable in a production-shaped configuration.** Each service falls back to `choco_*_pw` for PostgreSQL, Redis and RabbitMQ when the environment variable is unset. There is no profile that rejects a default or missing secret at startup, so a deployment that forgets to set a variable runs on a known password. | every `application.yml`; `docker-compose.yml` | A `prod` profile (or an explicit startup check) that fails fast when a required secret is unset or equals its development default. Scheduled with Phase 14. |
| F-3 | Medium | **HSTS depends on the request being seen as secure, which behind a TLS-terminating proxy it will not be unless forwarded headers are trusted.** Spring's `HstsHeaderWriter` writes the header only for a request its `SecureRequestMatcher` considers secure (verified in `spring-security-web` 6.5.11), and it is also sent on the plain-HTTP local stack where it is inert. If the production proxy terminates TLS and the application does not read `X-Forwarded-Proto`, HSTS is never emitted at all. | `common-security` `SecurityAutoConfiguration`, `GatewaySecurityConfiguration`, deployment proxy | In Phase 14 configure `server.forward-headers-strategy` so the app trusts `X-Forwarded-Proto` from the known proxy, then verify the header is present over HTTPS and that plain HTTP redirects. |
| F-4 | Medium | **No `Content-Security-Policy` header is set by the backend.** The API returns JSON so the risk is low today, but the storefront and admin dashboard are served through the same edge and need a CSP. | gateway / reverse proxy | **Both frontends done:** `frontend/storefront/nginx.conf` sets a CSP that names only `'self'` and the Razorpay checkout host in `script-src`/`frame-src`, plus `X-Content-Type-Options`, `X-Frame-Options` and `Referrer-Policy`. `frontend/admin/nginx.conf` sets a stricter CSP (`script-src 'self'`, no external frame origin) because the dashboard loads no third-party script. |
| F-5 | Low | **Admin MFA is not implemented.** The requirement asks for it "where supported". Admin sign-in is password-only with rate limiting and lockout. | `AdminAuthenticationService` | Add TOTP-based second factor for administrators, or document the decision and the compensating controls. |
| F-6 | Low | **Account-enumeration on registration.** Registration is rate-limited per IP but the response distinguishes an already-registered email. Login and password-reset are already enumeration-safe. | `RegistrationService` | Return a generic response for a duplicate registration and send the "already registered" notice by email, if the business accepts the UX change. |
| F-7 | Low | **No dependency, secret or container scanning in CI.** There is no `.github/workflows` yet, so no automated `dependency-check`, secret scan or image scan runs. The storefront's npm dependencies are likewise unscanned (`npm audit` has not run in CI). | `.github/workflows` | Add OWASP Dependency-Check, `npm audit`, a secret scan and a container scan in Phase 14. |
| F-8 | Informational | **Local image storage has no explicit file permissions.** Files are written with the process umask. The generated-key and path-confinement design already prevents traversal and overwrite. | `LocalImageStorageAdapter` | Set restrictive permissions on the storage root in the deployment configuration. |

### Verified controls (no finding)

Password hashing (Argon2id, OWASP parameters; BCrypt cost 15 fallback with transparent upgrade);
RS256 token signing and issuer validation; refresh-token rotation and replay detection; rate limiting
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
- **Dependencies:** not yet scanned by a tool. The parent POM pins versions; a Dependency-Check run is
  scheduled with CI (F-7).

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
