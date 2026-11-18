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
   - Progress: **Identity (done) → API Gateway (done) → Catalog (done) → Order Management (done) →
     Razorpay Payment (done, `707f193`) → Made-to-Order Production and Seller-Managed Delivery (done,
     the `fulfilment-service`) → Notification (done, the `notification-service`) → Invoice issuance
     and access (done, in the `order-service`) → Reporting and Customer Exports (done, the
     `reporting-service`) → Customer Storefront (done, `frontend/storefront`)**. The next module is
     the **Admin Dashboard** (Phase 13), then Docker Compose/Deployment/CI-CD (Phase 14) and the
     end-to-end verification and handover (Phase 15).
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

39. **An aggregate event that no use case drains is a subscription that can never fire.** The
    notification service bound `fulfilment.delivery-assigned` and rendered a template for it, but the
    fulfilment service's `Delivery.assign` saved the delivery and never called
    `pullDomainEvents()` — so the event stayed in the aggregate and the customer was never told. The
    binding, the template and the enum all looked correct; only the producer was missing. A domain
    test that asserts `peekDomainEvents()` proves the event is raised, but only an integration test
    that reads the broker proves it left the service. Both are needed: the domain test for the edge,
    the broker test for the drain.

40. **A subscription to an event with no producer is dead weight, not a safety net.** The
    notification service also bound `fulfilment.packaging-completed`; the packaging transition
    (`ProductionJob.startPackaging`) raises no event at all, so the binding could never deliver. It
    was removed rather than given a producer, because `ready-for-dispatch` already carries the
    customer-facing "your order is packed" fact. Before wiring a consumer to an event, grep for a
    `registerEvent`/outbox write that actually raises it.

41. **A payload field read by the wrong name fails silently into a fallback.** The notification
    listener read `reason` from `fulfilment.delivery-failed`, but the event carries `failureReason`;
    `orDefault` then supplied a generic "the delivery could not be completed" and the customer never
    learned why. The low-stock handler read `onHand`/`minimum` against `onHandMilliUnits`/
    `minimumMilliUnits`, and `Map.of` (which rejects nulls) turned the missing fields into a thrown
    `NullPointerException` that dead-lettered every alert. A consumer that reads JSON by string key
    has no compiler to catch a rename; the integration test must assert on the rendered value, not
    only that a message arrived.
42. **A seller-facing customer list is a different query from the customer's own read, and the search
    term must be escaped.** `CustomerRepository.search` builds the `LIKE` pattern in the adapter
    (`%term%`, empty term → `%`) and escapes `%`, `_` and the escape character itself, so a search for
    `50%` matches literally and an absent term is not a nullable parameter. Passing the raw term to a
    `LIKE` would let a customer's `%` match every account, and passing a nullable `:search` parameter
    would repeat the catalog bug where PostgreSQL infers `bytea` and `lower(bytea)` does not exist.
    The list also returns `CustomerSummaryView` — a record with no field for a hash, token or OTP — so
    a leak into the paged listing is a compile error rather than a review item.
43. **A read-only admin surface is authorised on the controller class, not on the URL pattern alone.**
    `AdminCustomerController` carries `@PreAuthorize("hasAuthority('customer:read')")` at the class
    level, so a new endpoint cannot be added to that class without inheriting an access decision, and
    the permission is granted to both `ADMIN` and `SUPER_ADMIN` in `AdminPermissions.forRole`. The
    gateway route and the public-path list are separate: `/api/v1/admin/customers/**` is routed at the
    edge but is deliberately never public, and `RoutingIT` proves both halves.

44. **Revoking a refresh token does not revoke the access token — nothing does, unless you make it.**
    A bearer access token is self-contained: once signed it is valid until it expires, so revoking the
    refresh-token row on logout stopped the session being *renewed* while the access token already in
    the client's hands kept working for the rest of its 15 minutes. For an administrator that token
    authorises refunds, catalogue edits and customer exports, so "logged out" was a lie. The fix names
    the session in the token (`jti` = refresh-token row id) and checks a revocation list in the
    decoder. Two shapes are needed because two security events differ: a **session** revocation
    (logout, rotation, replay) is presence-based, and a **subject cutoff** (password change/reset) is
    time-bounded so a fresh sign-in after the change still works. Three rules make it safe:
    - The check lives in the shared `JwtDecoder` (`common-security`), not only at the gateway, so a
      service enforces it when called directly; every service that depends on `common-redis` gets the
      registry and therefore the wrapper automatically.
    - It **fails closed**: if the store cannot be consulted the decoder refuses the token. Answering
      "not revoked" when the answer is unknown re-enables every session that was ever logged out.
    - A token with no `jti`, subject or issue time is refused rather than trusted — the identity
      service always sets them, so their absence means the token did not come from that path.
    Tests that mint tokens by hand (every service's `Abstract*IntegrationTest`, and the gateway's)
    must set a `jti`, or the decoder rejects them; that is why each one now calls `.id(...)`.
