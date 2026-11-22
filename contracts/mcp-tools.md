# MCP tools (owner: Paul)

Server: `mcp-server/`, Spring AI MCP Server (WebMVC, SSE transport), port 8083.

* Stream: `GET http://mcp-server:8083/sse`, messages: `POST /mcp/message?sessionId=...`.
* MCP Inspector: transport "SSE", URL `http://localhost:8083/sse`.
* The server has no database. Reads go to S2 with `X-Api-Key: $SERVICE_API_KEY`. The only side effect is
  the report email (SMTP to `MAIL_HOST`, Mailpit in Docker).

| # | Tool | Kind | Parameters | Returns | Backed by |
|---|---|---|---|---|---|
| 1 | `get_market_summary` | read | `date` (YYYY-MM-DD) | per stock: open, high, low, close, changePct, vwap, volume, tradeCount, ordersPlaced, ordersCancelled, ordersRejected | `GET /api/stats/daily?date=` |
| 2 | `get_incidents` | read | `since` (date or instant), `type?` | groups `{type, symbol, count, exampleMessage}` | `GET /api/incidents/summary?since=` |
| 3 | `get_bot_performance` | read | `date`, `symbol?` | per stock: quotesPlaced, fills, boughtQty, soldQty, endInventory, realizedPnl | `GET /api/bots/stats?date=` |
| 4 | `get_bot_configs` | read | `symbol?` | configId, enabled, spreadBps, quoteQty, maxInventory, skewFactor, refreshMs | `GET /api/bots/configs` |
| 5 | `get_trades` | read | `symbol`, `since` | up to 200 trades, newest first | `GET /api/trades?symbol=&since=` |
| 6 | `propose_bot_config_change` | propose | `configId`, `spreadBps?`, `quoteQty?`, `maxInventory?`, `enabled?`, `reason` | `{id, configId, symbol, current, proposed, reason, applyUrl}`; **writes nothing** | `GET /api/bots/configs` for the current values |
| 7 | `send_report` | action | `subject`, `markdown`, `recommendations[]` | `{messageId, recipients, sent}` | SMTP |

## Validation in `propose_bot_config_change`

The same limits as S1's `PUT /api/admin/bots/{id}`, so an admin can always save a recommendation:
`spreadBps` 1 to 2000, `quoteQty` and `maxInventory` positive, `quoteQty` not above `maxInventory`, at least one
changed value, and a non-empty reason. An invalid proposal is returned to the agent as a tool error.

## Apply link

`{WEB_URL}/admin/bots/{configId}?spreadBps=75&reason=...` opens Admin > Bot settings with the new values filled in
and a "recommended by the nightly report" notice. Nothing changes until the admin presses Save; S1 then
publishes `BotConfigChanged` and the market maker re-quotes within one tick.
