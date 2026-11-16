# features/trading — Stock page (owner: Patric)

The stock page: live order book, offers list with **Buy** and the "price changed, confirm?"
dialog (409 flow), and the order form. Uses `<PriceChart symbol>` from `features/insights`.

```
index.ts            public surface: tradingRoutes, StockPage, OrderBook, OffersList, OrderForm
routes.tsx          /stocks/:symbol   (Cezar's router imports tradingRoutes once)
integration.ts      the ONLY file that imports from outside this folder (see below)
types.ts            TypeScript mirror of contracts/s1/trading.yaml
api/tradingApi.ts   fetch wrappers + TradingApiError (RFC 9457 problem details)
hooks/              TanStack Query hooks, STOMP-driven live updates
components/         OrderBook, OffersList, BuyOfferDialog, OrderForm (Mantine only)
pages/StockPage.tsx
```

## What this folder expects from the rest of the app

Everything external is re-exported through `integration.ts`, so if a name differs on Cezar's or
Cristi's side, that single file is the only thing to adjust.

| Import | Owner | Expected shape |
|---|---|---|
| `useAuth()` from `@/app/auth` | Cezar | `{ token: string \| null; user: { id: number; username: string; role: 'TRADER' \| 'ADMIN' \| 'MARKET_MAKER' } \| null }` |
| `useStomp<T>(destination, onMessage, enabled?)` from `@/app/stomp` | Cezar | subscribes to a STOMP destination on `/ws` while mounted; `onMessage` receives the parsed JSON body |
| `PriceChart` from `@/features/insights` | Cristi | `({ symbol }: { symbol: string }) => JSX.Element` |
| `VITE_S1_URL` (env, optional) | Cezar | base URL of S1; default `/api/s1` (nginx proxy) |

Mantine (`@mantine/core`), `@tanstack/react-query` and `react-router-dom` are the only
libraries used; no new npm dependency is needed.

## Live updates (destinations from contracts/s1/ops.yaml)

| Destination | Payload | Used for |
|---|---|---|
| `/topic/book/{symbol}` | `Book` (same shape as `GET /api/stocks/{symbol}/book`) | replaces the book, refreshes offers |
| `/topic/trades/{symbol}` | `TradeExecuted` payload | last-trades tape, refreshes offers |
| `/topic/prices/{symbol}` | `PriceUpdated` payload | last price in the header |
| `/user/queue/orders` | `OrderEvent` payload | status of the orders placed from this page |

## The 409 flow (concurrency cases 1 and 2)

`POST /api/offers/{orderId}/buy { qty, maxPrice, seenVersion }`:

* **200** → trade done; the dialog shows the trade and closes.
* **409 `PRICE_CHANGED`** → `error.offer` is the current offer. The dialog switches to
  *"Price changed to 12.00 (was 10.00). Buy N at 12.00?"*; confirming resends with the new
  `seenVersion` and `maxPrice`.
* **409 `NO_LONGER_AVAILABLE`** → "This offer is gone / only N left"; the offers list refreshes.
* **422 `INSUFFICIENT_FUNDS`** → shown inline.
