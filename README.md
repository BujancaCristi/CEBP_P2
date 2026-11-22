# CEBP Stock Exchange

## Start the platform

Copy `.env.example` to `.env`, set `JWT_SECRET` and `SERVICE_API_KEY`, then run:

```bash
docker compose up --build
```

Docker Compose 5.8.0 or Docker Desktop with the Compose plugin is required.

The web application is available at `http://localhost`, S1 Swagger at
`http://localhost:8081/swagger-ui.html`, S2 Swagger at
`http://localhost:8082/swagger-ui.html`, and Mailpit at `http://localhost:8025`.

Kafka UI is available at `http://localhost:8080`.

The seeded `local` profile accounts are:

| Username | Password | Role |
|---|---|---|
| `admin` | `admin-password` | ADMIN |
| `trader` | `trader-password` | TRADER |
| `market-maker` | `bot-password` | MARKET_MAKER |

Use `SPRING_PROFILES_ACTIVE=local` when running S1 directly to load the demo seed.
The production profile uses JWT authentication and the real account ledger/outbox relay;
`local` and `test` retain the development stubs needed by the trading test suite.

## Current implemented platform behavior

The Cezar platform slice now includes:

- JWT registration, login, and current-user endpoints
- Production JWT security; local/test profile header-based security remains available for
  trading tests
- The real account ledger implementation in production profiles
- Atomic cash reservation, share reservation, release, and trade settlement
- A transactional outbox relay publishing to Kafka every 200 ms
- S1/S2 Docker health checks
- Vite/React application shell, authentication context, STOMP hook, and nginx routing
- Login/register, market, portfolio, and account-history placeholders
- Docker Compose, `.env.example`, Nginx proxy configuration, and CI workflow

S1 and S2 can be rebuilt independently:

```bash
docker compose up -d --build s1-exchange
docker compose up -d --build s2-insights
docker compose up -d --build web-client
```

## 502 and proxy troubleshooting

A `502 Bad Gateway` means nginx could not connect to one of the backend upstream containers.
Check Compose and the direct service health endpoints:

```bash
docker compose ps
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
docker compose logs --tail=100 web-client
```

The nginx configuration uses Docker's internal DNS resolver. It therefore resolves S1 and
S2 container names dynamically and does not rely on one container IP indefinitely.

The proxy routes are:

| Browser route | Upstream |
|---|---|
| `/api/s1/*` | S1 on port 8081 |
| `/api/s2/*` | S2 on port 8082 |
| `/ws` | S1 WebSocket endpoint |

## Current user-facing status

A newly registered user receives `10,000 EUR`. Registration and JWT login have been verified
against Docker Compose.

The React shell is currently present, but not every screen is fully populated:

- Cezar: market, portfolio, authentication, and account shell
- Patric: stock trading page, order book, offers, order form, and order/trade APIs
- Cristi: charts, market movers, statistics, and reports
- Paul: live STOMP updates and market-maker/ops screens

My Orders and My Trades are platform-route placeholders until their trading API displays are
completed and connected.

S2 starts with MCP client integration disabled in Docker Compose until Paul's MCP server is
running. This allows insights and its REST service to start independently.

## Demo/mock data mode

Docker Compose enables mock frontend data by default with `VITE_MOCK_DATA=true`. This allows
the market, portfolio, order/trade history, stock pages, charts, movers, and reports to be
previewed while backend teammate features are incomplete.

Mock data is compiled into the web image. Switch to real APIs with:

```env
VITE_MOCK_DATA=false
```

Then rebuild the web client:

```bash
docker compose up -d --build web-client
```

## Platform responsibilities

The platform implementation is in `s1-exchange/.../platform/` and provides authentication,
accounts, portfolio endpoints, atomic cash/share reservations, trade settlement, Kafka
outbox delivery, and seed data. The React shell and platform pages are in
`web-client/src/app/` and `web-client/src/features/platform/`.

See `docs/trading-integration.md` and `docs/walkthrough.md` for the contracts between
the platform, trading, insights, ops, and realtime slices.

For Docker, proxy, authentication, and container troubleshooting, see
`docs/platform-troubleshooting.md`.