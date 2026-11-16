# Kafka events — contract

**Owner: Patric.** Frozen after Phase 0; afterwards only additive changes (new optional field,
new event type) announced in the team chat. Java records live in
`s1-exchange/src/main/java/ro/upt/exchange/s1/events/`; S2 (Cristi) keeps its own copies.

## Envelope

Every message on every topic is the same JSON envelope (`envelope.schema.json`):

```json
{
  "eventId": "6f1c3b2e-6f6b-4f14-9a7d-3d0c4b1f0a11",
  "type": "TradeExecuted",
  "occurredAt": "2026-11-16T14:03:11.204Z",
  "schemaVersion": 1,
  "payload": { }
}
```

* `eventId` — UUID, unique per event. S2 inserts it into `processed_events` in the same
  transaction as the read-model change; a duplicate key means "already applied". For events
  written through the outbox, `eventId` = `event_outbox.id`.
* `type` — one of the event types below; picks the payload schema in `schemas/`.
* `occurredAt` — ISO-8601 UTC, when S1 made the change.
* `schemaVersion` — `1`. A breaking change bumps it; consumers ignore versions they do not know.
* `payload` — see `schemas/<Type>.schema.json`.

Serialisation: the message **value** is the envelope as a JSON string (no Spring type headers);
the message **key** is a string (the symbol, see below).

## Topics

All topics: 3 partitions, replication 1, created by S1 at startup (`KafkaTopicsConfig`).
The key keeps every stock's events ordered.

| Topic | Key | Types | Producer | Consumers |
|---|---|---|---|---|
| `exchange.order-commands` | symbol *(UNSAFE demo: orderId)* | `PlaceOrder`, `ModifyOrder`, `CancelOrder` | S1 trading (API) | S1 trading (matcher, group `s1-matching`) — **internal** |
| `exchange.orders` | symbol | `OrderPlaced`, `OrderUpdated`, `OrderCancelled`, `OrderRejected` | S1 trading via outbox | S2 |
| `exchange.trades` | symbol | `TradeExecuted` | S1 trading via outbox | S2 |
| `exchange.prices` | symbol | `PriceUpdated` | S1 trading via outbox | S2 |
| `exchange.incidents` | symbol or `global` | `IncidentRaised`, `IncidentUpdated` | S1 ops (Paul) via outbox | S2 |
| `exchange.bots` | symbol | `BotConfigChanged` | S1 ops (Paul) via outbox | S2 |

S2 consumes the five public topics in consumer group `s2-insights` and commits offsets only
after its database transaction commits.

## When S1 trading emits what

| Action | Events (in order) |
|---|---|
| `POST /api/orders` accepted | `OrderPlaced` (status `PENDING`) |
| `POST /api/orders` rejected (no cash/shares) | `OrderRejected` (status `REJECTED`, `reason`) |
| matcher fills | per fill: `TradeExecuted`, `PriceUpdated`, `OrderUpdated` (resting order); then `OrderUpdated` for the incoming order (`OPEN` / `PARTIALLY_FILLED` / `FILLED`) |
| MARKET remainder cancelled | `OrderCancelled` (`reason` = `NO_LIQUIDITY` or `INSUFFICIENT_FUNDS`) |
| `DELETE /api/orders/{id}` applied | `OrderCancelled` (`reason` = `CANCELLED_BY_USER`) |
| `PATCH /api/orders/{id}` applied | `OrderUpdated` (new `price`/`qty`, `version` + 1), then any fills as above |
| `POST /api/offers/{id}/buy` | `OrderPlaced` + `OrderUpdated` (buyer's order, `FILLED`), `OrderUpdated` (offer), `TradeExecuted`, `PriceUpdated` |

`OrderUpdated` carries the full current state of the order (not a delta), so a consumer can
always upsert. `remainingQty` and `status` together tell you what happened.

## Files

```
envelope.schema.json           the envelope
schemas/OrderCommand.schema.json    PlaceOrder / ModifyOrder / CancelOrder payload
schemas/OrderEvent.schema.json      OrderPlaced / OrderUpdated / OrderCancelled / OrderRejected payload
schemas/TradeExecuted.schema.json
schemas/PriceUpdated.schema.json
schemas/IncidentEvent.schema.json   IncidentRaised / IncidentUpdated payload
schemas/BotConfigChanged.schema.json
examples/<Type>.json            one full message (envelope + payload) per type, used by the mocks
```

Validate an example against its schema with any JSON Schema 2020-12 validator, e.g.
`npx ajv-cli validate -s schemas/TradeExecuted.schema.json -d examples/TradeExecuted.json`
(after wrapping the payload — or validate the whole message against `envelope.schema.json`,
which references the payload schemas through `$defs`).
