# S1 trading — integration guide for the team

**Owner: Patric.** What the trading slice provides, what it expects from the other slices, and
how to run it before the rest exists.

## What is where

```
s1-exchange/
  pom.xml, Dockerfile, application.yml          Patric  (every dependency the team needs is in)
  src/main/resources/platform.yml, ops.yml, realtime.yml   PLACEHOLDERS - replace with yours
  src/main/resources/db/migration/V100__exchange_schema.sql   schema (verbatim from the PDF)
  src/main/java/ro/upt/exchange/s1/
    config/      ExchangeTopics (topic names), KafkaTopicsConfig (6 × NewTopic)
    domain/      all entities + repositories (incl. the conditional UPDATEs the ledger needs)
    events/      EventEnvelope, EventTypes, payload records, BookSnapshot   (Java side of contracts/events)
    trading/     api/ dto/ error/ service/ kafka/                           (Patric)
    platform/    AccountLedger, EventOutbox, AuthenticatedUser (+ STUBS)    (Cezar)
    ops/         IncidentService (+ no-op STUB)                             (Paul)
    realtime/    LivePublisher (+ no-op STUB)                               (Paul)
contracts/s1/trading.yaml      OpenAPI (lint: npx @redocly/cli lint contracts/s1/trading.yaml)
contracts/events/              envelope + 6 topics + JSON schemas + one example per type
web-client/src/features/trading/   stock page (see its README for the seams)
docs/concurrency-analysis.md
```

## Running S1 alone (Phase 1, "build against mocks")

Needs Docker. From `s1-exchange/`:

```bash
# JDK 21+ (the pom targets 21; JDK 23 works), Maven 3.9
mvn test                      # unit tests only
mvn -Pit verify               # + Testcontainers suites (PostgreSQL + Kafka started for you)
```

To run the server by hand you need a PostgreSQL and a Kafka; the simplest is to reuse the
test containers' images:

```bash
docker run -d --name pg -e POSTGRES_DB=exchange -e POSTGRES_USER=exchange -e POSTGRES_PASSWORD=exchange -p 5432:5432 postgres:16-alpine
docker run -d --name kafka -p 9092:9092 apache/kafka:3.8.0
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
# Swagger UI: http://localhost:8081/swagger-ui.html
```

Under the `local` / `test` profiles the **stub security** (`platform/StubSecurityConfig`)
authenticates a request from the header `X-User-Id: <users.id>`; every request without it is
anonymous. Cezar's JWT `SecurityConfig` replaces this (the stub is `@Profile({"local","test"})`,
so it is inert in the default profile).

## The stubs and who replaces them

| Stub (delete when yours lands) | Owner | Contract it implements |
|---|---|---|
| `platform/StubAccountLedger` | Cezar | `AccountLedger` — see its Javadoc for the reservation rules trading relies on, and the `noRollbackFor` requirement on `Insufficient*Exception` |
| `platform/JpaEventOutbox` | Cezar | `EventOutbox.add(topic, key, type, payload)` — a complete insert side; only the relay is missing (`OutboxEventRepository.lockUnpublishedBatch` / `markPublished` are ready for it) |
| `platform/StubSecurityConfig` | Cezar | sets an `Authentication` whose principal is `AuthenticatedUser(userId, accountId, username, role)` |
| `ops/NoopIncidentService` | Paul | `IncidentService.raise(...)`, ideally `REQUIRES_NEW` |
| `realtime/NoopLivePublisher` | Paul | `LivePublisher` — payloads are the `events/` records, so the web app sees one shape on REST, Kafka and STOMP |

Interfaces and their Javadoc are the frozen Phase-0 signatures. Only the stub *classes* go.

## Calling into trading from your slice

* **Paul's market maker** places and cancels quotes through the public API
  (`POST /api/orders`, `DELETE /api/orders/{id}`, role `MARKET_MAKER`, `source` is set to
  `MARKET_MAKER` automatically). Fills arrive on `/user/queue/orders` and
  `/topic/trades/{symbol}`.
* **Cezar's My orders / My trades pages** use `GET /api/orders/mine`, `GET /api/trades/mine`,
  `PATCH` / `DELETE /api/orders/{id}`. The TypeScript types and `tradingApi` wrappers in
  `web-client/src/features/trading/` are exported from its `index.ts`
  (`import { tradingApi, tradingKeys } from '@/features/trading'`), and `tradingKeys` lets you
  invalidate the same TanStack Query caches.
* **Cristi's S2** consumes `exchange.orders`, `exchange.trades`, `exchange.prices` — schemas and
  examples in `contracts/events/`. `OrderUpdated` always carries the full order.

## HTTP semantics worth knowing

* Writes are **asynchronous**: `POST /api/orders` returns `202` with the `PENDING` order; it is
  matched milliseconds later by the Kafka consumer. `PATCH`/`DELETE` also return `202`.
* `POST /api/offers/{id}/buy` is **synchronous** and settles in one transaction; `409` bodies
  carry `reason` (`PRICE_CHANGED` / `NO_LONGER_AVAILABLE`) and the current `offer`.
* Errors are RFC 9457 problem details with a `code` (`VALIDATION`, `NOT_FOUND`, `INVALID_STATE`,
  `INSUFFICIENT_FUNDS`, …). `422` from `POST /api/orders` is the exception: its body is the
  `REJECTED` order itself (it is stored, so it shows in the user's history).
* MARKET orders are immediate-or-cancel; a MARKET BUY reserves nothing up front and may be
  partially filled then cancelled with `reason = INSUFFICIENT_FUNDS` / `NO_LIQUIDITY`.
* Prices must be multiples of the stock's `tick_size`; totals are rounded to cents.

## Configuration (`application.yml`, `exchange.*`)

| Property | Default | Meaning |
|---|---|---|
| `exchange.concurrency-mode` (`EXCHANGE_CONCURRENCY_MODE`) | `SAFE` | `UNSAFE` switches on the broken variants for the demo |
| `exchange.topics.*` | `exchange.…` | topic names, 3 partitions |
| `exchange.matching.concurrency` | 3 | matcher threads (= partitions) |
| `exchange.matching.pending-timeout-seconds` | 10 | sweeper re-publishes older `PENDING` orders |
