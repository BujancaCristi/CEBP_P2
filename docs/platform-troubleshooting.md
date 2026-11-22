# Platform troubleshooting

## Docker daemon or Compose

Docker Compose 5.8.0 or Docker Desktop with the Compose plugin is required.

If Docker reports that the daemon socket is missing, start Docker Desktop and enable WSL
integration for the current Linux distribution.

## A container is unhealthy

Inspect all services:

```bash
docker compose ps
docker compose logs --tail=100 s1-exchange
docker compose logs --tail=100 s2-insights
docker compose logs --tail=100 web-client
```

S1 readiness:

```bash
curl http://localhost:8081/actuator/health/readiness
```

S2 readiness:

```bash
curl http://localhost:8082/actuator/health/readiness
```

## 502 Bad Gateway

A 502 means nginx could not reach its S1 or S2 upstream. Common causes are:

* The upstream container is restarting.
* An upstream container exited.
* nginx has stale container routing after recreation.

Check direct upstreams before checking the browser route:

```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
```

Then check nginx:

```bash
docker compose logs --tail=100 web-client
```

Recreate the web client if nginx itself needs to reload the dynamic DNS configuration:

```bash
docker compose up -d --build --force-recreate web-client
```

## S1 restarts or becomes unhealthy

Verify the exchange database and Kafka are healthy:

```bash
docker compose ps exchange-db kafka
docker compose logs --tail=100 exchange-db kafka s1-exchange
```

The production JWT secret must contain at least 32 characters. It is configured through
`JWT_SECRET` in `.env`.

## S2 restarts

S2's Docker Compose configuration disables the MCP client until an MCP server is available.
To use report-agent MCP tools, add Paul's MCP server and set
`MCP_CLIENT_ENABLED=true`/`MCP_URL` for S2.

## 403 Forbidden

A 403 from an `/api/s1/**` endpoint usually means that:

* The JWT is missing.
* The JWT is invalid or expired.
* The account's role cannot perform the operation.

Successful login returns a bearer token. Send it as:

```text
Authorization: Bearer <token>
```

## The web client looks empty

Docker Compose enables mock preview data by default. Set `VITE_MOCK_DATA=true` in `.env`
before building the web image:

```env
VITE_MOCK_DATA=true
```

Mock mode supplies:

* Sample stocks and market movers
* Portfolio positions, cash, and P&L
* Demo order and trade history
* Order book bids/asks and offer data
* Recent trade tape
* Price-history charts
* Statistics
* A sample nightly report

Authentication remains real: register or log in first, then open the market and account pages.
Mock screens display a yellow `Demo data` badge.

To use live API data instead:

```env
VITE_MOCK_DATA=false
```

Rebuild the web client afterward:

```bash
docker compose up -d --build web-client
```
