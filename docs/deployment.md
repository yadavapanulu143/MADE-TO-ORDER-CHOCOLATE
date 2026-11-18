# Deployment

How to run the whole platform: the infrastructure, the eight backend processes, and the two
frontends. There are two supported shapes — a local stack that builds from source, and a
production-shaped stack where every credential comes from the environment and nothing is published
except the gateway and the two frontends.

---

## 1. What runs

| Component | Container | Host port | Notes |
|---|---|---|---|
| API gateway | `choco-gateway` | 8080 | The only backend port published. Everything a browser calls goes through here. |
| Identity | `choco-identity` | — | Signing key, OTPs, customers, administrators. |
| Catalog | `choco-catalog` | — | Products, variants, images, settings. |
| Order | `choco-order` | — | Cart, checkout, orders, invoices. |
| Payment | `choco-payment` | — | Razorpay integration, webhooks. |
| Fulfilment | `choco-fulfilment` | — | Production jobs and seller-managed delivery. |
| Notification | `choco-notification` | — | Email and SMS fan-out, admin log. |
| Reporting | `choco-reporting` | — | Reports, exports, dashboard counters. |
| Storefront | `choco-storefront` | 5173 | React build served by nginx. |
| Admin dashboard | `choco-admin` | 5174 | React build served by nginx. |
| PostgreSQL | `choco-postgres` | 5432 | One database and one role per service. |
| Redis | `choco-redis` | 6379 | Rate limits, refresh-token state, OTP counters, access-token revocation list. |
| RabbitMQ | `choco-rabbitmq` | 5672 / 15672 | One topic exchange, per-service durable queues. |
| Mailpit | `choco-mailpit` | 1025 / 8025 | Local SMTP sink and web UI, development only. |

Only the gateway and the two frontends publish a host port on purpose. The services are reachable on
the compose network by name, so a service cannot be reached around the gateway's edge
authentication.

---

## 2. Local stack (builds from source)

```bash
# 1. Signing key for the identity service. The script refuses to overwrite an existing key.
./scripts/generate-jwt-keys.sh

# 2. Optional: copy the environment template if you need to override any default.
cp .env.example .env

# 3. Build and start everything.
docker compose -f docker-compose.yml -f docker-compose.app.yml up -d --build

# 4. Watch it come up.
docker compose -f docker-compose.yml -f docker-compose.app.yml ps
```

The stack is healthy when every row reads `(healthy)`. Then:

- Storefront: <http://localhost:5173>
- Admin dashboard: <http://localhost:5174>
- Gateway health: <http://localhost:8080/actuator/health>
- Mailpit (read the OTPs): <http://localhost:8025>

To add the observability profile (Prometheus, Grafana, Loki, Tempo and the OTLP collector), start it
too and turn trace export on:

```bash
CHOCO_OTLP_ENABLED=true docker compose --profile observability \
  -f docker-compose.yml -f docker-compose.app.yml up -d
```

Grafana is at <http://localhost:3000> (`admin` / `choco_grafana_pw` by default).

### The signing key

The identity service signs access tokens with an RSA key. `scripts/generate-jwt-keys.sh` writes
`secrets/jwt-private.pem` — a single PEM holding **both** the private key and the matching public key,
because the loader reads the two blocks from one file — and `secrets/jwt-public.pem`. Both are
git-ignored.

The private key is mounted as a compose secret, which is a bind mount: the file keeps the host's
ownership and mode. The service image's entrypoint therefore starts as root just long enough to copy
the key to a `0400` file owned by the unprivileged `choco` user, then drops to that user before the
JVM starts. Without that step a `0600` key owned by the deploying host user is unreadable inside the
container and the service logs *"set but not readable"* and silently falls back to an ephemeral key,
which invalidates every token on restart.

---

## 3. Production-shaped stack

The production override makes every credential mandatory and removes the development conveniences.
It will not start unless the secrets are supplied — that is the point.

```bash
cp .env.example .env.production
# Edit .env.production and set every value. The compose file refuses to start with an empty one.
./scripts/generate-jwt-keys.sh

docker compose -f docker-compose.yml -f docker-compose.app.yml -f docker-compose.prod.yml up -d
```

What the production override changes:

- Every database password, the Redis password, the RabbitMQ password, the JWT signing key and the
  mail credentials are required (`${VAR:?message}`), so a missing secret is a startup error rather
  than a silent default.
