# Local Development

## 1. Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 21 | `java -version` must report 21 |
| Maven | 3.9+ | or use `./mvnw` once the wrapper is added |
| Node.js | 20+ | for the storefront and admin apps |
| Docker | 24+ | with Compose v2 |

## 2. First-time setup

```bash
# 1. Create your local environment file from the template.
cp .env.example .env
# Then edit .env and fill in anything you actually need. Razorpay keys can stay
# blank until Phase 6; the services start without them in the default profile.

# 2. Start the infrastructure (Postgres, Redis, RabbitMQ).
docker compose up -d postgres redis rabbitmq

# 3. Confirm all three are healthy.
docker compose ps
```

Expected: `choco-postgres`, `choco-redis` and `choco-rabbitmq` all report `(healthy)`.

### Verifying database isolation

Each service has its own database and role, and `CONNECT` is revoked from `PUBLIC`. To confirm:

```bash
# Must be refused.
docker exec choco-postgres psql -U choco_order -d choco_identity -c "SELECT 1"
# -> FATAL: permission denied for database "choco_identity"

# Must succeed.
docker exec choco-postgres psql -U choco_order -d choco_order -c "SELECT 1"
```

## 3. Building the backend

The shared modules must be installed before the services that depend on them will build:

```bash
cd backend

# Install the parent POM (once).
mvn -N install

# Install the shared modules, with tests.
for m in common-core common-web common-security common-events common-redis common-test; do
  mvn -f shared/$m/pom.xml install
done
```

Once every service module exists, a single `mvn verify` from `backend/` builds everything.

### Reading a verification code during local development

Verification codes are **not** written to the log by default. Registration still succeeds and the
OTP challenge is persisted; the code is simply delivered by mail (Mailpit, on port 1025) instead of
being printed.

To read codes from the log instead, start the service with the opt-in:

```bash
CHOCO_LOG_OTP=true mvn -f services/identity-service/identity-app/pom.xml spring-boot:run
```

Do this only on a development machine. Never set it in an environment a real customer can reach.

### Running one module's tests

```bash
mvn -f shared/common-security/pom.xml test
```

## 4. Infrastructure endpoints

| Service | URL | Credentials |
|---|---|---|
| RabbitMQ management | http://localhost:15672 | `choco` / `choco_rabbit_pw` |
| Prometheus | http://localhost:9090 | — |
| Grafana | http://localhost:3000 | `admin` / `choco_grafana_pw` |
| Loki | http://localhost:3100 | — |
| Tempo | http://localhost:3200 | — |

The observability stack is behind a profile so it does not consume memory during normal development:

```bash
docker compose --profile observability up -d
```

## 5. Test strategy

| Layer | Tooling | What it proves |
|---|---|---|
| Unit | JUnit 5 + AssertJ | domain invariants, hashing, policy, state machines |
| Integration | Testcontainers (real Postgres, real RabbitMQ) | JPA mappings, Flyway migrations, outbox relay, repositories |
| Web slice | `@WebMvcTest` + Spring Security test | status codes, validation, authorization rules |
| End-to-end | Compose stack + real HTTP | the full purchase journey |

Testcontainers is used rather than H2 because the services depend on PostgreSQL-specific behaviour:
`FOR UPDATE SKIP LOCKED`, partial indexes, `jsonb` and `TIMESTAMPTZ`. An H2-based test would pass
while production failed.

Mocks are avoided in favour of hand-written fakes (see `common-test/Fakes`) so tests assert on
outcomes rather than on call order.

## 6. Resetting everything

```bash
# Stop and delete all data volumes (databases, caches, message queues, dashboards).
docker compose --profile observability down -v
```

## 7. Common problems

| Symptom | Cause | Fix |
|---|---|---|
| Postgres container exits immediately | init script unreadable by the postgres user | `chmod 644 infra/postgres/init/*.sql` |
| Postgres logs `syntax error at or near "#"` | `#` used as a comment in SQL | use `--` |
| `Child module ... does not exist` | a module declared in the parent POM has no `pom.xml` | create it, or build with `-f <module>/pom.xml` |
| `Could not find artifact com.choco:choco-parent` | parent POM not installed | `mvn -N install` from `backend/` |
| `permission denied for database` in tests | expected; isolation is working | — |