45. **A revocation list keyed on individual access tokens cannot retire a session.** Keying on the
    token would leave every *other* access token the session issued (a refresh in another tab, a token
    minted a moment earlier) valid. Key on the **session id** so revoking the session retires all of
    them at once; the token names it as `jti`. `RedisTokenRevocationRegistry` stores a presence key for
    a session and a cutoff value for a subject, each with a TTL equal to the access-token lifetime, so
    the store self-expires and never grows with the user count.

46. **A development credential default is one forgotten flag away from production.** The compose prod
    overlay requires every secret with `${VAR:?}`, but that guard lives in one file: a bare
    `java -jar`, a Kubernetes manifest or a forgotten `-f` bypasses it, and each service's
    `application.yml` then falls back to `choco_*_pw`. `ProductionConfigurationGuard` is the second
    line — a `prod`-profile listener that reads the *resolved* environment and refuses to boot on a
    missing, blank or default password. Two details make it correct: it is **scoped per capability**
    (it checks `spring.datasource.password` only when `spring.datasource.url` is set, so the gateway,
    which has no database, is not blocked), and it runs at `LOWEST_PRECEDENCE` so Spring Boot has
    already loaded `application.yml` into the environment. A listener registered in
    `META-INF/spring.factories` (not the `AutoConfiguration.imports` file) is what fires that early.
    `ProductionDefaultsDriftTest` reads every service `application.yml` and fails if a new
    `${...:choco_..._pw}` default is not in the guard's list, so the list cannot silently fall behind.
47. **Behind a TLS-terminating proxy, `request.isSecure()` is false and HSTS is never sent.**
    Spring Security's `HstsHeaderWriter` uses a `SecureRequestMatcher` that trusts `isSecure()` alone,
    so a proxy that terminates TLS and forwards plain HTTP to the app gets no
    `Strict-Transport-Security` at all — the header is configured, looks right, and does nothing. The
    usual remedy, `server.forward-headers-strategy=framework`, is the wrong one here: it rewrites
    `remoteAddr` from the client-supplied `X-Forwarded-For`, which defeats `ClientIpResolver`'s rule
    (trust the forwarded address only when the immediate peer is a configured proxy) and re-opens the
    rate-limiter bypass. `TrustedProxyHttpsMatcher` reads `X-Forwarded-Proto` only from a peer in
    `choco.http.trusted-proxies` — the same property `ClientIpResolver` uses, so the scheme rule and
    the address rule cannot drift — and is the HSTS request matcher for both the shared chain and the
    gateway. An untrusted client can neither force HSTS on (a false positive pins a scheme the host
    may not serve) nor suppress it.

48. **A path public at the gateway and protected at the service is a 401 that looks like a bug in
    something else.** The gateway's `GatewayProperties.publicPaths` listed `/api/v1/serviceability/**`
    so an anonymous visitor could check delivery coverage, and the storefront's "Delivery areas" page
    is deliberately outside `ProtectedRoute` — but the fulfilment service's own `public-paths` was
    empty, so it answered 401. Every other public path worked, so the failure read as a broken page
    rather than an authorisation mismatch, and `RoutingIT` did not catch it because its downstream is
    a MockWebServer that never enforces auth. The edge's public list is a *forwarding* decision, not
    an authorisation one: the owning service still decides, and the two lists must agree. A path is
    public at both ends or at neither. `FulfilmentJourneyIT.anonymousIsAnswered` pins the service end
    (it fails with 401 if the fulfilment `public-paths` is emptied), and the companion
    `anonymousCannotReachAdmin` proves the change did not open the administrative surface.

49. **A fake that throws the exact exception the code catches cannot prove the catch is right.** The
    provider-failure path was covered only by a unit test whose fake `MessageSender` throws
    `MessageDeliveryException` directly — the very type `NotificationDispatchService.attempt` catches.
    That passes whether or not the real `SmtpEmailSender` actually surfaces that type: a fake throws
    whatever the test tells it to. `NotificationJourneyIT.providerOutageIsRecordedNotThrown` stops the
    real GreenMail sink instead, so a real `MailException` travels through the real
    `ChannelMessageSender`, and the test fails if the production wrap is ever removed. When the thing
    under test is *which exception a transport raises*, drive a real failure, not a fake one.

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
- **The payment outcome arrives as an event, and only as an event.** `PaymentEventsListener` on the
  order service's own durable queue (`choco.order-service`, bound to `payment.captured`, `payment.failed`,
  `payment.pending` and `payment.refund-processed`) is the sole path by which an order learns a payment
  succeeded, failed or was refunded. There is deliberately **no HTTP endpoint** that marks an order paid:
  a browser must never be able to confirm its own payment. When this consumer was missing the whole
  saga was dead — an order could be paid and stay `PAYMENT_PENDING` forever, and the fulfilment service
  never received `order.confirmed`. Do not reintroduce a synchronous "mark paid" endpoint.