- `CHOCO_LOG_OTP` is forced to `false`. A logged OTP is a usable OTP.
- Containers run with a read-only root filesystem, a `no-new-privileges` security option, and only
  `/tmp` (and the secret-staging directory) writable.
- Springdoc is off, so `/v3/api-docs` and `/swagger-ui.html` are not served.
- The SMTP connection requires TLS.

### Required secrets

| Variable | Purpose |
|---|---|
| `POSTGRES_ROOT_PASSWORD` | PostgreSQL superuser (initialisation only). |
| `IDENTITY_DB_PASSWORD`, `CATALOG_DB_PASSWORD`, `ORDER_DB_PASSWORD`, `PAYMENT_DB_PASSWORD`, `FULFILMENT_DB_PASSWORD`, `NOTIFICATION_DB_PASSWORD`, `REPORTING_DB_PASSWORD` | One per service role. |
| `REDIS_PASSWORD` | Redis `requirepass`. |
| `RABBITMQ_PASSWORD` | RabbitMQ default user. |
| `MAIL_HOST`, `MAIL_PORT`, `SMTP_USER`, `SMTP_PASSWORD`, `SMTP_AUTH`, `SMTP_TLS`, `MAIL_FROM` | Outbound mail. The identity and notification services read `CHOCO_SMTP_*`; the compose files map these names onto them. |
| `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET` | Payment provider. Without them the payment service starts but refuses every money-moving call — it fails closed. |
| `CHOCO_CORS_ORIGINS` | The browser origins allowed to call the API, comma-separated. Required so production never wildcards CORS; set it to the real storefront and dashboard origins. |
| `CHOCO_HTTP_TRUSTED_PROXIES` | The address(es) of the TLS-terminating proxy, so HSTS is emitted only for genuinely HTTPS requests and `X-Forwarded-For` is trusted only from that peer. Leave unset to trust no proxy (the safe default). |
| `CHOCO_ADMIN_BOOTSTRAP_EMAIL`, `CHOCO_ADMIN_BOOTSTRAP_PASSWORD` | First administrator, created once. Clear these afterwards. |

Never commit `.env.production`, `secrets/*.pem`, or any value from this table. `.gitignore` already
excludes `.env*` and `*.pem`.

---

## 4. HTTPS in production

HTTPS is mandatory in production and is **not** terminated by anything in this repository. The
supported shapes are:

1. A managed load balancer or ingress that terminates TLS and forwards to the gateway on 8080 and
   the two frontends on 80. This is the recommended shape.
2. A reverse proxy (nginx, Caddy, Traefik) on the same host, with certificates from Let's Encrypt,
   proxying to the same ports.

The gateway already sends HSTS (`max-age=31536000; includeSubDomains`), `X-Frame-Options: DENY` and
`Referrer-Policy: no-referrer`. `X-Frame-Options` and `Referrer-Policy` are unconditional. HSTS is
sent only when the request is understood to be HTTPS, because sending it on a plain-HTTP host would
pin a scheme the host does not serve.

Behind a proxy that terminates TLS, the application sees plain HTTP, so it needs to be told the
client's scheme came over HTTPS. Set `CHOCO_HTTP_TRUSTED_PROXIES` (property
`choco.http.trusted-proxies`) to the address the proxy connects from, and have the proxy send
`X-Forwarded-Proto: https`:

```yaml
  gateway:
    environment:
      CHOCO_HTTP_TRUSTED_PROXIES: ${TRUSTED_PROXY_ADDRESS:?set TRUSTED_PROXY_ADDRESS in .env.production}
```

The header is trusted **only** from an address in that list. This is deliberate: `X-Forwarded-Proto`
and `X-Forwarded-For` are client-supplied, and the same list is what stops the rate limiter from
being bypassed by a forged `X-Forwarded-For`. With the list empty — the default — the forwarded
scheme is ignored entirely and a plain request never carries HSTS. Do **not** set
`server.forward-headers-strategy=framework`: it rewrites the client address from `X-Forwarded-For`
unconditionally and defeats that protection.

Verify after deployment that a real HTTPS request carries `Strict-Transport-Security`, and that a
plain-HTTP request to the same host either redirects to HTTPS or carries no HSTS.

Local development deliberately runs over plain HTTP on localhost. Do not claim production HTTPS is
configured until a real certificate is serving real traffic.

---

## 5. Persistence and durability

Every stateful component uses a named volume, so `docker compose down` (without `-v`) preserves
data and `docker compose down -v` wipes it.

