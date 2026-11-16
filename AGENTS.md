# Repository Notes for Agents

Single-seller, made-to-order chocolate e-commerce platform. See `docs/architecture.md` for the
design and `docs/development-progress.md` for the requirement-by-requirement status.

## Build commands

The parent POM declares every module, so `mvn` at `backend/` fails until all of them exist.
**Build modules individually:**

```bash
cd backend
mvn -N install                                   # install the parent POM first
mvn -f shared/common-core/pom.xml test           # one module's tests
mvn -f shared/common-core/pom.xml install        # shared modules must be installed before services build
```

## Layout

```
backend/pom.xml                    parent: pinned versions + enforcer + pluginManagement
backend/shared/common-core         framework-free domain primitives, error model, ports
backend/shared/common-web          RFC 9457 errors, correlation ids, OpenAPI, Jackson
backend/shared/common-security     Argon2id/BCrypt-15 hashing, RS256 JWT, RBAC
backend/shared/common-events       event envelope, transactional outbox, RabbitMQ topology
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

## Test tally

| Module | Tests |
|---|---|
| common-core | 26 |
| common-web | 7 |
| common-security | 23 |
| common-events | 6 |
| identity-domain | 26 |
| **total** | **88** |