- **A capture must be for the order's exact total, or it is refused.** `Order.markPaid` takes the amount
  the provider collected and compares it to the order's frozen `total()`, throwing
  `PAYMENT_AMOUNT_MISMATCH` on any difference. This is not belt-and-braces: the initiate endpoint takes
  a caller-supplied amount, so without this check a ₹1 payment would confirm a ₹5,000 order. The check
  runs *before* the idempotency shortcut, so a wrong-amount capture is refused even on a redelivery.
  Any new path that confirms an order must pass the collected amount through.
- **Every listener queue must reject, not requeue, on repeated failure.** `default-requeue-rejected: false`
  with a bounded retry is set on both `order-service` and `fulfilment-service`. Without it a message that
  always fails is redelivered in a tight loop forever, pegging a consumer and hiding the fault; with it
  the message is dead-lettered where an operator can see and replay it.
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
- **An invoice is a record, not a workflow.** `Invoice` (in the order service, since it is a statement
  about an order) has no update methods at all — a correction is a credit note, never a rewrite. It is
  issued exactly once per order from a verified `payment.captured`, idempotent by order id, with the
  database's `invoices_order_unique` index as the last line so a race cannot produce two documents.
  Everything it shows — customer, billing address, priced lines — is a frozen snapshot of the order and
  is never re-read. The invoice number comes from the `invoice_number_seq` sequence, never a count.
- **Ownership is part of the invoice query.** `findByIdAndCustomerId` has no unscoped twin, and
  `ownInvoiceForOrder` filters by customer, so a cross-customer read by id or by order is a `404`, not
  the document. `/api/v1/admin/invoices/**` needs an administrator role; issuing by hand needs
  `order:write`. The `order.invoice-issued` event carries the invoice number and the billed total so the
  notification service can email the customer without calling back.

## Fulfilment service conventions

The fulfilment service (port 8085, database `choco_fulfilment`) owns made-to-order production and
seller-managed delivery in the same five-module shape as the others. Things specific to it:

- **A job is opened only by `order.confirmed`.** There is no REST endpoint that creates a job and no
  other event bound to the queue, so "nothing is made before a payment is verified" is enforced by the
  shape of the system, not by a check someone could forget. Do not add a "create job" endpoint.
- **Consumption is idempotent by key.** A redelivered `order.confirmed` finds the jobs it already
  opened (order id + variant id) and opens nothing new; a redelivered `order.cancelled` finds the jobs
  already cancelled. A broker retry must never double the kitchen's work or return ingredients twice.
- **Ingredient tracking is optional by design.** A job's preparation status advances whether or not
  any ingredient exists, and consumption is only attempted when a recipe line exists for the variant.
  A variant with no recipe consumes nothing — never assume a recipe.
- **The production and delivery status machines are explicit tables.** `ProductionStatus.canTransitionTo`
  and `DeliveryStatus.canTransitionTo` encode the real edges; an ordinal comparison would let the
  seller skip making the chocolate or deliver before dispatch. Never set a status field directly.
- **The delivery is opened when the job reaches `READY_FOR_DISPATCH`,** against the address frozen in
  the confirmed event. Delivery is seller-managed: there is no courier SDK, no tracking number and no
  GPS position anywhere in the model, and none may be fabricated.
- **A `ServiceableArea` is an exact PIN or a prefix; longest prefix wins.** A zero charge is a real
  value ("free delivery here"), not "unset". A quote for an uncovered PIN is a reasoned answer, not an
  error.
- **Money stays in paise and stock stays in milli-units.** `Money.inr(...)` and `StockLevel` (three
  decimal places, integer arithmetic) — no `double` anywhere, because a recipe that subtracts 0.1 g a
  hundred times must not drift.
- **A mapper that rebuilds an entity must carry the current `@Version` onto it,** exactly as in the
  other services, or optimistic locking silently stops working.

## Notification service conventions

The notification service (port 8086, database `choco_notification`) owns email and SMS fan-out. Things
specific to it:

