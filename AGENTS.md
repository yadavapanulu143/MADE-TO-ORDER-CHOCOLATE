# Repository Notes for Agents

Single-seller, made-to-order chocolate e-commerce platform. See `docs/architecture.md` for the
design and `docs/development-progress.md` for the requirement-by-requirement status.

## Permanent development and execution rules

These rules were set by the project owner and apply to **every** remaining module, milestone,
integration and future session. They are not optional and are not renegotiated per task.

1. **Automatically push every successful commit to GitHub.** After each module or meaningful
   milestone: implement, run all required tests (including the cumulative regression suite), fix
   every failure, review the full diff for accidental changes/secrets/generated files, update the
   docs and tracker, create a meaningful commit, push it, and verify the remote has it. Push only
   tested and committed work. Never push failing tests. Never force-push or rewrite remote history.
   Never commit or push credentials, passwords, API keys, tokens or certificates. If auth,
   permissions, network or a remote conflict blocks the push, report the exact issue, keep the local
   commit and continue only when it is safe. Never claim a push succeeded unless it did. Do not wait
   for approval after each successful commit.
   - **`main` is the only branch.** Develop directly on local `main`. Do not create feature,
     temporary or release branches, and do not open pull requests. After every successful commit,
     push that exact commit to `origin/main`, then confirm the remote hash equals the local hash.
     Every verified milestone is committed and pushed on its own; do not batch several milestones
     into one push. Fetch and inspect remote changes before integrating them; never overwrite remote
     work blindly.
2. **Follow the development sequence strictly.** Identity Service (done, `d507fa2`) → API Gateway →
   Catalog/Product → Order Management → Razorpay Payment → Made-to-Order Production →
   Seller-Managed Delivery → Notification → Invoices/Reporting/Exports → Customer Storefront →
   Admin Dashboard → Docker Compose/Deployment/CI-CD → End-to-End Testing and Handover. No
   priority-based reordering. Finish one module before starting the next, then proceed without
   asking.
3. **Extremely comprehensive testing is mandatory.** Aim for maximum meaningful coverage of every
   execution path, branch, boundary, malformed input, invalid transition, authz boundary,
   customer-to-customer isolation, admin boundary, concurrency/race, transaction/rollback, DB
   constraint and migration, request/response validation, failure recovery, idempotency, abuse
   scenario and integration with prior modules. Use unit, integration (Testcontainers, real infra),
   contract, security, migration, concurrency, property/fuzz, mutation, frontend and end-to-end
   tests. There is no upper bound on test count when complexity justifies it.
4. **Coverage and quality requirements.** Maintain measurable line and branch coverage reports.
   Investigate uncovered critical code and add tests where practical. Never inflate coverage with
   meaningless assertions or code-executing-only tests. Never delete, weaken, bypass or skip an
   existing test to make the build pass. Never present a mocked test as an integration test. Never
   claim defect-freedom from a coverage number.
5. **Cumulative testing after every module.** Run the new module's unit and integration tests, all
   previously completed modules' unit and integration tests, the complete backend regression suite,
   affected frontend tests, and the relevant API/security/DB/e2e tests. Fix all failures, re-run,
   then commit and push. The Identity Service integration suite (real PostgreSQL, Redis, RabbitMQ,
   email) stays part of the cumulative suite permanently.
6. **Continue working autonomously.** Keep going until the whole project is genuinely complete. If
   the session is interrupted, save exact progress in `docs/development-progress.md` and this file:
   current module, completed functionality, test results, commit hash, GitHub push status, exact
   next task and blockers. Resume from that state; never recreate the project or repeat finished
   work. If an external credential/provider value is unavailable, continue with unrelated work using
   safe sandbox configuration, document the blocker, and never fabricate a real transaction.
7. **Milestone reporting.** After each milestone report: module, functionality, unit test counts
   (run/pass/fail/skip), integration test counts, cumulative regression results, coverage metrics
   and uncovered areas, security/e2e results, build status, commit hash, push status and remote
   branch, blockers, and the next module. Do not call a module complete until its functionality and
   required tests are actually complete.