| Volume | Holds |
|---|---|
| `postgres-data` | Every service's database. The only durable copy of business data. |
| `redis-data` | Append-only rate-limit and refresh-token state. |
| `rabbitmq-data` | Durable queues and undelivered messages. |
| `catalog-images` | Uploaded product images. |
| `grafana-data`, `loki-data`, `tempo-data`, `prometheus-data` | Observability history. |

PostgreSQL runs with its default `fsync=on`, `synchronous_commit=on` and WAL archiving to the data
volume, which is what makes a committed transaction survive a container restart. That is a
*container* restart. It is **not** a backup: a lost volume loses the data. Backups are the operator's
responsibility and must be configured against the real deployment — see
[Backup and restore](#6-backup-and-restore).

---

## 6. Backup and restore

### Scheduled backups

`scripts/backup-databases.sh` dumps every database, **proves the dump is complete before keeping it**,
and prunes old ones. A truncated dump (the failure a full disk produces) is deleted and reported as a
failure rather than left looking like a backup.

```bash
# Run it from cron on the host that runs the stack, e.g. daily at 02:00 UTC.
0 2 * * *  /srv/choco/scripts/backup-databases.sh >> /var/log/choco-backup.log 2>&1
```

| Setting | Default | Meaning |
|---|---|---|
| `BACKUP_DIR` | `./backups` | Where dumps are written. Put this on a volume that is itself backed up or replicated off-host. |
| `BACKUP_RETENTION` | `14` | Days to keep. Older dumps are deleted by the script. |
| `POSTGRES_CONTAINER` | `choco-postgres` | The container to dump from. |
| `POSTGRES_USER` | `choco_root` | The role to dump as. |

A local dump on the same disk as the database does not survive that disk failing. The retention above
keeps two weeks locally; **copy the dumps off-host** (object storage, another volume) as part of the
same schedule — `aws s3 cp` or `rsync` after the script, or point `BACKUP_DIR` at a mounted remote
filesystem. That off-host copy is the part only you can configure, because it needs your storage
credentials.

### Restore

```bash
# Restore into a fresh instance.
cat backups/choco-2026-01-01T020000Z.sql | docker exec -i choco-postgres psql -U choco_root -d postgres
```

A backup is only proven when it has been restored. The restore must be rehearsed against a scratch
instance and the result checked (row counts on `orders`, `payments`, `customers`), not assumed.

**Rehearsal, run against the local compose stack:**

```bash
docker exec choco-postgres pg_dumpall -U choco_root > /tmp/backup.sql   # 194 KB
docker run -d --name choco-restore-rehearsal -e POSTGRES_PASSWORD=scratch_pw postgres:17.6-alpine
docker cp /tmp/backup.sql choco-restore-rehearsal:/tmp/b.sql
docker exec choco-restore-rehearsal psql -U postgres -d postgres -f /tmp/b.sql
```

Result: `psql` reported **zero errors**; all seven service databases (`choco_identity`,
`choco_catalog`, `choco_order`, `choco_payment`, `choco_fulfilment`, `choco_notification`,
`choco_reporting`) were recreated with their tables, and row counts matched the source exactly —
`orders` 3/3, `payments` 0/0, `customers` 3/3, `products` 1/1, and the `flyway_schema_history`
row count per database (3/2/1 for identity/order/payment). That is the whole point of the exercise:
a dump that restores the schema but loses rows, or loses the migration history, would leave a
service unable to start or silently missing data. Re-run this against the production instance after
the first deployment and record the counts here.

---

## 7. Operational notes

- **Health checks.** Every service exposes `/actuator/health` with readiness and liveness probes
  enabled. Compose uses readiness for `depends_on`, so a service only starts once its database,
  Redis and RabbitMQ are accepting connections.
- **Logs.** Structured JSON on stdout; `docker compose logs -f <service>`. With the observability
  profile, logs also flow to Loki and traces to Tempo.
- **Metrics.** `/actuator/prometheus` on every service; Prometheus scrapes them.
- **Alerting.** `infra/prometheus/alert-rules.yml` holds six rules, all written against metrics the
  services already expose, so nothing extra has to be instrumented: `ServiceDown`, `GatewayDown`,
  `HighHttpErrorRate` (5xx above 5% for 5m), `SlowHttpResponses` (p95 above 2s for 10m),
  `JvmHeapPressure` (heap above 90% for 10m) and `DatabaseConnectionPoolSaturated` (threads waiting
  for a connection for 5m). Each carries a `for:` so a single scrape blip does not page anyone, and a
  `severity` label. Prometheus loads them through `rule_files` in `prometheus.yml`; validate a change
  with `promtool check rules`. **Where the alerts are routed is a deployment decision** — this
  repository defines the conditions but ships no Alertmanager, so an alert is visible in the
  Prometheus UI (`/alerts`) and nowhere else until you add one. That is the honest state: the rules
  fire, but no one is paged.
- **Graceful shutdown.** Services set `server.shutdown: graceful`, so an in-flight request finishes
  before the process exits.
- **Restart policy.** `unless-stopped`, so a crashed service is restarted and a deliberately stopped
  one stays stopped.
- **The first administrator.** Created once from `CHOCO_ADMIN_BOOTSTRAP_*` at startup. The bootstrap
  refuses a weak password, never overwrites an existing account, and is idempotent — a restart with
  different credentials cannot reset a live password. Clear the two variables after the first boot.

---

## 8. CI/CD

`.github/workflows/` holds five pipelines:

| Workflow | Trigger | What it does |
|---|---|---|
| `backend.yml` | push to `main`, PR touching `backend/` | `mvn verify`: unit tests, integration tests against real PostgreSQL/Redis/RabbitMQ through Testcontainers, and the architecture enforcer rules. Uploads the surefire, failsafe and JaCoCo reports. |
| `frontend.yml` | push to `main`, PR touching `frontend/` | For each app: `npm ci`, typecheck, `vitest run`, `vite build`. |
| `images.yml` | changes to a Dockerfile or compose file | Builds every service image and both frontends. **Pushes them when a registry is configured** (see below); a pull request always builds only. |
| `security.yml` | every push and PR, plus weekly | gitleaks secret scan, OWASP dependency-check, and a Trivy filesystem and configuration scan. |
| `e2e.yml` | push to `main` touching `backend/`, `frontend/` or a compose file | Boots the whole stack with `docker compose up` and drives `scripts/e2e-journey.py` against it over the gateway. |

### Publishing images

`images.yml` publishes to whatever registry you configure — it is off until you set one, so the
default clone builds and tests without pushing anywhere.

Set three repository secrets (**Settings → Secrets and variables → Actions**):

| Secret | Value | Notes |
|---|---|---|
| `REGISTRY` | e.g. `ghcr.io/your-org` or `registry.example.com/choco` | The presence of this secret is what turns publishing on. No trailing slash needed. |
| `REGISTRY_USERNAME` | the registry user | Optional for GHCR — omit it and the workflow uses the GitHub actor. |
| `REGISTRY_PASSWORD` | a token with push rights | Optional for GHCR — omit it and the workflow uses the built-in `GITHUB_TOKEN` (the `packages: write` permission is already granted). |

Each image is tagged with the commit SHA (immutable) and, on a push to `main` only, `latest`. A pull
request never logs in and never pushes, so a fork cannot move `latest`. To deploy, pull
`<REGISTRY>/<service>-service:<sha>`; the service names are `identity`, `catalog`, `order`, `payment`,
`fulfilment`, `notification`, `reporting` and `gateway`, and the frontends are `storefront` and
`admin`.

Nothing deploys automatically. A deployment is a deliberate `docker compose` (or Kubernetes) action
on the host, using the image tags the workflow published. Adding a CD step means adding a job that
runs `docker compose -f docker-compose.prod.yml pull && up -d` on the target host over SSH; that host
and its credentials are yours to provide, and are not committed.

---

## 9. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Identity logs *"set but not readable"* and tokens die on restart | The signing key is not readable by the container user. | Confirm the entrypoint ran (it logs `staged the signing key`). Regenerate with `./scripts/generate-jwt-keys.sh` if the file has only the private key block. |
| A service is `unhealthy` and never starts | Its database, Redis or RabbitMQ is not up yet, or a credential is wrong. | `docker compose logs <service>`; the failure is usually a Flyway connection error naming the credential. |
| Repeated `Failed to export spans` in a log | Trace export is on but no OTLP collector is running. | Leave `CHOCO_OTLP_ENABLED=false` (the default) or start the observability profile. |
| The first request after a restart returns 503 from the gateway | The JVM is still warming up and the gateway's 10-second read timeout fired. | Retry; the second request succeeds. Raise `CHOCO_GW_READ_TIMEOUT` if this matters. |
| `PROD CONFIG` validation fails | A required secret is unset in `.env.production`. | The error names the variable. |