- **A notification is an aggregate, not a log line.** The business needs to answer "was this order
  confirmation ever sent, and how many times was it tried?" after the fact; a log line is rotated
  away, a row survives.
- **Deduplication is part of the identity.** Every notification carries a `dedupe_key` derived from
  the producing event id and type, and the database enforces it unique. A broker redelivery — normal,
  not exceptional — therefore cannot send a customer a second order confirmation.
- **The message is rendered before it is stored.** The subject and body are the finished text, so a
  retry re-sends exactly what the customer would have received, and a later template change cannot
  rewrite the history of what was sent.
- **`FAILED` is a resting state, not a transient one.** A failed attempt stays `FAILED` — visible in
  the seller's log and resendable — and the retry query selects both `PENDING` and `FAILED` due rows.
  A re-failed retry does not re-run the `→ PENDING` transition (a guarded self-transition). Once the
  attempt budget is spent the notification is `DEAD_LETTER`, and the only way out is an administrator
  resend, which resets the attempt counter so the message gets a full budget rather than immediately
  dead-lettering again.
- **A provider failure is never a business failure.** The consumer records the failure and returns;
  nothing in the path writes to order or payment state, so a mail outage cannot un-pay an order. The
  stored error is a bounded, provider-neutral summary — never a raw transport error that echoes the
  recipient's address.
- **The admin log is `/api/v1/admin/notifications`,** gated by `notification:read` /
  `notification:write` and routed by the gateway. The public path list never opens it.

## Reporting service conventions

The reporting service (port 8087, database `choco_reporting`) is a **pure consumer**: it owns no
aggregate, accepts no command and publishes no event. Every table is a derived read model that can be
rebuilt from the event log. Things specific to it:

- **A projection is upserted by the producer's own identifier, which makes every handler idempotent by
  construction.** `order_projection` is keyed on the order id, `payment_projection` on the payment id,
  `production_projection` on the job id. A redelivered event overwrites the row with the same values
  instead of adding a second, so at-least-once delivery needs no separate dedupe table — there are no
  append-only rows to duplicate. `ReportingJourneyIT` proves a redelivered `order.placed` leaves one row.
- **Copy the producer's status verbatim; never invent a reporting-local name for it.** The fulfilment
  service sends `PREPARATION_STARTED`, `ATTEMPT_FAILED` and its other status names. A projection that
  translates them into its own vocabulary is a second place for the two services to disagree, and the
  disagreement is silent: the column is written with a value the reports do not recognise while the
  unit test — which uses the same invented name — stays green. This exact bug was hit and fixed.
- **The event-type→use-case map lives in the inbound adapter.** `PlatformEventsListener` is the only
  class that knows the wire names; `ProjectionService` takes a plain `Map<String, Object>`, so every
  projection rule is a unit test with no broker and no database.
- **One refund, one row.** A refund request and its processed counterpart share the key
  `refund-<orderId>`, so a refund is not counted once as pending and again as paid.
- **A report has a ceiling and says so.** `ReportLimits.MAX_REPORT_ROWS` refuses a window that fills the
  cap rather than truncating and presenting a partial total as if it were the whole.
- **An export declares its columns.** Each `ExportType` names the columns it may emit and the dataset
  is built from that list alone, so a query returning more cannot leak more. The customer export masks
  the email and mobile and needs `customer:export` on top of `report:read`. Every export writes an
  audit row (actor, dataset, format, row count) — never the exported content. The download is an
  attachment with `Cache-Control: no-store`.
- **CSV is neutralised against formula injection.** A cell beginning with `=`, `+`, `-`, `@`, tab or
  carriage return is prefixed with `'`, because a spreadsheet opens such a cell as a formula.
- **The spreadsheet library (`org.apache.poi`) never leaves `..reporting.adapter.out.export..`.**
  `CleanArchitectureTest` fails the build if it appears anywhere else.
- **The surface is never public.** `/api/v1/reports/**`, `/api/v1/exports/**` and
  `/api/v1/dashboard/**` are routed by the gateway and never in its public-path list; the service's own
  method security requires `report:read` on every endpoint, so the edge authenticates and the service
  authorises. `GatewayPropertiesTest` and `RoutingIT` both pin it.

## Storefront conventions

The customer website lives in `frontend/storefront` (React + Vite + TypeScript, Vitest + Testing
Library). It is a presentation client only. Things specific to it:

- **The browser never decides anything that matters.** It does not decide a payment succeeded, that a
  PIN is serviceable, that an order is cancellable, or that a customer may check out. Each of those is
  a server answer the app renders. The Razorpay handler's response is POSTed to the verify endpoint,
  which checks the signature and re-fetches the payment; the storefront shows whatever the server
  records, and a `null` public key is shown as "online payment is not configured", never as success.