## Build commands

The parent POM lists only modules that exist (the rest are commented out), so a full build works:

```bash
cd backend
mvn verify                        # unit tests + integration tests + enforcer rules
mvn -f shared/common-core/pom.xml test    # one module's unit tests
```

Two things worth knowing about the test phases:

- **`*Test` classes run under `test`; `*IT` classes run under `verify`** (failsafe). A module's
  integration tests are skipped by `mvn test`, which is intended — they need Docker. Use `mvn verify`
  to run everything.
- **Shared modules must be `install`ed before services compile against them.** `mvn -f <module>`
  without the reactor will fail to resolve a sibling that was only built, not installed.

## Layout

```
backend/pom.xml                    parent: pinned versions + enforcer + pluginManagement
backend/shared/common-core         framework-free domain primitives, error model, ports
backend/shared/common-web          RFC 9457 errors, correlation ids, OpenAPI, Jackson
backend/shared/common-security     Argon2id/BCrypt-15 hashing, RS256 JWT, RBAC
backend/shared/common-events       event envelope, transactional outbox, RabbitMQ topology
backend/shared/common-redis        Redis rate limiter + disables Redis repository scanning
backend/shared/common-test         Testcontainers definitions, port fakes
backend/gateway                    Spring Cloud Gateway (server MVC): routing, edge auth, throttle
backend/services/<name>/           five modules each: -domain -application -adapters-out -adapters-in -app
infra/                             postgres init, prometheus, grafana, loki, tempo, otel
```

## Conventions that are enforced, not optional

- **Clean Architecture**: `-domain` modules set `skipEnforcer=false` and thereby fail the build if
  they acquire Spring, JPA, Jackson, Flyway, Postgres or the Razorpay SDK. Verified working.
- **Enforcer excludes must be explicit per pattern.** Comma-separated property values break the
  parser; write one `<exclude>` element per artifact pattern.
- **Money is integer paise**, never `double`. Use `Money.inr(...)`.
- **Time comes from the injected `Clock` port**, never `Instant.now()`, so tests are deterministic.
- **`AggregateRoot` API**: `registerEvent`, `pullDomainEvents()` (drains), `peekDomainEvents()`
  (read-only). Tests asserting on events must use `peekDomainEvents()`.
- **Passwords**: hash via the `PasswordHasher` port. Never log, serialise or export a hash.
- **Ownership checks** go through `CurrentUserProvider.requireOwnerOrAdmin`.
- **Rate limits are declared once**, in `AbuseGuard.Policy.defaults()`. `RateLimitProperties`
  initialises its fields from that method and `RateLimitPropertiesTest` asserts the two agree. Never
  restate a limit as a literal elsewhere; that is how the login limit once drifted below the account
  lockout threshold and started masking the specific lockout error.
- **The client address for rate limiting comes from `ClientIpResolver`**, never from
  `request.getHeader("X-Forwarded-For")` directly. The header is client-supplied and only trusted when
  the immediate peer is in `choco.http.trusted-proxies` (empty by default).
- Domain layers must not reference `com.choco.identity.domain.event.X` by simple name inside
  `filteredOn` lambdas when a name clash is possible; use the fully-qualified name.

## Local infrastructure

```bash
docker compose up -d postgres redis rabbitmq              # core
docker compose --profile observability up -d              # + prometheus/grafana/loki/tempo
docker compose --profile observability down -v            # reset everything
```

Grafana http://localhost:3000 (admin / choco_grafana_pw), RabbitMQ http://localhost:15672
(choco / choco_rabbit_pw).

## Pitfalls already hit (do not repeat)

1. **Postgres init script unreadable** — files created with mode 600 cannot be read by the postgres
   user. Always `chmod 644 infra/postgres/init/*.sql`.
