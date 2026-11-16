# Concurrency analysis — S1 trading

**Owner: Patric.** Companion to Application_Description.pdf §6. The numbers below come from
`s1-exchange`'s Testcontainers suites (PostgreSQL 16 + Kafka 3.8, real containers), run with
`mvn -Pit verify` on 16 Nov 2026. Every scenario runs 200 rounds in each mode; a *violation* is a
round where the test's invariant is broken.

## Results

| Case | Invariant | SAFE | UNSAFE |
|---|---|---|---|
| 1. Two users buy the same offer at the same time | exactly one buyer succeeds; 100 shares sold | **0** / 200 | **200** / 200 (both succeed, 200 shares "sold" from 100) |
| 2. Price changed while the buyer was deciding | no trade above the price the buyer saw | **0** / 200 (200 × `409 PRICE_CHANGED`) | **200** / 200 (bought at 12.00 having seen 10.00) |
| 3. Demand exceeds supply (70 + 50 wanted, 100 available) | total sold = 100, second buyer gets the rest | **0** / 200 | **137** / 200 (over-sold; the other 63 rounds both order ids hashed to the same partition, so they were serialised by accident) |
| 1, across **two S1 instances** | exactly one buyer succeeds | **0** / 50 | — |
| 1, UNSAFE + in-memory `ReentrantLock`, one instance | | — | **0** / 50 (the JVM lock hides the bug) |
| 1, UNSAFE + in-memory lock, **two instances** | | — | **50** / 50 (each JVM has its own lock) |

Test classes: `SafeConcurrencyIT`, `UnsafeConcurrencyIT`, `TwoInstancesIT`
(`src/test/java/ro/upt/exchange/s1/trading/concurrency/`). Rounds are configurable with
`-Dconcurrency.rounds=` and `-Dconcurrency.rounds2=`.

## The three mechanisms

The three cases are deliberately solved with three different tools, each of which works across
threads, across transactions *and across running copies of S1*, because the arbiter is
PostgreSQL or Kafka, never the JVM.

### Case 1 — atomic conditional update (database row lock)

`OrderRepository.takeOffer`:

```sql
UPDATE orders
   SET remaining_qty = remaining_qty - :qty,
       status  = CASE WHEN remaining_qty - :qty = 0 THEN 'FILLED' ELSE 'PARTIALLY_FILLED' END,
       version = version + 1
 WHERE id = :offerId
   AND status IN ('OPEN', 'PARTIALLY_FILLED')
   AND remaining_qty >= :qty          -- case 1
   AND version = :seenVersion         -- case 2
   AND price <= :maxPrice
```

Both buyers run this statement. The first takes the row lock and changes 1 row. The second
blocks on the lock; when the first commits, PostgreSQL **re-evaluates the WHERE clause on the
new row version** (READ COMMITTED semantics for UPDATE) — `remaining_qty >= 100` is now false —
and changes 0 rows. `OfferService` reads the offer again and answers
`409 NO_LONGER_AVAILABLE`. The check and the write are one statement; there is no window.

**UNSAFE** (`UnsafeOrderWrites.takeOffer`): `SELECT remaining_qty` → check `>= qty` in Java →
`UPDATE orders SET remaining_qty = :computed`. Both buyers read 100, both compute 0, the second
UPDATE simply overwrites the first — a *lost update*. Both get a trade; 200 shares leave an offer
of 100.

### Case 2 — optimistic locking with `version`

Every write to an order bumps `version` (the JPA `@Version` column, also incremented by the
native statements). The offer the buyer sees carries its version; the buy sends it back as
`seenVersion` and the same UPDATE above filters on `version = :seenVersion`. When the seller
changed the price (`PATCH /api/orders/{id}` → `OrderRepository.reprice`, which bumps the
version), the buy changes 0 rows, the service reads the current offer and answers
`409 PRICE_CHANGED` with the new price and version. The web app shows *"Price changed to 12.00,
confirm?"* and, only if the user agrees, resends with the new `seenVersion` and `maxPrice`.

**UNSAFE**: version and `maxPrice` are ignored; the buyer pays whatever the row says when the
UPDATE runs — 12.00 having agreed to 10.00, in 200 of 200 rounds.

### Case 3 — one writer per stock (partitioned queue)

