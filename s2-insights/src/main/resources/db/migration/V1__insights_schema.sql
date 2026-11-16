-- S2 read database "insights". Only S2 connects to it. Built entirely from Kafka events.
-- Flyway: s2-insights/src/main/resources/db/migration/V1__insights_schema.sql (owner: Cristi)

CREATE TABLE processed_events (
    event_id      UUID        PRIMARY KEY,
    topic         VARCHAR(60) NOT NULL,
    processed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE trades_view (
    trade_id        BIGINT        PRIMARY KEY,
    symbol          VARCHAR(10)   NOT NULL,
    buyer_user_id   BIGINT        NOT NULL,
    seller_user_id  BIGINT        NOT NULL,
    qty             BIGINT        NOT NULL,
    price           NUMERIC(18,4) NOT NULL,
    total_value     NUMERIC(18,2) NOT NULL,
    executed_at     TIMESTAMPTZ   NOT NULL
);
CREATE INDEX idx_trades_view_symbol_time ON trades_view (symbol, executed_at DESC);

CREATE TABLE daily_stock_stats (
    symbol            VARCHAR(10)   NOT NULL,
    day               DATE          NOT NULL,
    open              NUMERIC(18,4),
    high              NUMERIC(18,4),
    low               NUMERIC(18,4),
    close             NUMERIC(18,4),
    vwap              NUMERIC(18,4),
    volume            BIGINT        NOT NULL DEFAULT 0,
    trade_count       BIGINT        NOT NULL DEFAULT 0,
    orders_placed     BIGINT        NOT NULL DEFAULT 0,
    orders_cancelled  BIGINT        NOT NULL DEFAULT 0,
    orders_rejected   BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (symbol, day)
);

CREATE TABLE bot_daily_stats (
    bot_user_id     BIGINT        NOT NULL,
    symbol          VARCHAR(10)   NOT NULL,
    day             DATE          NOT NULL,
    quotes_placed   BIGINT        NOT NULL DEFAULT 0,
    fills           BIGINT        NOT NULL DEFAULT 0,
    bought_qty      BIGINT        NOT NULL DEFAULT 0,
    sold_qty        BIGINT        NOT NULL DEFAULT 0,
    end_inventory   BIGINT        NOT NULL DEFAULT 0,
    realized_pnl    NUMERIC(18,2) NOT NULL DEFAULT 0,
    avg_spread_bps  NUMERIC(8,2),
    PRIMARY KEY (bot_user_id, symbol, day)
);

CREATE TABLE incidents_view (
    incident_id  BIGINT      PRIMARY KEY,
    type         VARCHAR(30) NOT NULL,
    severity     VARCHAR(6)  NOT NULL,
    symbol       VARCHAR(10),
    order_id     BIGINT,
    message      TEXT        NOT NULL,
    status       VARCHAR(12) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_incidents_view_time ON incidents_view (created_at DESC);

CREATE TABLE bot_configs_view (
    config_id      BIGINT       PRIMARY KEY,
    bot_user_id    BIGINT       NOT NULL,
    symbol         VARCHAR(10)  NOT NULL,
    enabled        BOOLEAN      NOT NULL,
    spread_bps     INT          NOT NULL,
    quote_qty      BIGINT       NOT NULL,
    max_inventory  BIGINT       NOT NULL,
    skew_factor    NUMERIC(6,3) NOT NULL,
    refresh_ms     INT          NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL
);