2. **`#` comments in `.sql` files** — Postgres requires `--`. A `#` fails at init.
3. **`CONNECT` is granted to `PUBLIC` by default** on every new database. The init script revokes it
   and grants it back only to the owning role. Verified: `choco_order` cannot connect to
   `choco_identity`.
4. **`jackson-datatype-jsr310` is required** to serialise `Instant` in event envelopes and outbox
   payloads; without it the first publish throws at runtime, not at compile time.
5. **`library/` image namespace** — Docker Hub API returns 404 for `postgres:17.6-alpine` because
   official images live under `library/`. Verify images with an actual `docker pull`.
6. **`@Value` cannot bind a YAML list.** `@Value("${choco.security.public-paths:}") List<String>`
   resolves to the empty default, so every configured public path stays protected and endpoints
   return 401 with no error. Use a `@ConfigurationProperties` class (`SecurityProperties`).
7. **A salted password hash can never be used for lookup.** Argon2 and BCrypt salt every digest, so
   `findByHash(hasher.hash(token))` matches nothing, and password reset fails 100% of the time while
   looking correct. Opaque tokens are hashed with the deterministic `OpaqueTokenHasher` (SHA-256).
8. **The parent POM must not list unimplemented modules.** A `<module>` without a `pom.xml` breaks the
   whole reactor, so `mvn test` fails before compiling anything. Comment modules in as they land.
9. **RabbitMQ's default converter accepts only String/byte[]/Serializable.** Publishing an
   `EventEnvelope` POJO fails at runtime and the outbox retries to exhaustion. `OutboxRelay`
   serialises to a JSON String before sending.
10. **A mail outage must not roll back registration.** Delivery is best-effort; the account and OTP
    challenge are already persisted and the customer can request a resend.
11. **Spring Data Redis scans for repositories even in a JPA service.** With `spring-data-redis` on the
    classpath, every JPA repository is logged at startup as *"Could not safely identify store
    assignment for repository candidate ..."*. Worse, a stray Redis repository interface would be wired
    to the wrong store. `RedisRepositoryScanningPostProcessor` in `common-redis` sets
    `spring.data.redis.repositories.enabled=false` as a lowest-precedence default for every service.
12. **A declared Maven plugin is not a bound one.** `maven-failsafe-plugin` was in `pluginManagement`
    with no `<executions>`, so `*IT` tests compiled and then never ran — a green build that tested
    nothing. It is now bound to `integration-test`/`verify` in the parent `<build><plugins>`.
13. **`@Value("${x:default}")` duplicates the default it mirrors.** The login limit existed as a
    literal in a `@Value` default *and* in `AbuseGuard.Policy.defaults()`, the two drifted, and the
    copy that took effect was the one nobody maintained. Prefer one source of truth plus a test that
    the bound configuration equals it.
14. **A count check and a count increment must be one atomic step.** A read-then-write rate limiter
    passes every sequential test and lets all concurrent requests through: the Redis version uses a Lua
    script, and `RedisRateLimiterIT#concurrent_requests_cannot_exceed_the_limit` fails loudly (40 of 40
    allowed) if that is ever replaced with separate round trips.
15. **Rate limiting and account lockout are separate controls and must not overlap.** The lockout is
    per account and reports `ACCOUNT_LOCKED`; the limiter is a coarse backstop. If the limiter fires
    first, every locked-out customer gets the generic 429 and the actionable message is unreachable.
16. **`spring-boot:run` resolves sibling modules from the local Maven repo, not the working tree.**
    After changing `identity-adapters-out` (or any module the app depends on), run
    `mvn -DskipTests install -pl <module>` first, or the app boots against a stale jar and your change
    appears to have no effect.
17. **`ddl-auto: validate` is the check that catches a schema/entity drift.** A new JPA entity whose
    columns do not exactly match its Flyway migration makes the app fail to start; it does not warn.
    The fastest loop is to boot the service against the compose Postgres after adding a migration.
