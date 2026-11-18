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
`Referrer-Policy: no-referrer`. Once TLS is terminated in front of it, those headers take effect.

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

Not automated in this repository; the commands below are the procedure to run against a real
deployment.

```bash
# Back up every database in one file.
docker exec choco-postgres pg_dumpall -U choco_root > backup-$(date +%F).sql

# Restore into a fresh instance.
cat backup-2026-01-01.sql | docker exec -i choco-postgres psql -U choco_root -d postgres
```

A backup is only proven when it has been restored. The restore must be rehearsed against a scratch
instance and the result checked (row counts on `orders`, `payments`, `customers`), not assumed.

---

## 7. Operational notes

- **Health checks.** Every service exposes `/actuator/health` with readiness and liveness probes
  enabled. Compose uses readiness for `depends_on`, so a service only starts once its database,
  Redis and RabbitMQ are accepting connections.
- **Logs.** Structured JSON on stdout; `docker compose logs -f <service>`. With the observability
  profile, logs also flow to Loki and traces to Tempo.
- **Metrics.** `/actuator/prometheus` on every service; Prometheus scrapes them.
- **Graceful shutdown.** Services set `server.shutdown: graceful`, so an in-flight request finishes
  before the process exits.
- **Restart policy.** `unless-stopped`, so a crashed service is restarted and a deliberately stopped
  one stays stopped.
- **The first administrator.** Created once from `CHOCO_ADMIN_BOOTSTRAP_*` at startup. The bootstrap
  refuses a weak password, never overwrites an existing account, and is idempotent — a restart with
  different credentials cannot reset a live password. Clear the two variables after the first boot.

---

## 8. CI/CD

`.github/workflows/` holds four pipelines:

| Workflow | Trigger | What it does |
|---|---|---|
| `backend.yml` | push to `main`, PR touching `backend/` | `mvn verify`: unit tests, integration tests against real PostgreSQL/Redis/RabbitMQ through Testcontainers, and the architecture enforcer rules. Uploads the surefire, failsafe and JaCoCo reports. |
| `frontend.yml` | push to `main`, PR touching `frontend/` | For each app: `npm ci`, typecheck, `vitest run`, `vite build`. |
| `images.yml` | changes to a Dockerfile or compose file | Builds every service image and both frontends. Does not push — there is no registry configured. |
| `security.yml` | every push and PR, plus weekly | gitleaks secret scan, OWASP dependency-check, and a Trivy filesystem and configuration scan. |

No workflow pushes to a registry or deploys. Adding a registry means setting `REGISTRY`,
`REGISTRY_USERNAME` and `REGISTRY_PASSWORD` as repository secrets and adding a login and push step to
`images.yml`.

---

## 9. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Identity logs *"set but not readable"* and tokens die on restart | The signing key is not readable by the container user. | Confirm the entrypoint ran (it logs `staged the signing key`). Regenerate with `./scripts/generate-jwt-keys.sh` if the file has only the private key block. |
| A service is `unhealthy` and never starts | Its database, Redis or RabbitMQ is not up yet, or a credential is wrong. | `docker compose logs <service>`; the failure is usually a Flyway connection error naming the credential. |
| Repeated `Failed to export spans` in a log | Trace export is on but no OTLP collector is running. | Leave `CHOCO_OTLP_ENABLED=false` (the default) or start the observability profile. |
| The first request after a restart returns 503 from the gateway | The JVM is still warming up and the gateway's 10-second read timeout fired. | Retry; the second request succeeds. Raise `CHOCO_GW_READ_TIMEOUT` if this matters. |
| `PROD CONFIG` validation fails | A required secret is unset in `.env.production`. | The error names the variable. |
