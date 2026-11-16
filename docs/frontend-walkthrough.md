# Frontend walkthrough (web-client)

Status on 16 Nov 2026. The web client is not runnable yet: the Vite project, the app shell and the
router do not exist. What exists are the feature folders, written against the contracts.

## 1. Layout and ownership

```
web-client/src/
  app/          Cezar   router, layout, auth context (useAuth), useStomp hook
  api/          Cezar   generated API clients
  features/
    platform/   Cezar   Login, Register, Market, Portfolio, My orders, My trades
    trading/    Patric  Stock page, order book, offers + buy dialog, order form   (written)
    insights/   Cristi  PriceChart, MarketMovers, Statistics page, Admin > Reports (written)
    admin/      Paul    Admin > Bot settings, Admin > Incidents                    (not started)
```

Each feature has a `routes.tsx` that exports its pages. Cezar's router imports all four once, so
nobody edits the router later. A feature imports another feature only through that feature's
`index.ts`.

## 2. The seam rule

Every feature has an `integration.ts` file. It is the only file that imports from outside its
folder. If a name on another person's side differs, that one file is the only thing to change.

| Feature | Imports from outside | Expected from |
|---|---|---|
| trading | `useAuth` from `@/app/auth`, `useStomp` from `@/app/stomp`, `PriceChart` from `@/features/insights` | Cezar, Cezar, Cristi |
| insights | `useAuth` from `@/app/auth` | Cezar |

Environment variables: `VITE_S1_URL` (default `/api/s1`) and `VITE_S2_URL` (default `/api/s2`). Both
go through the nginx proxy in compose.

## 3. Trading (Patric, written)

Files in `features/trading/`: `pages/StockPage.tsx` (route `stocks/:symbol`), `components/` (OrderBook,
OffersList, BuyOfferDialog, OrderForm), `hooks/` (useOrderBook, useOffers, usePlaceOrder, useBuyOffer,
useMyOrderUpdates), `api/tradingApi.ts`, `types.ts` (mirrors `contracts/s1/trading.yaml`).

Behaviour that matters:
- Buy from an offer sends `POST /api/offers/{id}/buy` with `seenVersion` and `maxPrice`.
- A 409 `PRICE_CHANGED` opens the confirm dialog with the new price. A 409 `NO_LONGER_AVAILABLE` refreshes the offers.
- Live updates come from STOMP destinations `/topic/book/{symbol}`, `/topic/trades/{symbol}`,
  `/topic/prices/{symbol}` and `/user/queue/orders`.

## 4. Insights (Cristi's contract, written by me for Cristi to take over)

Files in `features/insights/`:
- `components/PriceChart.tsx`: `<PriceChart symbol="ACME" />`, daily close with 7, 30 and 90 day ranges.
- `components/MarketMovers.tsx`: `<MarketMovers />`, gainers, losers and most traded for the day.
- `pages/StatisticsPage.tsx`: daily table per stock with a date picker.
- `pages/ReportsPage.tsx`: "Run report now", shows the returned markdown.
- `api/insightsApi.ts` and `types.ts`, mirroring `contracts/s2-openapi.yaml`.

Dependencies: Mantine, TanStack Query, Recharts and react-markdown. No other libraries.

## 5. Platform (Cezar, not started)

Login, Register, the Market page (uses `<MarketMovers />`), Portfolio, My orders (cancel and modify
through the trading API) and My trades. The app shell with the role-based menu also belongs here.

## 6. Admin (Paul, not started)

Bot settings (editable, pre-filled from the `?spreadBps=` query of the report's Apply link) and
Incidents (list, filters, status change). Contract in `contracts/s1/ops.yaml` once it exists.

## 7. What is missing before anything runs

1. The Vite + TypeScript + Mantine project with `package.json` and the nginx config (Cezar).
2. `app/auth` (`useAuth`) and `app/stomp` (`useStomp`), which the trading and insights features import.
3. The router that mounts each feature's `routes.tsx`.
4. The generated API clients in `api/`.
5. The platform and admin features.

Until items 1 to 3 exist, no feature can be type-checked or rendered.

## 8. Checking the work

Once the Vite project exists: `npm run build` for type errors, then `npm run dev` with the S1 and S2
services running. Confirm the stock page loads the order book, a buy shows the 409 dialog when the price
changed, and the Statistics page loads data from S2.