18. **"At most one default per customer" needs a partial unique index, not a plain one.** A unique
    index on `(customer_id, is_default)` would forbid a second *non-default* address, because every
    such row has `is_default = false`. `CREATE UNIQUE INDEX ... ON t (customer_id) WHERE is_default`
    is the only correct expression, and it is what stops two concurrent "make default" requests from
    both succeeding.
19. **Ownership scoping belongs in the repository method, not in a caller-side check.** Address reads
    go through `findByIdAndCustomerId`; there is deliberately no unscoped `findById`. The service also
    reports a cross-customer access as `404` rather than `403`, so the response does not confirm that
    another customer's id exists. Five tests fail if the `customer_id` predicate is dropped from the
    query, which is how that rule is kept honest.
20. **A multi-field domain `update` must validate into locals before assigning.** Assigning fields one
    at a time leaves the aggregate half-updated when a later field is rejected; the caller sees an
    exception, but the in-memory object has already changed and a subsequent save would persist a mix
    of old and new values. `Address.update` validates everything first, and a test pins it.
21. **`AbuseGuard.Policy` is a positional record; adding a field breaks every call site.** There are
    three: `defaults()`, `RateLimitProperties.toPolicy()` (plus its field initialisers) and the strict
    policy in `AbuseGuardTest`. Add the new pair in the same position in all three, or the build fails
    with an argument-count error rather than anything explanatory.
22. **A contact change is a security event, not a field edit.** `Customer.changeEmail` /
    `changeMobile` clear that channel's verified flag, because the new address has not been proven.
    Without the clear, a customer could move an account onto an address they do not control and keep
    the "verified" badge — receiving password-reset mail for a mailbox that is not theirs. A code is
    then issued to the *new* destination, not the old one. Two tests fail if the flag is left set.

23. **Administrators are a separate aggregate and table, not customers with a flag.** `AdminAccount`
    has its own repository, its own refresh tokens (`admin_refresh_tokens`), and its own login
    endpoint under `/api/v1/auth/admin`. The separation is the authorisation boundary: a
    self-registered customer can never present their own credentials at the admin endpoint, because
    the admin service never consults the customers table. The role is read from the administrator row
    at token-issue time and is never taken from request input, so it can only be `ADMIN` or
    `SUPER_ADMIN`. There is deliberately no public admin sign-up; the first administrator is created
    once from `choco.admin.bootstrap.*` and that configuration is then cleared. The bootstrap is
    idempotent — it refuses a weak password rather than seeding one and never overwrites an existing
    account, so a restart with different credentials cannot reset a live password.

24. **Admin and customer rate limits use the same thresholds but separate counters.** `AbuseGuard`
    keys admin attempts under `rl:adminlogin:*` rather than `rl:login:*`. Sharing one counter would
    mean a flood of customer sign-ins from a shared NAT could throttle the seller out of their own
    dashboard, and an attack on the dashboard could lock customers out of the storefront.

25. **The integration tests run against real containers, not mocks, on purpose.** A mock proves a
    call happened; it cannot prove a migration matches its entity, that a `CHECK` constraint fires,
    that a Lua rate limiter is atomic, or that an event actually reaches RabbitMQ. Those are the
    failures that pass every unit test and break production, so `AbstractIdentityIntegrationTest`
    starts real PostgreSQL, Redis and RabbitMQ and reads OTPs from a real SMTP sink. Two seams are
    deliberate and worth remembering: the SMTP sink is on a **fixed port (3025)** because GreenMail
    restarts per class while the Spring context is cached (a random port sends the second class to a
    dead sink), and the base class **reserves a free HTTP port** so `server.port` and
    `choco.security.jwk-set-uri` agree and tokens are verified through the real JWKS fetch.

