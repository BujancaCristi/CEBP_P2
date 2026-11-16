# Frontend plan (web-client): full scope, planning only

Status on 16 Nov 2026. This is a plan, not an implementation. It covers every screen in
Application_Description section 8, the shared shell, and the conventions all four owners follow.
Feature status and the seam rules are in `docs/frontend-walkthrough.md`.

## 1. Goals and non-goals

Goals: a trader can register, log in, see the market live, buy and sell, watch the portfolio and
history, and read statistics. An admin can change bot settings from a report's link, handle incidents
and run the report. The UI must make the concurrency behaviour visible: the 409 confirm dialog for a
changed price, and "no longer available" for a taken offer.

Non-goals: the market maker has no UI (it is headless). No charting beyond the price chart and
movers. No mobile-specific layout beyond responsive Mantine defaults. No offline mode.

## 2. Users and roles

| Role (from JWT role claim) | Sees | Notes |
|---|---|---|
| TRADER | Market, Stock, Portfolio, My orders, My trades, Statistics | Default after register |
| ADMIN | Everything a trader sees, plus Admin: Bot settings, Admin: Incidents, Admin: Reports | Seeded user |
| MARKET_MAKER | Nothing in the web app | Bot is headless; the menu must not offer it a login |

Menu rule: the menu is built from the route table (section 4), filtered by role. A route whose role
is not met redirects to Market with a notice, not to a blank page.

## 3. Shared shell (owner: Cezar)

### 3.1 Providers, outermost first

1. Mantine provider with the theme (section 6.4).
2. TanStack QueryClientProvider with defaults: one retry on network errors, no retry on 4xx, stale
   time 10 s for market data and 60 s for everything else.
3. Auth provider: holds the token and the user, exposes useAuth() with the shape that
   features/trading/integration.ts expects (token, and user with id, username, role), plus login,
   register and logout.
4. STOMP provider: one connection to /ws per logged-in session, shared by all features, exposes
   useStomp(destination, onMessage, enabled?).
5. React Router with the layout route.

### 3.2 Layout

- Top bar: app name, the logged-in user and role, logout.
- Side navigation built from the route table, role-filtered. Admin entries grouped under an
  "Admin" heading.
- Content area with a page title. The app is shallow, so no breadcrumbs.
- Global notification area (Mantine notifications) for background errors such as a dropped STOMP
  connection.
- A full-page loading state while the session is being restored.

### 3.3 Session handling

- Login returns a JWT. Storage is an open decision (section 11).
- Every request sends the token in the Authorization header as a Bearer token.
- A 401 from any API clears the session and sends the user to Login, with the original path kept in
  a returnTo query parameter.
- A 403 shows "not allowed for your role" on the page itself, not a redirect.
- Logout clears the token, closes the STOMP connection and clears the query cache.

### 3.4 API layer

- Clients are generated from the joined S1 spec (the three S1 contracts joined with redocly) and
  from the S2 spec with openapi-typescript. Generated files live in src/api/. Features never
  hand-write types for endpoints that exist in a spec.
- Feature wrappers (tradingApi.ts, insightsApi.ts) stay as thin functions over the generated client,
  so a changed route is fixed in one place.
- Errors are RFC 9457 problem details. One shared error class carries the status, the code and the
  problem body. The trading feature already does this (TradingApiError); the shell provides the same
  class for every feature to reuse.
- Query keys follow one pattern: service, resource, then parameters. Example: the order book of a
  stock is keyed by the S1 service, the book resource and the symbol. Invalidation rules are listed
  per page in section 5.

### 3.5 Realtime

- The STOMP hook subscribes on mount and unsubscribes on unmount, and only while the user is logged
  in.
- Payloads are the event records from contracts/events/, so the web app sees one shape from REST,
  Kafka and STOMP. Destinations come from the trading README and from contracts/s1/ops.yaml, which
  Paul has yet to write.
