# S2 - Insights (owner: Cristi)

Event-driven read side of the exchange: consumes S1's five public Kafka topics into the `insights`
PostgreSQL database, serves statistics over REST (`contracts/s2-openapi.yaml`), and runs the nightly
report agent.

## Run

```bash
mvn test                 # unit tests
mvn -Pit verify          # + ProjectionIT (Testcontainers PostgreSQL, replays contracts/events/examples)
S2_DB_URL=jdbc:postgresql://localhost:5433/insights KAFKA_BOOTSTRAP=localhost:9092 mvn spring-boot:run
# Swagger UI: http://localhost:8082/swagger-ui.html
```

Environment: `S2_DB_URL/USER/PASSWORD`, `KAFKA_BOOTSTRAP`, `JWT_SECRET` (same as S1), `SERVICE_API_KEY`
(the MCP server's `X-Api-Key`), `GEMINI_API_KEY`, `MCP_URL`, `INSIGHTS_ZONE`.

## How the read model is built

`EventProjector` handles one envelope per transaction: insert into `processed_events` (primary key =
`eventId`, so a redelivery is a no-op), then `INSERT ... ON CONFLICT` into the `*_view` tables. The Kafka
offset is committed after the transaction (`ack-mode: record`). Replaying from offset 0
(`scripts/replay.sh`) therefore gives the same numbers.

| Event | Effect |
|---|---|
| `TradeExecuted` | `trades_view`; OHLC, VWAP, volume, trade count in `daily_stock_stats`; fills, bought/sold, inventory and cash P&L in `bot_daily_stats` when a side is a bot (known from `bot_configs_view`) |
| `OrderPlaced` / `OrderCancelled` / `OrderRejected` | order counters per stock per day; a placed order with `source = MARKET_MAKER` counts as a bot quote |
| `IncidentRaised` / `IncidentUpdated` | upsert `incidents_view` |
| `BotConfigChanged` | upsert `bot_configs_view` (older events never overwrite newer rows) |
| `PriceUpdated`, `OrderUpdated` | ignored (live price is S1's; fills come from trades) |

Decisions: day boundary = `INSIGHTS_ZONE` (default Europe/Bucharest); realized P&L is cash P&L
(sells minus buys), inventory not marked to market; OHLC comes from trades only.

## Nightly report agent

`ReportAgent`: Spring AI `ChatClient` on Gemini (OpenAI-compatible endpoint, swap with `LLM_BASE_URL` /
`LLM_MODEL`) plus the MCP client connected to `MCP_URL`. Runs at 02:00 (`insights.agent.cron`) and on
`POST /api/agent/run?date=` (ADMIN), which returns the report in the response. Safeguards: at most 15
tool calls (`CountingToolCallback`), nothing stored, a "report failed" email through `send_report` on error.
The system prompt is the one from the application description (section 7).

## Security

JWT (HS256, `JWT_SECRET`) with claim `role`; `X-Api-Key` = `SERVICE_API_KEY` gives role SERVICE, accepted
only on `/api/trades`, `/api/bots/**` and `/api/incidents/**`.