26. **`@ConditionalOnMissingBean` on a bean whose type is implemented by a framework bean silently
    disables yours.** The shared CORS bean is typed `CorsConfigurationSource`, and Spring MVC's
    `mvcHandlerMappingIntrospector` also implements that interface. It was registered first, so the
    condition saw "a bean already exists", skipped the real CORS configuration, and the introspector
    was injected in its place — CORS was off for every service with no error. The condition is now
    `@ConditionalOnMissingBean(name = "corsConfigurationSource")` and the bean is `@Primary`. A
    preflight integration test (allowed origin answered, unknown origin never wildcarded) is what
    caught it; a unit test of the config object never would have.

27. **A circuit breaker does not treat a 5xx response as a failure by default.** In Spring Cloud
    Gateway MVC `CircuitBreakerConfig.statusCodes` starts empty, so a service returning a steady
    stream of 500s looks perfectly healthy to the breaker — it only trips on a thrown exception such
    as a connection failure. Name the codes (`500`, `502`, `503`, `504`) or the breaker protects
    against crashes but not against a service that is up and answering with errors. Also note the
    fallback URI must use the `forward:` scheme; any other scheme fails at context load.

28. **Spring 6.1+ throws `NoResourceFoundException`, not `NoHandlerFoundException`, for an unmatched
    path.** The shared `GlobalExceptionHandler` mapped only the older exception, so an unknown path
    returned 500 instead of 404. Both are now mapped to the not-found handler. This is what makes the
    gateway's "unrouted path" behaviour a 404 rather than a 500.

29. **A repository adapter that maps a lazy entity outside an open session throws
    `LazyInitializationException`.** The catalog `ProductRepositoryAdapter` loaded a product and
    mapped its variant/image/attribute collections to the domain after the transaction had closed, so
    every read failed. Reads that touch lazy children must run inside a transaction — the adapter's
    read methods are `@Transactional(readOnly = true)`, and the mapping happens within that boundary.

30. **An aggregate that carries no version cannot use JPA `@Version` for optimistic locking unless the
    mapper restores it.** The mapper built a fresh entity at version 0 on every save, so the second
    edit of any product sent `where version = 0` against a row already at 1 and failed as a stale
    object. The adapter now reads the current version (`findVersionById`) and copies it onto the mapped
    entity before saving. Any other service that maps a domain aggregate to a versioned entity needs
    the same treatment.

31. **A nullable parameter compared with `is null` in JPQL can bind as `bytea` on PostgreSQL.** The
    catalog search used `(:search is null or lower(p.name) like ...)`, and PostgreSQL inferred the
    parameter's type from the `IS NULL` test as `bytea`, so `lower(bytea)` did not exist and every
    search failed. Build the `LIKE` pattern in the adapter (a literal `%` when there is no term) and
    drop the nullable parameter; escape `%` and `_` in the user's term with an explicit `escape`
    character so a search for `50%` matches literally.

32. **A `@PreAuthorize` denial is `AuthorizationDeniedException`, which a catch-all `Exception` handler
    reports as 500.** The refusal is a 403, but only if a handler for it is reached first.
    `@ControllerAdvice` resolution is ordered, first match wins, so the specific handler must outrank
    the fallback: `AccessDeniedExceptionHandler` (in `common-security`) maps the denial to 403, and the
    shared `GlobalExceptionHandler` — whose `Exception` handler matches everything — was lowered to
    `LOWEST_PRECEDENCE`. Two advices at the same precedence leave the outcome to bean order, which is
    how a permission refusal was reported as a server error.

33. **A route must be added to the gateway and the public-path list kept in step with the service's own
    endpoints.** `/api/v1/admin/catalog/**` existed in the catalog service but was never routed at the
    edge, so every admin catalogue call 404'd through the gateway while working when the service was
    called directly. The gateway's public paths also named `/api/v1/settings/public/**`, which no
    endpoint serves; the real public read is `/api/v1/settings`. `GatewayPropertiesTest` now asserts the
    admin prefix is never public and `RoutingIT` proves the admin route is proxied and gated.