- Reconnect: the library reconnects automatically. After a reconnect the app invalidates the market
  and order queries once, so it does not show stale data after a gap.
- How the token travels on the STOMP connect frame is an open decision (section 11).

## 4. Route table

| Path | Role | Feature | Page | Main data |
|---|---|---|---|---|
| /login | anonymous | platform | Login | POST /api/auth/login |
| /register | anonymous | platform | Register | POST /api/auth/register |
| / | TRADER, ADMIN | platform | Market (redirect target) | GET /api/stocks, live prices, MarketMovers |
| /stocks/:symbol | TRADER, ADMIN | trading | Stock page | book, offers, trades, PriceChart |
| /portfolio | TRADER, ADMIN | platform | Portfolio | portfolio endpoint, live prices |
| /orders | TRADER, ADMIN | platform | My orders | GET /api/orders/mine, cancel and modify |
| /trades | TRADER, ADMIN | platform | My trades | GET /api/trades/mine |
| /statistics | TRADER, ADMIN | insights | Statistics | GET /api/stats/daily (S2) |
| /admin/bots | ADMIN | admin | Bot settings | GET /api/admin/bots, PUT /api/admin/bots/{id} |
| /admin/incidents | ADMIN | admin | Incidents | GET /api/admin/incidents, PATCH /api/admin/incidents/{id} |
| /admin/reports | ADMIN | insights | Reports | POST /api/agent/run (S2) |

The Statistics page is reached from the menu at /statistics, and the Reports page at /admin/reports.

## 5. Page specifications

Each page lists its data, its states (loading, empty, error, and feature-specific ones), its
actions, and the queries it invalidates.

### 5.1 Login (platform, Cezar)

- Form: username and password. Submit is disabled while the request is pending.
- On success: store the session, go to returnTo or to Market.
- Errors: a 401 shows "wrong username or password" inline. Network errors show a generic message.
- No "remember me" in the first version.

### 5.2 Register (platform, Cezar)

- Form: username, email, password, password confirmation. Client-side checks mirror the server rules,
  to be taken from contracts/s1/platform.yaml.
- On success: a new trader account with the demo cash balance. Log in immediately and go to Market,
  with a one-time welcome notice that shows the balance.
- Errors: a 409 for a taken username is shown on the username field.

### 5.3 Market (platform, Cezar)

- Table: symbol, company, last price, change against previous close, volume today, best bid and best
  ask. Sortable by column. Filter by name or symbol.
- Live: prices update from the price topic of each listed stock. Bid and ask update from the book topic.
- A side panel shows MarketMovers from the insights feature.
- Rows link to the stock page.
- States: loading skeleton; empty ("no stocks listed"); error with retry; a stale-data badge when the
  STOMP connection is down.
- Acceptance: every stock shows a bid and an ask once Paul's bot runs (integration checklist item).

### 5.4 Stock page (trading, Patric; written)

- Header: symbol, company, last price, change. PriceChart from the insights feature.
- Order book: bids and asks grouped by price level, live from the book topic.
- Offers: open SELL orders with total price and version. Buy opens the buy dialog.
- Buy dialog: quantity, and a max price pre-filled from the price the user saw. Confirm button.
  A 409 PRICE_CHANGED switches the dialog to "Price changed to X, confirm?". A 409
  NO_LONGER_AVAILABLE closes it with a notice and refreshes offers. A 422 INSUFFICIENT_FUNDS shows
  inline.