- **The wire layer is one file per concern.** `src/api/types.ts` mirrors the backend DTOs, `client.ts`
  owns the token, the refresh and RFC 9457 problem mapping, and `endpoints.ts` groups calls by
  resource. A page imports an endpoint function, not `fetch`.
- **One refresh is shared across concurrent 401s.** The refresh token rotates, so two requests that
  both see a 401 must not both spend it. `client.ts` keeps a single in-flight refresh promise; a test
  proves two simultaneous 401s produce one refresh call. A refused refresh clears the session and
  notifies listeners rather than looping.
- **An empty 2xx body is a success, not an error.** Resend and password-reset request answer `202`
  with no body; the client reads the text once and only parses when there is something to parse.
- **Money is rendered from the server's own string when it is present.** `money(minorUnits, formatted)`
  prefers the server-formatted value so a receipt cannot disagree with the invoice; the local formatter
  is only a fallback.
- **An idempotency key is generated per checkout attempt,** so a double-tap cannot create two orders.
- **The dev proxy and the production proxy both point at the gateway.** Vite proxies `/api` to
  `CHOCO_GATEWAY_URL` in development; nginx proxies `/api` to the `gateway` service in production, so
  the browser sees one origin and no CORS preflight is needed.
- **A test stubs `fetch`, nothing the app owns.** Settings are cached per tab, so the test setup resets
  that cache between cases (`resetSettingsCache`) rather than letting one test's stub leak into the
  next. A page that reads a path parameter is rendered through a real `<Route>` (`renderAtRoute`), not
  just a history entry, or `useParams` is empty.

## ACID Compliance and Distributed Transaction Consistency

This section is permanent and must stay current as modules land.

**Local ACID, per service database.** Every service owns one PostgreSQL database and never writes
another's. Business invariants are enforced by the schema (primary keys, foreign keys, unique, check
and not-null constraints) *and* by the domain — the database is the last line, not the only one.
Optimistic locking (`@Version`) guards concurrent edits; a mapper that rebuilds an entity must carry
the current version across or locking silently stops working (a bug already hit in catalog). Reads
that touch lazy collections run in a read-only transaction. Money is integer paise; stock is
milli-units; no `double` arithmetic can drift.

**Distributed consistency across services.** Independent databases cannot share one ACID transaction,
so consistency is eventual and is built from:

- **Transactional outbox.** A producer writes the event to an `outbox_event` row *inside the same
  transaction* as the state change; a relay publishes it and marks it sent. This removes the dual-write
  window where the commit fails but the message is already gone. The relay query uses
  `FOR UPDATE SKIP LOCKED`, safe on several replicas.
- **Idempotent consumers keyed by event id.** A redelivered event finds the work it already did and
  does nothing new — one production job per line, one delivery per order, one notification per dedupe
  key.
- **Durable queues, bounded retry, dead-letter.** One durable topic exchange; each consumer has a
  durable queue, a retry queue with TTL and a shared dead-letter queue. Bounded retry (3 attempts,
  1s→2s backoff, 10s cap) then `default-requeue-rejected: false`, so a poison message dead-letters
  instead of looping.
- **The order→payment→production saga is closed by events, not HTTP.** The order service freezes the
  total and asks payment-service to collect exactly that amount; payment-service verifies a capture
  against the amount it was asked to collect; the order service consumes `payment.captured` and moves
  the order's state from the event alone, refusing a capture whose amount is not the order's frozen
  total (`PAYMENT_AMOUNT_MISMATCH`); fulfilment opens a job from `order.confirmed`. There is
  deliberately no synchronous "mark paid" endpoint.
- **Never hold a transaction open across a network call.**

**Evidence.** Commit/rollback and constraint violations are proven by every `SchemaAndPersistenceIT`;
optimistic locking by the mapper-version tests; concurrent processing by `OrderPaymentIT` (two
simultaneous payment starts) and `FulfilmentJourneyIT` (idempotent consumption over a real broker);
outbox atomicity and publication by `OutboxRelayIT`; duplicate webhook and verify idempotency by
`PaymentJourneyIT`; provider-failure isolation by `NotificationJourneyIT`; read-model idempotency and
the projection's transaction boundary by `ReportingJourneyIT` (a redelivered event leaves one row, a
refund request and its processing are one row). Token revocation — the session/access-token
consistency boundary — is proven by `RevocationAwareJwtDecoderTest` (unit) and
`RedisTokenRevocationRegistryIT` (real Redis: presence session key, time-bounded subject cutoff, a
later cutoff superseding an earlier one, an expired window writing nothing). Backups and recovery are
documented with deployment (Phase 14) and are not yet verified end to end.

