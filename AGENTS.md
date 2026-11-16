# Repository Notes for Agents

Single-seller, made-to-order chocolate e-commerce platform. See `docs/architecture.md` for the
design and `docs/development-progress.md` for the requirement-by-requirement status.

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

## Test tally

`mvn verify` at `backend/`: **248 tests, 0 failures, 0 errors**. Counts move as work lands; treat this
table as a rough map of where the tests are, not as a number to keep in sync by hand.

| Module | Tests |
|---|---|
| common-core | 40 |
| common-web | 7 |
| common-security | 23 |
| common-events | 6 |
| common-redis | 4 (integration, real Redis) |
| identity-domain | 44 |
| identity-application | 111 |
| identity-adapters-out | 6 |
| identity-adapters-in | 1 |
| identity-app | 6 |
| **total** | **248** |

## Local dev services

Redis, RabbitMQ and SMTP are needed for identity-service to report `UP`. RabbitMQ credentials come
from compose (`choco` / `choco_rabbit_pw`). A dev SMTP sink on port 1025 is enough for OTPs; there is
no provider selected for SMS yet, so codes are written to the log only while `CHOCO_LOG_OTP=true`
(equivalently `choco.notifications.log-otp`). The default is `false` in both the shipped config and
the `@Value` fallback, so a missing property fails closed; enabling it logs a startup warning.