34. **A status transition table is only as complete as its entries.** The order domain's
    `PREPARATION_STARTED → PREPARATION_ON_HOLD` edge was missing, so an order already being prepared
    could not be put on hold — exactly the case an ingredient shortage creates. The domain test caught
    it because it exercised the whole lifecycle rather than only the happy path. When adding a state,
    enumerate every edge into and out of it, not just the forward one.

35. **Check for a conflict before an idempotency shortcut, never after.** `Order.markPaid` returned
    early when the order was already paid, which swallowed a confirmation naming a *different* payment
    id instead of refusing it. The order is to test the inconsistent case first, then take the
    idempotent path: a duplicate of the same request is a replay, but a different request that lands in
    the same slot is corruption.

36. **A signature must be verified over the exact bytes the provider signed.** The webhook body is
    read as a `String` and passed to the verifier unchanged. Re-serialising the parsed JSON — even to
    the same fields — changes whitespace and key order, so the HMAC never matches and every webhook is
    rejected while the code looks correct. The checkout signature is over `order_id|payment_id` with
    the *account* secret; the webhook signature is over the raw body with the *webhook* secret. They
    are different secrets and swapping them fails silently as "signature invalid".

37. **A signature check proves origin, not settlement.** A valid checkout signature means the response
    came from the provider; it does not mean the money was captured. The payment is re-fetched from
    the provider afterwards and only a `captured` status for the expected amount is recorded as paid.
    The shared `applyReportedState` keeps the browser path and the webhook path from drifting — a
    test that marks a payment paid from the signature alone would pass and be wrong.

38. **A test that reuses the SDK's own signer cannot catch a wrong-string bug.** The gateway tests
    compute the expected HMAC independently with `javax.crypto`. If they called the SDK to produce the
    signature, a verification over the wrong fields or the wrong secret would still pass, and the bug
    would only appear against the real provider.

## Payment service conventions

The payment service follows the same five-module shape. Things specific to it:

- **A payment is confirmed by the server, never by the browser.** The checkout redirect only triggers
  a server-side verification: `verifyCheckoutSignature`, then `gateway.fetchPayment`, and only a
  captured payment for the expected amount becomes `PAID`. A redirect to a success page is not
  evidence. `PaymentService.applyReportedState` is shared by verification and the webhook so the two
  paths cannot drift.
- **The Razorpay SDK lives only in `RazorpayPaymentGateway`.** The domain and application layers
  depend on the `PaymentGatewayPort` interface, so the rules are testable against a fake and the
  provider is replaceable. A `CleanArchitectureTest` rule fails the build if `com.razorpay..` or
  `org.json..` appears anywhere outside `..payment.adapter.out.gateway..`.
- **Two separate secrets, two separate verifications.** The account secret verifies the checkout
  signature over `order_id|payment_id`; the webhook secret verifies the raw request body. The body is
  taken as a `String` and passed on unchanged — re-serialising it changes whitespace and key order and
  every signature fails. When no webhook secret is configured, every webhook is refused.
- **Idempotency is enforced by the database, not only by code.** `payments_order_unique` and
  `payments_provider_payment_unique` make a second payment for one order, or two payments sharing a
  provider payment id, impossible. A repeated capture is a no-op in the aggregate, so a retried
  webhook adds no second attempt.
- **Nothing sensitive is ever stored or logged.** The schema holds identifiers and a coarse
  `PaymentMethod` (UPI, CARD, NETBANKING) — never a card number, CVV, UPI PIN or bank credential. The
  key secret and webhook secret come from the environment, are never logged and never returned; only
  the publishable key id is exposed.
- **A misconfigured service fails closed.** With no credentials, `UnconfiguredPaymentGateway` still
  lets the service boot but refuses every money-moving call and never verifies a signature. There is
  no stub that pretends a payment succeeded.

## Order service conventions

The order service follows the same five-module shape as identity and catalog. Things specific to it:

- **Three status tracks, one status.** `PaymentStatus`, `PreparationStatus` and `DeliveryStatus` advance
  independently; `OrderStatus` is the projection a customer reads. Never collapse them — "paid but not
  started" must be representable.