**Known limitation.** Revocation is *eventual across services only in the sense that every service
reads the same Redis*: there is no per-request round trip to the identity service, so a service that
cannot reach Redis refuses the token (fail-closed) rather than falling back to signature-only. That is
the intended trade-off — an outage denies access instead of silently accepting revoked sessions — but
it does make Redis a hard dependency of the authenticated request path, which is recorded as a
durability/availability assumption to confirm with deployment (Phase 14).

## Security review

`docs/security-review.md` holds the security architecture, the findings by severity with evidence, the
fixes, the remaining risks and the maintenance procedure. It is a living document and is updated as
each module lands and as the deployment configuration is built.

## Access-token revocation

The identity service mints access tokens with the session id as their `jti` (the refresh-token row
id). Every service that depends on `common-redis` gets a `TokenRevocationRegistry` bean, and
`common-security`'s `jwtDecoder` bean wraps the Nimbus decoder in `RevocationAwareJwtDecoder` when one
is present, so a revoked token is refused at the service even when it is called directly rather than
through the gateway.

- **Revoked on:** logout, refresh-token rotation, replay detection (session scope) and password change
  or reset (subject scope, "no token issued before this moment").
- **Store:** Redis. `revoked:session:<id>` is a presence key; `revoked:subject:<id>` holds a cutoff
  epoch-millis. Each entry carries a TTL equal to the access-token lifetime, so the list self-expires.
- **Fail-closed:** if Redis cannot be reached, the decoder refuses the token. A token with no `jti`,
  subject or issue time is refused.
- **Not stored:** the revocation list holds identifiers and a cutoff, never a token — a copy of it
  yields no usable credential.

A test that mints a token by hand must set `jti` (`.id(...)` on `JwtClaimsSet.builder()`), or the
decoder rejects it. All service `Abstract*IntegrationTest` bases and the gateway base do this.

## Test tally

`mvn clean verify` at `backend/`: **1271 tests, 0 failures, 0 errors, 0 skipped**. Counts move as work
lands; treat this table as a map of where the tests are, not a number to keep in sync by hand.

| Module | Tests |
|---|---|
| common-core | 40 |
| common-web | 7 |
| common-security | 48 |
| common-events | 6 |
| common-redis | 9 (integration, real Redis: rate limiter + token revocation) |
| identity-domain | 56 |
| identity-application | 150 |
| identity-adapters-out | 6 |
| identity-adapters-in | 6 |
| identity-app | 48 (6 unit + 42 integration: real PostgreSQL, Redis, RabbitMQ, SMTP) |
| catalog-domain | 19 |
| catalog-application | 27 |
| catalog-app | 30 (4 unit + 26 integration: real PostgreSQL, Redis, RabbitMQ) |
| order-domain | 117 |
| order-application | 78 |
| order-adapters-out | 14 (real HTTP server) |
| order-adapters-in | 9 |
| order-app | 79 (4 architecture + 75 integration: real PostgreSQL, Redis, RabbitMQ, in-process downstream stub) |
| payment-domain | 40 |
| payment-application | 31 |
| payment-adapters-out | 18 (real HMAC signature algorithms) |
| payment-app | 30 (5 architecture + 25 integration: real PostgreSQL, Redis, RabbitMQ, HMAC-verifying provider stub) |
| gateway | 67 (16 unit + 51 integration: real Redis) |
| fulfilment-domain | 90 |
| fulfilment-application | 37 |
| fulfilment-app | 31 (4 architecture + 27 integration: real PostgreSQL, Redis, RabbitMQ) |
| notification-domain | 13 |
| notification-application | 35 |
| notification-app | 29 (4 architecture + 25 integration: real PostgreSQL, Redis, RabbitMQ, GreenMail SMTP sink) |
| reporting-domain | 20 |
| reporting-application | 42 |
| reporting-adapters-out | 10 (CSV/XLSX writers, real spreadsheet library) |
| reporting-app | 29 (5 architecture + 24 integration: real PostgreSQL, Redis, RabbitMQ) |
| **total** | **1271** |

**Tally from each module's `Results:` block, not by summing report files.** A nested `@Nested` class
writes its own report file with `Tests run: 0` at the outer level, so a per-file sum silently
undercounts; the module-level `Results:` line is the authoritative figure. An earlier revision of this
table claimed 907, which was such a sum.

An earlier revision of this table claimed 446, counting the gateway's unit tests as 35. That number
was wrong. `GatewayPropertiesTest` has always held 14 `@Test` methods — 13 when it was first added
(`a69f8f2`), 14 after the admin-route test landed, and 15 after the admin-refresh test — and no
gateway test file was ever deleted in the repository's history. Keep this number tied to a run, not
to a hand edit.