`POST /api/orders` does not match. It reserves cash/shares, stores the order as `PENDING` and,
after the commit, sends a `PlaceOrder` command to `exchange.order-commands` with **key =
symbol**. Kafka puts every command of a symbol on the same partition; the matcher
(`OrderCommandConsumer`, concurrency = partitions = 3) reads each partition with exactly one
thread. So all orders of one stock are matched **one at a time, in arrival order**, by
construction — the queue is the lock, and it still holds with two S1 instances (Kafka assigns
each partition to one consumer in the group).

The matcher (`MatchingEngine.match`) walks the opposite side best-price-first/oldest-first,
fills `min(remaining, remaining)` at the resting price, and settles everything (both orders,
the trade, cash, shares, `stocks.value`, outbox rows) in **one PostgreSQL transaction**. The
offset is committed only after that transaction; a crash in the middle replays the command,
and a replayed `PlaceOrder` for an order that is no longer `PENDING` is a no-op.

**UNSAFE** (`UnsafeOrderWrites.commandKey` = order id): commands of one stock spread over the
three partitions and are matched in parallel; the fill is written as
`SET remaining_qty = :computed` from a value read before the other matcher committed. 70 + 50
are both filled from 100: 120 sold, 137 times out of 200. (In the other 63 rounds the two order
ids happened to hash to the same partition.)

### Why the SAFE matcher *also* uses a conditional decrement

Buy-from-offer (synchronous HTTP, case 1) and the matcher (Kafka thread, case 3) can touch the
same resting SELL order at the same time. The matcher therefore writes fills as
`remaining_qty = remaining_qty - :qty … WHERE remaining_qty >= :qty` too, and re-reads the book
if 0 rows changed. Partitioning gives ordering and a contention-free path; the conditional WHERE
is the belt to its braces. Cancel and modify, which must read the order to know what reservation
to release, are compare-and-set on `version` with a short retry loop.

## What the CHECK constraints do and do not catch

`accounts`, `portfolio` and `orders` have CHECK constraints (non-negative cash, shares,
`remaining_qty BETWEEN 0 AND qty`). They are the last safety net: an UNSAFE double sale that
drives the seller's `reserved_qty` negative is rejected by the database. They do **not** catch a
lost update that stays within range — `remaining_qty = 0` written twice is perfectly valid —
which is why the UNSAFE tests seed the seller with a large reservation (as if it had other open
SELL orders): with the net gone, the lost update is visible as 200 shares sold out of 100.

## Why an in-memory lock is not enough

`TwoInstancesIT` boots additional S1 contexts on the same database. With UNSAFE writes wrapped
in a `ReentrantLock`, a single instance shows 0 violations in 50 rounds: the lock serialises its
two request threads and the bug is invisible. With two instances, each JVM has its own lock
object, nothing serialises the two transactions, and all 50 rounds oversell. The same test with
SAFE writes across the two instances: 0 violations, because the row lock lives in PostgreSQL.

## Other mechanisms in S1 trading

* **Transactional outbox** — events are rows in `event_outbox` written in the business
  transaction; Cezar's relay publishes them with `SELECT … FOR UPDATE SKIP LOCKED`, so no event is
  lost, none is sent for a rolled-back change, and two relays never send the same row.
* **Reservations** — a LIMIT BUY reserves `price × qty` cash, a SELL reserves `qty` shares, in
  atomic conditional updates (`cash_balance − reserved_cash >= :amount`); the ledger can never let
  money or shares go negative, independently of the order logic.
* **Idempotent commands** — `PlaceOrder` is skipped for non-`PENDING` orders; `CancelOrder` for
  non-cancellable ones; a `PendingOrderSweeper` re-publishes `PlaceOrder` for orders left
  `PENDING` if Kafka was unreachable right after the commit.
* **Live pushes after commit** — `TradingEvents` registers STOMP pushes as
  `afterCommit` synchronisations, so the web app never sees a trade that rolled back.

## Reproducing

```bash
cd s1-exchange
mvn -Pit verify                       # everything, 200 rounds (≈ 12 min with Docker Desktop)
mvn -Pit verify -Dconcurrency.rounds=20 -Dit.test='SafeConcurrencyIT,UnsafeConcurrencyIT'
mvn -Pit verify -Dit.test=TwoInstancesIT
```

Each run prints one `CONCURRENCY-RESULT …` line per scenario.