- Order form: side, type (LIMIT or MARKET), price (LIMIT only, on the stock's tick size), quantity.
  After submit the order shows as PENDING until the user's order queue reports OPEN, FILLED or REJECTED.
- Recent trades tape from the trade topic.
- Invalidates: book, offers and my orders after every write.

### 5.5 Portfolio (platform, Cezar)

- Summary cards: cash balance, reserved cash, total equity (cash plus positions at last price).
- Positions table: symbol, quantity, reserved quantity, average cost, last price, market value,
  unrealised P&L (amount and percent).
- Live: last price comes from the market subscription, so totals recalculate without a refetch.
- Dependency: valuation should come from S1's portfolio endpoint. Open decision (section 11).
- States: empty ("no positions yet, buy on the Market page"), loading, error.

### 5.6 My orders (platform, Cezar)

- Filters: status (OPEN, PARTIALLY_FILLED, FILLED, CANCELLED, REJECTED), symbol, side.
- Table: id, time, symbol, side, type, price, quantity, remaining, status, source, reason.
- Actions: cancel and modify, only for open orders. Both return 202, so the row shows "change
  pending" until the order queue confirms.
- Pagination with the page shape from contracts/s1/trading.yaml.
- Invalidates: my orders and the portfolio.

### 5.7 My trades (platform, Cezar)

- Table: time, symbol, side from the user's point of view, quantity, price, total.
- Filters: symbol and date range.
- Pagination as in 5.6. Data from GET /api/trades/mine.

### 5.8 Statistics (insights, Cristi; written)

- Date picker and a table per stock: open, high, low, close, change, VWAP, volume, trades, placed,
  cancelled, rejected. Empty state for a day with no activity.
- Not in v1: per-stock drill-down.

### 5.9 Admin: Bot settings (admin, Paul)

- Table: one row per stock with the bot's settings: enabled, spread in basis points, quote quantity,
  max inventory, skew factor, refresh interval. Edit per row through a form.
- Pre-fill from the report's link: a URL such as /admin/bots/3?spreadBps=80 sets the form fields and
  shows a "recommended by the nightly report" notice. Nothing saves until the admin presses Save.
- Validation mirrors the propose tool's checks, so the admin cannot save a value the report itself
  would have refused.
- Save: PUT /api/admin/bots/{id}. On success the table refreshes and the notice clears. The bot picks
  up the change through the BotConfigChanged event.
- States: loading, empty (no bots configured), error, saving.

### 5.10 Admin: Incidents (admin, Paul)

- Filters: status, severity, type, symbol, time window.
- Table: time, type, severity, symbol, order, user, message, status.
- Actions: change status (acknowledge, resolve) through PATCH /api/admin/incidents/{id}.
- Live: new incidents appear through a realtime channel if ops.yaml defines one. Otherwise the page
  polls every 30 seconds.
- This page reads from S1, which holds the live status. Incident statistics stay on S2.

### 5.11 Admin: Reports (insights, Cristi; written)

- Date picker (defaults to yesterday), "Run report now" button, progress text while the agent runs
  (up to a minute), and a result panel with the markdown report and the sent or failed status.
- The report is not stored, so reloading the page loses it. The page says so, as the application
  description requires.

## 6. Cross-cutting behaviour

### 6.1 Error mapping (all pages)

| Status | Shown as |
|---|---|
| 401 | Session expired; redirect to Login with returnTo |
| 403 | "Not allowed for your role" on the page |
| 404 | "Not found", with a link back to Market |
| 409 | Page-specific (buy dialog, username taken); otherwise a conflict notice |
| 422 | Field errors from the problem body, or a page-specific message |
| 5xx and network | Generic error with retry; never a raw stack trace or problem title |

### 6.2 Formatting

- Prices: two decimals in tables, more when the stock's tick size needs it. Decide from the tick size,
  not a fixed count.
- Money totals: two decimals. The currency (EUR) appears in headers, not in every cell.
- Percentages: sign and two decimals, green for positive and red for negative. The sign is shown too,
  so colour is not the only cue.
- Timestamps: the viewer's local time. "Today" and the date pickers use the exchange day boundary
  (Europe/Bucharest).
- Quantities: thousand separators.

### 6.3 Accessibility and layout

- Every table has a header row and a caption. Icon-only buttons have labels.
- Keyboard: the buy dialog traps focus and closes on Escape.
- Layout works down to phone width with a 16 px gutter. Side navigation collapses to a menu.

### 6.4 Theme

- One Mantine theme file, owned by Cezar. Positive, negative and neutral colours are tokens used
  everywhere. Dark mode follows the system setting.

## 7. Ownership of shared pieces

| Piece | Owner | Consumers |
|---|---|---|
| Providers, router, layout, navigation | Cezar | all |
| useAuth and session handling | Cezar | all |
| useStomp and the realtime provider | Cezar | trading, platform, admin |
| Generated API clients and the error class | Cezar | all |
| Theme and formatters | Cezar | all |
| Trading feature | Patric | Market links to it |
| Insights feature | Cristi | trading (PriceChart), platform (MarketMovers), admin (Reports) |
| Admin feature | Paul | none |

A shared formatter needed by two features goes into the shell, not into a third feature.

## 8. Testing plan

- Unit and component tests: Vitest with Testing Library. Each feature tests its own components and
  hooks. Cover the error paths: the 409 dialog, inline 422, and the 401 redirect.
- API mocks: MSW in tests, and Prism serving the joined specs during development. Both read the same
  contracts, so the mocks cannot drift from the spec.
- End to end: Playwright against docker compose. One script per demo item: register and see the
  starting balance; place and fill an order; buy a changed offer and see the dialog; an admin applies
  a report link and the bot's spread changes; statistics show S2 data.
- Accessibility: an automated axe check on each page during the end-to-end run.
- Build gate: tsc with no emit and vite build must pass in CI for web-client changes.

## 9. Build and delivery

- Vite and TypeScript in the shell project. Scripts: dev, build, test, e2e, generate-api.
- The generate-api script regenerates clients from the specs. CI fails if the generated files are
  out of date.
- nginx serves the build and proxies the S1 and S2 API paths and the WebSocket path.
- The Dockerfile is a multi-stage build: node for the build, nginx for serving.
- Environment: the S1 and S2 base URLs default to the proxy paths and can be overridden for local work.

## 10. Milestones (aligned with Work_Split)

| Phase | Frontend work | Owner |
|---|---|---|
| 0, days 1 to 3 | Vite project, shell, providers, route table with placeholder pages, generated clients from draft specs | Cezar |
| 0, days 1 to 3 | Feature folders with placeholder routes and index files | each owner |
| 1, weeks 1 to 2 | Build against MSW and Prism mocks: Trading pages, Statistics, Reports, Market, Portfolio, My orders, My trades, Login and Register, Admin pages | each owner |
| 2, week 3 | Switch from mocks to real S1 and S2; fix mismatches with the contracts | each owner |
| 3, week 4 | Playwright demo script, accessibility pass, build and delivery in compose | Cezar and all |

## 11. Open decisions

1. Token storage: memory only (logged out on refresh), or sessionStorage (survives refresh, readable
   by scripts). Recommended: sessionStorage with an expiry check.
2. STOMP authentication: the token in the connect frame or in the URL. Depends on the S1 WebSocket
   configuration, owned by Paul.
3. Portfolio valuation: should S1 return market value and P&L, or should the web app compute them
   from live prices. Recommended: S1 returns them, so the numbers match the server.
4. Incident live updates: a STOMP destination or polling. Depends on ops.yaml.
5. Page size for orders and trades: 20 per page as a starting value.
6. Bot settings editing: per row or a full form. Recommended: a form per stock, opened from the row.
7. Register bonus text: confirm the starting balance is shown in the UI, since it comes from the seed.

## 12. Gaps in the contracts this plan depends on

- contracts/s1/platform.yaml (Cezar) and contracts/s1/ops.yaml (Paul) do not exist yet. Pages 5.1 to
  5.7 and 5.9 to 5.10 cannot have generated clients until they do.
- The portfolio endpoint in the application description has no valuation fields. See section 11, item 3.
- The STOMP destinations for incidents and bot settings are named in the description but not yet in a
  contract file.
