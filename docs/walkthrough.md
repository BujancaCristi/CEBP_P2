# Walkthrough: what is implemented (Patric, Trading slice)

Status on 16 Nov 2026. Read with `Work_Split.pdf` (Patric section) and `docs/concurrency-analysis.md`.

## 1. The big picture

```
 browser (web-client/features/trading)
    |  REST + STOMP
    v
 S1 trading API ---- POST /api/orders ----> reserve cash/shares, save PENDING, 202
    |                                          |
    |                                          v  Kafka exchange.order-commands (key = symbol)
    |                                  OrderCommandConsumer (1 thread per partition)
    |                                          v
    |                                    MatchingEngine -- one PostgreSQL transaction -->
    |                                      orders, trades, ledger, stocks.value, event_outbox
    |
    +-- POST /api/offers/{id}/buy ---> one conditional UPDATE, synchronous, 200 or 409
```

Only the trading slice is real. Platform (Cezar), ops and realtime (Paul) run as stubs behind the
frozen interfaces, so S1 compiles and runs alone.

## 2. Backend, package by package (`s1-exchange/src/main/java/ro/upt/exchange/s1/`)

| Package | What it does |
|---|---|
| `domain/` | All JPA entities and repositories for the schema in `V100__exchange_schema.sql`, with `@Version`. Includes the native conditional updates (take offer, fill, reprice, ledger reservations). |
| `events/` | `EventEnvelope`, event type names and payload records. This is the Java side of `contracts/events/`. |
| `config/` | Topic names and six `NewTopic` beans, three partitions each. |
| `trading/api/` | `OrderController` (place, modify, cancel, mine), `OfferController` (list, buy), `BookController`, `TradeController`. |
| `trading/service/` | `OrderService` (validation, reservations, PENDING, publish command), `MatchingEngine` (price-time priority, partial fills, settlement), `OfferService` (buy flow and 409 logic), `BookService`, `PendingOrderSweeper` (re-sends commands for orders stuck PENDING), `TradingEvents` (outbox rows plus after-commit pushes). |
| `trading/service/*OrderWrites` | `OrderWriteStrategy` with `SafeOrderWrites` and `UnsafeOrderWrites`. The mode comes from `exchange.concurrency-mode=SAFE\|UNSAFE`. |
| `trading/kafka/` | The matching consumer and its Kafka config. |
| `trading/error/` | RFC 9457 problem details with a `code`. |
| `platform/`, `ops/`, `realtime/` | Interfaces plus stubs: `AccountLedger`, `EventOutbox`, stub security that reads `X-User-Id`, no-op `IncidentService` and `LivePublisher`. |

## 3. The three concurrency cases

| Case | SAFE | UNSAFE | Last result (200 rounds) |
|---|---|---|---|
| 1. Two buyers, one offer | Conditional UPDATE on `remaining_qty`, row lock | Read, check in Java, write computed value | SAFE 0, UNSAFE 200 |
| 2. Price changed mid-purchase | Same UPDATE filters on `version = seenVersion`, answers 409 `PRICE_CHANGED` | Ignores version and max price | SAFE 0, UNSAFE 200 |
| 3. 70 + 50 wanted, 100 available | Kafka key = symbol, one matcher per stock | Key = order id, parallel matchers | SAFE 0, UNSAFE 137 |
| Extra: two S1 instances | Row lock lives in PostgreSQL | An in-memory lock hides the bug in one JVM, fails with two | SAFE 0 of 50, UNSAFE 50 of 50 |

Details and SQL are in `docs/concurrency-analysis.md`.

## 4. HTTP behaviour worth knowing

- `POST /api/orders`, `PATCH` and `DELETE` are asynchronous and return 202.
- `POST /api/offers/{id}/buy` is synchronous. A 409 body carries `reason` and the current `offer`.
- Invalid or rejected orders return 422 with the stored REJECTED order.
- MARKET orders are immediate-or-cancel. Prices must match the stock's tick size.
- Swagger UI is at `/swagger-ui.html` and the spec at `/v3/api-docs`.

## 5. Web client (`web-client/src/features/trading/`)

A stock page with a live order book, an offers list with Buy, a confirm dialog on 409
(`PRICE_CHANGED`: "Price changed to 12.00, confirm?"; `NO_LONGER_AVAILABLE`: refresh), and an order
form. It uses Mantine, TanStack Query and STOMP only. Everything from other slices goes through
`integration.ts`: `useAuth`, `useStomp` (Cezar) and `PriceChart` (Cristi). Other features import
this folder only through `index.ts`.

## 6. Contracts

- `contracts/s1/trading.yaml`: orders, offers, book, my orders, my trades.
- `contracts/events/`: envelope, six topic schemas and one example per event type.

## 7. Tests

| Suite | Result |
|---|---|
| `TradingUnitTest` (`mvn test`) | unit tests |
| `TradingFlowIT` (6, includes the Swagger check) | passed on 16 Nov |
| `BuyOfferIT` (4) | passed |
| `SafeConcurrencyIT` / `UnsafeConcurrencyIT` (3 + 3, 200 rounds) | passed |
| `TwoInstancesIT` (2) | passed on 16 Nov (an older saved report showed 2 errors, now fixed) |

Run them with `mvn -Pit verify` from `s1-exchange/` using JDK 21 or newer. Docker is needed.

## 8. What is still needed from Patric

Against the Work_Split, every Patric task is done. What remains is verification that is only
possible once other slices land, plus small polish:

1. **Web client never compiled.** `web-client/` has no `package.json` or Vite project yet (Cezar's
   Phase 0). Type-check and run the stock page once it exists, and adjust `integration.ts` if
   `useAuth`, `useStomp` or `PriceChart` differ.
2. **Not verified against real slices.** Swap the stubs and re-run `TradingFlowIT` and the
   concurrency suites once Cezar's ledger, outbox relay and JWT security land. The ledger must keep
   the `noRollbackFor` rule documented in `AccountLedger`.
3. **Dockerfile never built** in this session. Run `docker build` on `s1-exchange/` before the
   compose gate.
4. **Contract drift check.** `trading.yaml` is hand-written and springdoc generates its own spec.
   Compare them once (for example with the Redocly lint) so the mocks match the real API.
5. **Integration checklist items owned by Patric.** "Every stock always shows a bid and an ask"
   needs Paul's bot. "Buy and sell in the browser, live updates" needs Cezar's shell and Paul's
   STOMP. Both are demo-day checks, not code gaps on your side.
6. **Optional.** The PDF suggests a pessimistic `SELECT ... FOR UPDATE` variant for case 1 as a
   comparison. It is not implemented.
7. **Housekeeping.** Commit this document. Ownership rules say others open issues instead of
   editing your files, so announce frozen interface or contract changes in the team chat.
