# MCP Tools Specification

**Owner: Paul**  
Transport: MCP over HTTP at `http://mcp-server:8083/mcp` (Spring AI MCP Server, WebMVC).  
Tested with MCP Inspector or Claude Desktop.

---

## Overview

The MCP Server exposes **7 tools** for the AI nightly report agent (running in S2):
- **5 read tools** (querying S2's statistics endpoints using `X-Api-Key: SERVICE_API_KEY`)
- **1 propose tool** (validates parameters and generates an administrative recommendation URL without writing to DB)
- **1 action tool** (renders an HTML email with recommendation apply links and sends it via SMTP to Mailpit)

Neither the agent nor the MCP server ever writes directly to any database.

---

## Tool 1: `get_market_summary`

* **Kind**: Read
* **Description**: Returns daily market overview statistics for all stocks or a specific date.
* **Arguments**:
  - `date` (`string`, required): ISO date `YYYY-MM-DD` (defaults to target date / yesterday).
* **Source**: S2 `GET /api/stats/daily?date={date}`
* **Returns**: JSON array of objects:
  ```json
  [
    {
      "symbol": "ACME",
      "day": "2026-11-21",
      "open": 100.50,
      "high": 105.00,
      "low": 99.00,
      "close": 103.20,
      "vwap": 102.15,
      "volume": 5200,
      "tradeCount": 42,
      "ordersPlaced": 120,
      "ordersCancelled": 15,
      "ordersRejected": 3,
      "changePct": 2.68
    }
  ]
  ```

---

## Tool 2: `get_incidents`

* **Kind**: Read
* **Description**: Returns incident summaries and frequencies grouped by type and stock since a given timestamp.
* **Arguments**:
  - `since` (`string`, required): ISO timestamp `YYYY-MM-DDTHH:mm:ssZ`.
  - `type` (`string`, optional): Filter by incident type (e.g. `BOT_INVENTORY_LIMIT`, `CONCURRENCY_CONFLICT`).
* **Source**: S2 `GET /api/incidents/summary?since={since}` (or `/api/incidents`)
* **Returns**: JSON array of incident groups:
  ```json
  [
    {
      "type": "BOT_INVENTORY_LIMIT",
      "symbol": "ACME",
      "count": 4,
      "exampleMessage": "Market maker reached maximum inventory limit of 2000 shares"
    }
  ]
  ```

---

## Tool 3: `get_bot_performance`

* **Kind**: Read
* **Description**: Returns market maker trading performance and P&L per stock for a specific day.
* **Arguments**:
  - `date` (`string`, required): ISO date `YYYY-MM-DD`.
  - `symbol` (`string`, optional): Stock symbol filter.
* **Source**: S2 `GET /api/bots/stats?date={date}&symbol={symbol}`
* **Returns**: JSON array of bot performance objects:
  ```json
  [
    {
      "botUserId": 2,
      "symbol": "ACME",
      "day": "2026-11-21",
      "quotesPlaced": 350,
      "fills": 45,
      "boughtQty": 1200,
      "soldQty": 1000,
      "endInventory": 200,
      "realizedPnl": -150.25,
      "avgSpreadBps": 85.0
    }
  ]
  ```

---

## Tool 4: `get_bot_configs`

* **Kind**: Read
* **Description**: Returns current bot configurations for all stocks or a specific symbol.
* **Arguments**:
  - `symbol` (`string`, optional): Stock symbol filter.
* **Source**: S2 `GET /api/bots/configs?symbol={symbol}`
* **Returns**: JSON array of current bot configurations:
  ```json
  [
    {
      "configId": 3,
      "botUserId": 2,
      "symbol": "ACME",
      "enabled": true,
      "spreadBps": 80,
      "quoteQty": 50,
      "maxInventory": 2000,
      "skewFactor": 0.5,
      "refreshMs": 2000,
      "updatedAt": "2026-11-20T18:00:00Z"
    }
  ]
  ```

---

## Tool 5: `get_trades`

* **Kind**: Read
* **Description**: Returns recent individual trade executions for detecting anomalies or price volatility.
* **Arguments**:
  - `symbol` (`string`, required): Stock symbol.
  - `since` (`string`, optional): ISO timestamp (default 24h ago).
  - `limit` (`integer`, optional): Max trades to return (default 100).
* **Source**: S2 `GET /api/trades?symbol={symbol}&since={since}&limit={limit}`
* **Returns**: JSON array of trade records:
  ```json
  [
    {
      "tradeId": 101,
      "symbol": "ACME",
      "buyerUserId": 5,
      "sellerUserId": 2,
      "qty": 50,
      "price": 102.50,
      "totalValue": 5125.00,
      "executedAt": "2026-11-21T14:30:15Z"
    }
  ]
  ```

---

## Tool 6: `propose_bot_config_change`

* **Kind**: Propose
* **Description**: Validates proposed bot configuration adjustments against current values and generates an administrative recommendation with an `applyUrl`. **Writes nothing to any database.**
* **Arguments**:
  - `configId` (`integer`, required): ID of the bot config to modify.
  - `spreadBps` (`integer`, optional): Proposed new spread in basis points (1–1000).
  - `quoteQty` (`integer`, optional): Proposed quote quantity (> 0).
  - `maxInventory` (`integer`, optional): Proposed max inventory (> 0).
  - `skewFactor` (`number`, optional): Proposed inventory skew factor.
  - `refreshMs` (`integer`, optional): Proposed refresh interval in milliseconds (>= 200).
  - `enabled` (`boolean`, optional): Proposed active status.
  - `reason` (`string`, required): Justification explaining why this adjustment is recommended.
* **Behavior**:
  1. Queries S2 `GET /api/bots/configs` to find the existing config by `configId`.
  2. Validates ranges and identifies differences.
  3. Constructs `applyUrl`, e.g.:
     `http://localhost:3000/admin/bots?configId=3&spreadBps=120&reason=Bot+lost+money`
  4. Returns recommendation structure.
* **Returns**:
  ```json
  {
    "id": "rec-1a2b3c",
    "configId": 3,
    "symbol": "ACME",
    "current": {
      "spreadBps": 80,
      "quoteQty": 50,
      "maxInventory": 2000,
      "enabled": true
    },
    "proposed": {
      "spreadBps": 120,
      "quoteQty": 50,
      "maxInventory": 2000,
      "enabled": true
    },
    "reason": "Bot lost money on stock that moved > 5%; widened spread by 1.5x",
    "applyUrl": "/admin/bots?configId=3&spreadBps=120"
  }
  ```

---

## Tool 7: `send_report`

* **Kind**: Action
* **Description**: Generates an HTML report email incorporating market summary findings and recommendation action buttons, and dispatches it via SMTP to `REPORT_RECIPIENTS`.
* **Arguments**:
  - `subject` (`string`, required): Email subject line.
  - `markdown` (`string`, required): Markdown summary of market activities, findings, and analysis.
  - `recommendations` (`array`, optional): List of recommendation objects produced by `propose_bot_config_change`.
* **Behavior**:
  - Converts markdown into styled HTML.
  - Inserts a responsive table of recommendations with prominent "Apply in Admin" links pointing to the web client.
  - Connects to SMTP server (`MAIL_HOST:MAIL_PORT`, typically Mailpit on port 1025) and delivers email.
* **Returns**:
  ```json
  {
    "messageId": "msg-9f8e7d-12345",
    "sent": true,
    "recipientCount": 1,
    "timestamp": "2026-11-22T02:00:15Z"
  }
  ```