- **`Order.transitionTo` is the only writer of the overall status.** Every lifecycle move goes through
  it, and it consults `OrderStatus.canTransitionTo`. A new admin or payment endpoint must call an
  aggregate method, never set the status directly, or the made-to-order rule can be bypassed.
- **An order is a snapshot.** Names, labels, SKUs and unit prices are copied at checkout and never
  re-read from the catalogue. A price change after the fact must not alter a placed order.
- **Payment state is a projection of a verified fact.** Nothing in this service decides a payment
  succeeded; a `payment.captured` event from the payment service does. A browser redirect is never
  trusted.
- **Money stays in paise.** `Money.inr(...)`, integer arithmetic, no `double` anywhere.
- **The order number comes from a database sequence**, never from a count or a timestamp, so two
  concurrent checkouts cannot be handed the same number.
- **The order service reads the catalogue, never writes it.** `GET /api/v1/catalog/variants?ids=…`
  is the one internal endpoint; it resolves published variants only, so a draft can never be priced
  into an order. Adding a cross-service read means adding an internal endpoint on the owning service,
  never a query against its database.
- **A mapper that rebuilds an entity must carry the current `@Version` onto it.** Building a fresh
  entity at version 0 makes the next update match no row, so optimistic locking silently stops
  working and a concurrent edit is lost instead of refused.

## Test tally

`mvn verify` at `backend/`: **780 tests, 0 failures, 0 errors, 0 skipped**. Counts move as work
lands; treat this table as a map of where the tests are, not a number to keep in sync by hand.

| Module | Tests |
|---|---|
| common-core | 40 |
| common-web | 7 |
| common-security | 23 |
| common-events | 6 |
| common-redis | 4 (integration, real Redis) |
| identity-domain | 52 |
| identity-application | 132 |
| identity-adapters-out | 6 |
| identity-adapters-in | 6 |
| identity-app | 40 (6 unit + 34 integration: real PostgreSQL, Redis, RabbitMQ, SMTP) |
| catalog-domain | 19 |
| catalog-application | 27 |
| catalog-app | 30 (4 unit + 26 integration: real PostgreSQL, Redis, RabbitMQ) |
| order-domain | 98 |
| order-application | 54 |
| order-adapters-out | 11 (real HTTP server) |
| order-app | 39 (integration: real PostgreSQL, Redis, RabbitMQ, in-process downstream stub) |
| payment-domain | 40 |
| payment-application | 31 |
| payment-adapters-out | 18 (real HMAC signature algorithms) |
| payment-app | 30 (5 architecture + 25 integration: real PostgreSQL, Redis, RabbitMQ, HMAC-verifying provider stub) |
| api-gateway | 40 (14 unit + 26 integration: real Redis) |
| **total** | **780** |

An earlier revision of this table claimed 446, counting the gateway's unit tests as 35. That number
was wrong. `GatewayPropertiesTest` has always held 14 `@Test` methods — 13 when it was first added
(`a69f8f2`) and 14 after the admin-route test landed — and no gateway test file was ever deleted in
the repository's history. The corrected total is 425, and a full `mvn clean verify` reports exactly
that with nothing skipped. Keep this number tied to a run, not to a hand edit.

### API Gateway tests (`*IT`, run by `mvn verify`)

`gateway` carries four integration classes under `src/test/java/com/choco/gateway/it`
(`AbstractGatewayIntegrationTest` starts real Redis via Testcontainers and serves the gateway on a
random port). Each downstream is a MockWebServer, so the tests assert what the gateway *forwarded*,
not only the status it returned.

- `RoutingIT` — a public request reaches its service; a protected one arrives with the token intact;
  a missing, forged, expired or wrong-issuer token is refused at the edge and never forwarded; an
  unknown path is not routed; the correlation id is preserved.