### API Gateway tests (`*IT`, run by `mvn verify`)

`gateway` carries six integration classes under `src/test/java/com/choco/gateway/it`
(`AbstractGatewayIntegrationTest` starts real Redis via Testcontainers and serves the gateway on a
random port). Each downstream is a MockWebServer, so the tests assert what the gateway *forwarded*,
not only the status it returned.

- `RoutingIT` — a public request reaches its service; a protected one arrives with the token intact;
  a missing, forged, expired or wrong-issuer token is refused at the edge and never forwarded; an
  unknown path is not routed; the correlation id is preserved; a public serviceability lookup and a
  customer's own fulfilment progress reach the fulfilment service, while the administrative kitchen
  surface is refused without a token.
- `RateLimitIT` — a burst is throttled once the allowance is spent; auth paths use the tighter
  allowance; a throttled request never reaches the service; the auth and general buckets are counted
  independently.
- `CircuitBreakerIT` — repeated 5xx from a service opens the breaker and the gateway answers 503 with
  an RFC 9457 body instead of propagating the error.
- `GatewaySecurityIT` — registration and JWKS are public; the customer profile path is not; a 401
  carries a problem-detail body; an allowed-origin preflight is answered with that origin and an
  unknown origin is never wildcarded.
- `GatewayHstsIT` — a request over plain HTTP gets no `Strict-Transport-Security`; a trusted proxy's
  `X-Forwarded-Proto: https` does; a forged one from an untrusted peer does not.
- `GatewayRevocationIT` — the edge consults the revocation store: a live token is proxied, a revoked
  session is a 401 that never reaches the downstream, a logged-out administrator cannot reach
  `/api/v1/admin/notifications`, revocation is scoped to one session, and a subject cutoff refuses only
  tokens issued before it.

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

`fulfilment-app` carries its own integration tests under `src/test/java/com/choco/fulfilment/it`
(`AbstractFulfilmentIntegrationTest` starts real PostgreSQL, RabbitMQ and Redis, and serves the
service on a random port). The hand-over is exercised through the real broker: a test publishes the
`order.confirmed` event the order service raises and waits for the consumer to open the job, so
idempotent consumption is proven end to end rather than assumed. Authentication is local: the test
supplies its own RSA key and a matching `JwtDecoder`, and mints real RS256 tokens the real filter
chain verifies.

- `SchemaAndPersistenceIT` — Flyway applied V1, Hibernate `validate` passes, and the database-level
  constraints reject a non-positive quantity, a negative stock level, a negative delivery charge, an
  out-of-range status and a second delivery for one order.
- `FulfilmentJourneyIT` — the made-to-order hand-over over a real broker and real HTTP: a confirmed
  order opens one job per line, a redelivery opens none, the seller walks a job to ready for dispatch
  (which opens exactly one delivery at the frozen address), an illegal jump is refused, a cancelled
  order abandons its jobs, a serviceability quote answers a covered PIN with its charge and an
  uncovered one with a reason **and answers an anonymous visitor too** (the gateway lists the path as
  public and the storefront's "Delivery areas" page is unauthenticated, so the service must not keep
  it protected), an anonymous visitor still cannot reach the administrative kitchen, the
  customer/admin boundary holds, and the delivery-assigned and delivery-rescheduled events actually
  reach the broker (a throwaway queue is bound and the envelope read, so a use case that saves but
  never drains its events fails the test rather than passing on the HTTP 200 alone).
- `CleanArchitectureTest` — the domain and application layers depend on no framework, adapter or
  persistence technology; inbound adapters do not reach into persistence.

`notification-app` carries its own integration tests under `src/test/java/com/choco/notification/it`
(`AbstractNotificationIntegrationTest` starts real PostgreSQL, RabbitMQ and Redis, serves the service
on a random port, and points the mail sender at a GreenMail SMTP sink on fixed port 3025). Events are
published as real envelopes to the exchange the listener binds, so the consumer path is exercised end
to end.

- `SchemaAndPersistenceIT` — Flyway applied V1, Hibernate `validate` passes, and the database-level
  constraints reject a negative attempt count, a blank dedupe key, an out-of-range status and a
  duplicate dedupe key (the idempotency guard).
