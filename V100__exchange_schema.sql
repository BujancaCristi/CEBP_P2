-- S1 main database "exchange". Only S1 connects to it.
-- Flyway: s1-exchange/src/main/resources/db/migration/V100__exchange_schema.sql (owner: Patric)

CREATE TABLE users (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username       VARCHAR(50)  NOT NULL UNIQUE,
    email          VARCHAR(255) NOT NULL UNIQUE,
    password_hash  VARCHAR(100) NOT NULL,
    role           VARCHAR(20)  NOT NULL CHECK (role IN ('TRADER', 'ADMIN', 'MARKET_MAKER')),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE accounts (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        BIGINT        NOT NULL UNIQUE REFERENCES users (id),
    cash_balance   NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (cash_balance >= 0),
    reserved_cash  NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (reserved_cash >= 0),
    currency       CHAR(3)       NOT NULL DEFAULT 'EUR',
    status         VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'FROZEN')),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version        BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT reserved_within_balance CHECK (reserved_cash <= cash_balance)
);

CREATE TABLE companies (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name         VARCHAR(120) NOT NULL UNIQUE,
    sector       VARCHAR(60)  NOT NULL,
    country      VARCHAR(60),
    description  TEXT,
    listed_at    DATE         NOT NULL DEFAULT CURRENT_DATE
);

CREATE TABLE stocks (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id          BIGINT        NOT NULL REFERENCES companies (id),
    symbol              VARCHAR(10)   NOT NULL UNIQUE,
    value               NUMERIC(18,4) NOT NULL CHECK (value > 0),
    previous_close      NUMERIC(18,4) NOT NULL CHECK (previous_close > 0),
    shares_outstanding  BIGINT        NOT NULL CHECK (shares_outstanding > 0),
    tick_size           NUMERIC(10,4) NOT NULL DEFAULT 0.01,
    active              BOOLEAN       NOT NULL DEFAULT TRUE,
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version             BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX idx_stocks_company ON stocks (company_id);

CREATE TABLE portfolio (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id    BIGINT        NOT NULL REFERENCES accounts (id),
    stock_id      BIGINT        NOT NULL REFERENCES stocks (id),
    quantity      BIGINT        NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    reserved_qty  BIGINT        NOT NULL DEFAULT 0 CHECK (reserved_qty >= 0),
    avg_cost      NUMERIC(18,4) NOT NULL DEFAULT 0,
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version       BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_portfolio_position UNIQUE (account_id, stock_id),
    CONSTRAINT reserved_within_quantity CHECK (reserved_qty <= quantity)
);

CREATE TABLE orders (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        BIGINT        NOT NULL REFERENCES users (id),
    account_id     BIGINT        NOT NULL REFERENCES accounts (id),
    stock_id       BIGINT        NOT NULL REFERENCES stocks (id),
    side           VARCHAR(4)    NOT NULL CHECK (side IN ('BUY', 'SELL')),
    type           VARCHAR(6)    NOT NULL CHECK (type IN ('LIMIT', 'MARKET')),
    price          NUMERIC(18,4) CHECK (price > 0),
    qty            BIGINT        NOT NULL CHECK (qty > 0),
    remaining_qty  BIGINT        NOT NULL,
    status         VARCHAR(16)   NOT NULL CHECK (status IN
                       ('PENDING', 'OPEN', 'PARTIALLY_FILLED', 'FILLED', 'CANCELLED', 'REJECTED')),
    reject_reason  VARCHAR(255),
    source         VARCHAR(12)   NOT NULL DEFAULT 'WEB' CHECK (source IN ('WEB', 'MARKET_MAKER')),
    created_time   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version        BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT limit_needs_price CHECK (type = 'MARKET' OR price IS NOT NULL),
    CONSTRAINT remaining_in_range CHECK (remaining_qty BETWEEN 0 AND qty)
);
-- Order book: only live orders, best price first, then oldest first
CREATE INDEX idx_orders_book ON orders (stock_id, side, price, created_time)
    WHERE status IN ('OPEN', 'PARTIALLY_FILLED');
CREATE INDEX idx_orders_user ON orders (user_id, created_time DESC);

CREATE TABLE trades (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    stock_id        BIGINT        NOT NULL REFERENCES stocks (id),
    buy_order_id    BIGINT        NOT NULL REFERENCES orders (id),
    sell_order_id   BIGINT        NOT NULL REFERENCES orders (id),
    buyer_user_id   BIGINT        NOT NULL REFERENCES users (id),
    seller_user_id  BIGINT        NOT NULL REFERENCES users (id),
    qty             BIGINT        NOT NULL CHECK (qty > 0),
    price           NUMERIC(18,4) NOT NULL CHECK (price > 0),
    total_value     NUMERIC(18,2) NOT NULL,
    executed_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_trades_stock_time ON trades (stock_id, executed_at DESC);
CREATE INDEX idx_trades_buyer ON trades (buyer_user_id);
CREATE INDEX idx_trades_seller ON trades (seller_user_id);

CREATE TABLE incidents (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type         VARCHAR(30) NOT NULL CHECK (type IN
                     ('ORDER_REJECTED', 'INSUFFICIENT_FUNDS', 'INSUFFICIENT_SHARES', 'CONCURRENCY_CONFLICT',
                      'PRICE_SPIKE', 'BOT_INVENTORY_LIMIT', 'EVENT_PUBLISH_FAILED')),
    severity     VARCHAR(6)  NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH')),
    stock_id     BIGINT      REFERENCES stocks (id),
    order_id     BIGINT      REFERENCES orders (id),
    user_id      BIGINT      REFERENCES users (id),
    message      TEXT        NOT NULL,
    details      JSONB,
    status       VARCHAR(12) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at  TIMESTAMPTZ
);
CREATE INDEX idx_incidents_time ON incidents (created_at DESC);
CREATE INDEX idx_incidents_type_time ON incidents (type, created_at DESC);

CREATE TABLE bot_configs (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    bot_user_id    BIGINT       NOT NULL REFERENCES users (id),
    stock_id       BIGINT       NOT NULL REFERENCES stocks (id),
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    spread_bps     INT          NOT NULL CHECK (spread_bps BETWEEN 1 AND 1000),
    quote_qty      BIGINT       NOT NULL CHECK (quote_qty > 0),
    max_inventory  BIGINT       NOT NULL CHECK (max_inventory > 0),
    skew_factor    NUMERIC(6,3) NOT NULL DEFAULT 0.5,
    refresh_ms     INT          NOT NULL DEFAULT 2000 CHECK (refresh_ms >= 200),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by     VARCHAR(64)  NOT NULL DEFAULT 'seed',
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_bot_stock UNIQUE (bot_user_id, stock_id)
);

CREATE TABLE event_outbox (
    id            UUID        PRIMARY KEY,
    topic         VARCHAR(60) NOT NULL,
    msg_key       VARCHAR(60) NOT NULL,
    type          VARCHAR(40) NOT NULL,
    payload       JSONB       NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at  TIMESTAMPTZ,
    attempts      INT         NOT NULL DEFAULT 0
);
-- Relay reads unpublished rows with SELECT ... FOR UPDATE SKIP LOCKED
CREATE INDEX idx_outbox_unpublished ON event_outbox (created_at) WHERE published_at IS NULL;