- `RateLimitIT` — a burst is throttled once the allowance is spent; auth paths use the tighter
  allowance; a throttled request never reaches the service; the auth and general buckets are counted
  independently.
- `CircuitBreakerIT` — repeated 5xx from a service opens the breaker and the gateway answers 503 with
  an RFC 9457 body instead of propagating the error.
- `GatewaySecurityIT` — registration and JWKS are public; the customer profile path is not; a 401
  carries a problem-detail body; an allowed-origin preflight is answered with that origin and an
  unknown origin is never wildcarded.

### Integration tests (`*IT`, run by `mvn verify`)

`identity-app` carries the full-stack tests under `src/test/java/com/choco/identity/it`. They start
real containers (`AbstractIdentityIntegrationTest`): PostgreSQL, Redis and RabbitMQ, plus a GreenMail
SMTP sink, and drive the service over HTTP on a reserved port. They exist to catch what unit tests
cannot — a migration that disagrees with its entity, a constraint the application layer does not
know about, a rate limiter that is not atomic, an event that never leaves the outbox.

- `SchemaAndPersistenceIT` — Flyway applied every migration, Hibernate `validate` passes, and the
  database-level unique, check and foreign-key constraints reject what the application layer would
  have caught only in a race.
- `CustomerJourneyIT` — register → verify email and mobile (codes read from the SMTP sink) → sign in
  → profile, contact change, addresses, refresh rotation, logout, rate limiting, ownership.
- `AdminJourneyIT` — the bootstrap administrator, admin token authority, and the boundary proving a
  customer credential and a customer refresh token are both refused at the admin endpoints.
- `OutboxRelayIT` — a real registration ends with `customer.registered` in a bound RabbitMQ queue
  and its outbox row marked published.

Two things these tests depend on, both deliberate:

- **The SMTP sink uses a fixed port (3025).** GreenMail restarts between classes while the Spring
  context is cached, so a random port would leave the second class sending to the first class's dead
  sink. The port must be free when the suite runs.
- **The access-token decoder fetches the service's own JWKS** over HTTP, so the base class reserves a
  free port up front and points both `server.port` and `choco.security.jwk-set-uri` at it. A token
  minted by the service is then verified by the same real decoder path production uses.

`payment-app` carries its own integration tests under `src/test/java/com/choco/payment/it`
(`AbstractPaymentIntegrationTest` starts real PostgreSQL, RabbitMQ and Redis, and serves the service
on a random port). The one thing stubbed is the provider, because Razorpay cannot be driven
deterministically from a test — but the stub is a real `PaymentGatewayPort` that computes **real HMAC
signatures**, so signature verification is exercised for real rather than bypassed. Authentication is
local: the test supplies its own RSA key and a matching `JwtDecoder`, and mints real RS256 tokens the
real filter chain verifies.

- `SchemaAndPersistenceIT` — Flyway applied V1, Hibernate `validate` passes, and the database-level
  constraints reject a second payment for one order, a shared provider order, a non-positive amount,
  a non-INR currency and an orphan refund.
- `PaymentJourneyIT` — the full payment journey over real HTTP: initiation and its idempotency, a
  captured payment with a valid signature, a forged signature refused, a customer unable to verify
  another's payment, signed/unsigned/duplicate/unknown webhooks, owner-scoped reads, and the seller's
  counters and refund.
- `CleanArchitectureTest` — the Razorpay SDK never leaves the gateway adapter; the domain and
  application layers depend on no framework, adapter or provider SDK.

## Local dev services

Redis, RabbitMQ and SMTP are needed for identity-service to report `UP`. RabbitMQ credentials come
from compose (`choco` / `choco_rabbit_pw`). A dev SMTP sink on port 1025 is enough for OTPs; there is
no provider selected for SMS yet, so codes are written to the log only while `CHOCO_LOG_OTP=true`
(equivalently `choco.notifications.log-otp`). The default is `false` in both the shipped config and
the `@Value` fallback, so a missing property fails closed; enabling it logs a startup warning.