- `NotificationJourneyIT` — a real event on the broker becomes one message in the SMTP sink: the
  greeting is filled from the contact projection, the dedupe key suppresses a redelivery, a provider
  outage is recorded on the notification rather than thrown, a low-stock alert renders the real level
  and threshold, an assigned delivery reaches the customer, a failed delivery carries the reason the
  event gave (not the generic fallback a wrong field name would produce), a rescheduled delivery tells
  the customer a new attempt is planned, a second reschedule of one delivery is not swallowed by the
  dedupe key (keyed on the event id, not the delivery id) while a redelivery of the same event still
  is, and an `order.invoice-issued` emails the customer the invoice number and the billed total.
- `AdminNotificationApiIT` — the admin log over real HTTP: list, counters and read are permission
  gated, a customer is refused, an unknown id is a 404, a delivered notification cannot be resent
  (409) and a customer cannot trigger a resend (403). A failed notification (produced by stopping the
  real SMTP sink) is resent successfully — the response is `SENT`, the message reaches the sink, and the
  resend writes an audit row naming the administrator, which the test reads straight from `audit_log`
  rather than trusting that the call happened.
- `CleanArchitectureTest` — the domain and application layers depend on no framework, adapter or
  provider SDK; the mail/SMS sender never leaks into the domain.

**The fulfilment service raises the delivery events the notification service consumes.**
`Delivery.assign` drains the aggregate's events on the first `PENDING → ASSIGNED` edge, so
`fulfilment.delivery-assigned` actually reaches the broker (a later re-assignment raises nothing — the
delivery person is the seller's own staff). `Delivery.reschedule` raises `fulfilment.delivery-rescheduled`
on every retry: without it a reschedule moves the status but tells the customer nothing, so the person
who asked for another attempt hears nothing. `FulfilmentJourneyIT.assigningPublishesAnEvent` and
`FulfilmentJourneyIT.reschedulingPublishesAnEvent` prove both end to end by binding a throwaway queue to
the exchange and reading the envelope. There is no `fulfilment.packaging-completed` event: the packaging
transition raises nothing, so no consumer binds to it.

**Delivery notifications are keyed on the event id, not the delivery id.** `deliveryEvent` derives the
dedupe key from the payload's `eventId` where it is carried (falling back to `deliveryId` for an older
payload). Keying on the delivery id alone collapses every step of one type on one delivery into a single
message, so a second failed attempt or a later reschedule would be swallowed and the customer would never
learn of the retry; the event id is stable across a broker redelivery, which is exactly the idempotency
the dedupe needs. `NotificationJourneyIT.aSecondRescheduleIsNotSwallowedByTheDedupeKey` pins it.

**`FAILED` is a resting status.** A notification that exhausts its retry budget stays `FAILED` rather
than falling back to `PENDING`, so a failure is visible in the admin log and resendable. The retry
query selects both `PENDING` and `FAILED` due rows, and a re-failed notification does not re-run the
`→ PENDING` transition (guarded self-transition). The admin log lives at `/api/v1/admin/notifications`
and is routed by the gateway; its authorities are `notification:read` / `notification:write`.

## Local dev services

Redis, RabbitMQ and SMTP are needed for identity-service to report `UP`. RabbitMQ credentials come
from compose (`choco` / `choco_rabbit_pw`). A dev SMTP sink on port 1025 is enough for OTPs; there is
no provider selected for SMS yet, so codes are written to the log only while `CHOCO_LOG_OTP=true`
(equivalently `choco.notifications.log-otp`). The default is `false` in both the shipped config and
the `@Value` fallback, so a missing property fails closed; enabling it logs a startup warning.

## The end-to-end harness

`scripts/e2e-journey.py` drives the whole customer→seller journey against a running stack over the
gateway: registration and both OTP channels, sign-in, an address, the public catalogue, a
serviceability quote, checkout, a verified `payment.captured` capture, the kitchen job, preparation,
dispatch, delivery, the notification fan-out, the invoice and the seller's dashboard and export. It is
the only check that exercises every service together; the per-service `*IT` suites each prove one seam
in isolation.

Run it against a stack that is already up:

```bash
docker compose -f docker-compose.yml -f docker-compose.app.yml up -d
E2E_ADMIN_EMAIL=... E2E_ADMIN_PASSWORD=... python3 scripts/e2e-journey.py
```

It reads the mobile OTP from the identity log (so start the container with `CHOCO_LOG_OTP=true`) and
the email OTP from Mailpit on port 8025. Without `E2E_ADMIN_EMAIL`/`E2E_ADMIN_PASSWORD` it runs the
customer journey and skips the seller stages. **It seeds its own fixtures:** when the catalogue is
empty it creates and publishes a product through the seller API, and it covers the delivery PIN code
before quoting, so it runs unchanged against a fresh volume. Do not make it depend on hand-seeded
data — CI starts from an empty volume, and `.github/workflows/e2e.yml` runs this harness there.
