# MADE-TO-ORDER-CHOCOLATE

A single-seller, made-to-order chocolate e-commerce platform. Customers register, verify an email and
a mobile number, browse products and variants, place orders and pay online through Razorpay. The
seller makes each order to order and delivers it personally. There is no marketplace, no third-party
courier integration and no cash on delivery; preparation begins only after a verified payment.

## Stack

- **Backend** — Java 21, Spring Boot 3.5, Spring Security, Spring Cloud Gateway. Eight deployables
  behind one gateway, each owning one PostgreSQL database.
- **Frontend** — React, Vite, TypeScript. A customer storefront and a seller dashboard.
- **Infrastructure** — PostgreSQL 17, Redis, RabbitMQ, Flyway, Docker Compose.
- **Observability** — Prometheus, Grafana, Loki, Tempo (optional `observability` profile).
- **Testing** — JUnit 5, Mockito, Testcontainers (real PostgreSQL, Redis, RabbitMQ, GreenMail), Vitest.

## Run it

```bash
# the full stack: infrastructure plus ten application containers
docker compose -f docker-compose.yml -f docker-compose.app.yml up -d --build

# infrastructure only, for local development
docker compose up -d postgres redis rabbitmq

# the production shape (requires every secret; fails closed naming the missing variable)
docker compose -f docker-compose.yml -f docker-compose.app.yml -f docker-compose.prod.yml up -d
```

Then open the storefront at `http://localhost:80` and the seller dashboard at `http://localhost:5174`.
Grafana is on `http://localhost:3000` (`admin` / `choco_grafana_pw`); RabbitMQ management is on
`http://localhost:15672` (`choco` / `choco_rabbit_pw`).

## Build and test

```bash
cd backend && mvn clean verify                        # unit + integration tests + enforcer rules
cd frontend/storefront && npm ci && npm test -- --run
cd frontend/admin      && npm ci && npm test -- --run
```

`*Test` classes run under `test`; `*IT` classes run under `verify` (failsafe) and need Docker.

## Documentation

| Document | What it covers |
|---|---|
| [`docs/handover.md`](docs/handover.md) | Final handover: state, evidence, external dependencies, next steps |
| [`docs/architecture.md`](docs/architecture.md) | Deployment topology, Clean Architecture, per-service design |
| [`docs/development-progress.md`](docs/development-progress.md) | Requirement-by-requirement status and the test tally |
| [`docs/security-review.md`](docs/security-review.md) | Security architecture, findings, fixes, residual risks |
| [`docs/deployment.md`](docs/deployment.md) | Deployment, secrets, HTTPS, durability, backup and restore |
| [`docs/local-development.md`](docs/local-development.md) | The day-to-day development loop |
| [`AGENTS.md`](AGENTS.md) | Working notes for contributors and the list of pitfalls already hit |

## Status

All roadmap milestones are complete and verified: **1273 backend tests, 105 storefront tests and 34
dashboard tests, all passing**, plus a 33-step end-to-end journey over the running stack. Deployment
is Docker Compose; Kubernetes is out of scope. Before a real launch, external dependencies — hosting,
TLS, live Razorpay credentials, an SMS provider — must be provided. See `docs/handover.md` section 9.
