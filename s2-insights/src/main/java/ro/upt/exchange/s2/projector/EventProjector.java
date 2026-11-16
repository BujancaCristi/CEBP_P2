package ro.upt.exchange.s2.projector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.upt.exchange.s2.config.InsightsProperties;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Turns one event envelope into read-model rows, in one transaction, idempotently
 * (the {@code processed_events} primary key rejects a second delivery of the same {@code eventId}).
 * All writes are {@code INSERT ... ON CONFLICT} so a replay from offset 0 rebuilds the same numbers.
 */
@Service
public class EventProjector {

    private static final Logger log = LoggerFactory.getLogger(EventProjector.class);
    private static final Set<String> BOT_SOURCES = Set.of("MARKET_MAKER", "BOT");

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final InsightsProperties props;

    public EventProjector(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper, InsightsProperties props) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.props = props;
    }

    @Transactional
    public boolean project(String topic, String json) {
        JsonNode env;
        try {
            env = mapper.readTree(json);
        } catch (Exception e) {
            log.warn("Unparseable event on {} skipped: {}", topic, e.getMessage());
            return false;
        }
        UUID eventId = UUID.fromString(env.path("eventId").asText());
        int inserted = jdbc.update("""
                INSERT INTO processed_events (event_id, topic) VALUES (:id, :topic) ON CONFLICT DO NOTHING""",
                new MapSqlParameterSource("id", eventId).addValue("topic", topic));
        if (inserted == 0) {
            return false; // already processed
        }
        String type = env.path("type").asText();
        JsonNode p = env.path("payload");
        Instant occurredAt = OffsetDateTime.parse(env.path("occurredAt").asText()).toInstant();
        switch (type) {
            case "TradeExecuted" -> trade(p);
            case "OrderPlaced" -> orderCounter(p, occurredAt, "orders_placed", true);
            case "OrderCancelled" -> orderCounter(p, occurredAt, "orders_cancelled", false);
            case "OrderRejected" -> orderCounter(p, occurredAt, "orders_rejected", false);
            case "OrderUpdated" -> { /* fills are counted from TradeExecuted */ }
            case "PriceUpdated" -> { /* live price only; OHLC comes from trades */ }
            case "IncidentRaised", "IncidentUpdated" -> incident(p, occurredAt);
            case "BotConfigChanged" -> botConfig(p, occurredAt);
            default -> log.debug("Ignoring event type {}", type);
        }
        return true;
    }

    private LocalDate day(Instant at) {
        return at.atZone(props.zoneId()).toLocalDate();
    }

    private void trade(JsonNode p) {
        Instant executedAt = OffsetDateTime.parse(p.path("executedAt").asText()).toInstant();
        BigDecimal price = p.path("price").decimalValue();
        long qty = p.path("qty").asLong();
        BigDecimal total = p.hasNonNull("totalValue") ? p.path("totalValue").decimalValue()
                : price.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
        String symbol = p.path("symbol").asText();
        long buyer = p.path("buyerUserId").asLong();
        long seller = p.path("sellerUserId").asLong();

        var params = new MapSqlParameterSource()
                .addValue("tradeId", p.path("tradeId").asLong()).addValue("symbol", symbol)
                .addValue("buyer", buyer).addValue("seller", seller).addValue("qty", qty)
                .addValue("price", price).addValue("total", total)
                .addValue("executedAt", executedAt.atOffset(java.time.ZoneOffset.UTC))
                .addValue("day", day(executedAt));
        jdbc.update("""
                INSERT INTO trades_view (trade_id, symbol, buyer_user_id, seller_user_id, qty, price, total_value, executed_at)
                VALUES (:tradeId, :symbol, :buyer, :seller, :qty, :price, :total, :executedAt)
                ON CONFLICT (trade_id) DO NOTHING""", params);
        jdbc.update("""
                INSERT INTO daily_stock_stats (symbol, day, open, high, low, close, vwap, volume, trade_count)
                VALUES (:symbol, :day, :price, :price, :price, :price, :price, :qty, 1)
                ON CONFLICT (symbol, day) DO UPDATE SET
                    high = GREATEST(daily_stock_stats.high, EXCLUDED.high),
                    low = LEAST(daily_stock_stats.low, EXCLUDED.low),
                    close = EXCLUDED.close,
                    open = COALESCE(daily_stock_stats.open, EXCLUDED.open),
                    vwap = (COALESCE(daily_stock_stats.vwap, 0) * daily_stock_stats.volume + :price * :qty)
                           / (daily_stock_stats.volume + :qty),
                    volume = daily_stock_stats.volume + :qty,
                    trade_count = daily_stock_stats.trade_count + 1""", params);

        // Bot side(s): the bot's user id is known from bot_configs_view
        for (long user : new long[]{buyer, seller}) {
            boolean isBot = Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM bot_configs_view WHERE bot_user_id = :u)",
                    new MapSqlParameterSource("u", user), Boolean.class));
            if (!isBot) continue;
            boolean bought = user == buyer;
            long signedQty = bought ? qty : -qty;
            // realized P&L: cash in from sells minus cash out from buys (simple cash P&L; inventory valued at 0)
            BigDecimal pnl = bought ? total.negate() : total;
            jdbc.update("""
                    INSERT INTO bot_daily_stats (bot_user_id, symbol, day, fills, bought_qty, sold_qty, end_inventory, realized_pnl)
                    VALUES (:user, :symbol, :day, 1, :bought, :sold, :signed, :pnl)
                    ON CONFLICT (bot_user_id, symbol, day) DO UPDATE SET
                        fills = bot_daily_stats.fills + 1,
                        bought_qty = bot_daily_stats.bought_qty + EXCLUDED.bought_qty,
                        sold_qty = bot_daily_stats.sold_qty + EXCLUDED.sold_qty,
                        end_inventory = bot_daily_stats.end_inventory + EXCLUDED.end_inventory,
                        realized_pnl = bot_daily_stats.realized_pnl + EXCLUDED.realized_pnl""",
                    new MapSqlParameterSource("user", user).addValue("symbol", symbol)
                            .addValue("day", day(executedAt)).addValue("bought", bought ? qty : 0)
                            .addValue("sold", bought ? 0 : qty).addValue("signed", signedQty).addValue("pnl", pnl));
        }
    }

    private void orderCounter(JsonNode p, Instant at, String column, boolean countBotQuote) {
        var params = new MapSqlParameterSource("symbol", p.path("symbol").asText()).addValue("day", day(at));
        jdbc.update("INSERT INTO daily_stock_stats (symbol, day, " + column + ") VALUES (:symbol, :day, 1) "
                + "ON CONFLICT (symbol, day) DO UPDATE SET " + column + " = daily_stock_stats." + column + " + 1", params);
        if (countBotQuote && BOT_SOURCES.contains(p.path("source").asText())) {
            jdbc.update("""
                    INSERT INTO bot_daily_stats (bot_user_id, symbol, day, quotes_placed)
                    VALUES (:user, :symbol, :day, 1)
                    ON CONFLICT (bot_user_id, symbol, day) DO UPDATE SET quotes_placed = bot_daily_stats.quotes_placed + 1""",
                    params.addValue("user", p.path("userId").asLong()));
        }
    }

    private void incident(JsonNode p, Instant at) {
        Instant createdAt = p.hasNonNull("createdAt") ? OffsetDateTime.parse(p.path("createdAt").asText()).toInstant() : at;
        jdbc.update("""
                INSERT INTO incidents_view (incident_id, type, severity, symbol, order_id, message, status, created_at)
                VALUES (:id, :type, :severity, :symbol, :orderId, :message, :status, :createdAt)
                ON CONFLICT (incident_id) DO UPDATE SET status = EXCLUDED.status, message = EXCLUDED.message""",
                new MapSqlParameterSource("id", p.path("incidentId").asLong())
                        .addValue("type", p.path("type").asText()).addValue("severity", p.path("severity").asText())
                        .addValue("symbol", p.hasNonNull("symbol") ? p.path("symbol").asText() : null)
                        .addValue("orderId", p.hasNonNull("orderId") ? p.path("orderId").asLong() : null)
                        .addValue("message", p.path("message").asText("")).addValue("status", p.path("status").asText("OPEN"))
                        .addValue("createdAt", createdAt.atOffset(java.time.ZoneOffset.UTC)));
    }

    private void botConfig(JsonNode p, Instant at) {
        Instant updatedAt = p.hasNonNull("updatedAt") ? OffsetDateTime.parse(p.path("updatedAt").asText()).toInstant() : at;
        jdbc.update("""
                INSERT INTO bot_configs_view (config_id, bot_user_id, symbol, enabled, spread_bps, quote_qty, max_inventory,
                                              skew_factor, refresh_ms, updated_at)
                VALUES (:id, :bot, :symbol, :enabled, :spread, :qty, :maxInv, :skew, :refresh, :updatedAt)
                ON CONFLICT (config_id) DO UPDATE SET
                    enabled = EXCLUDED.enabled, spread_bps = EXCLUDED.spread_bps, quote_qty = EXCLUDED.quote_qty,
                    max_inventory = EXCLUDED.max_inventory, skew_factor = EXCLUDED.skew_factor,
                    refresh_ms = EXCLUDED.refresh_ms, updated_at = EXCLUDED.updated_at
                WHERE bot_configs_view.updated_at <= EXCLUDED.updated_at""",
                new MapSqlParameterSource("id", p.path("configId").asLong()).addValue("bot", p.path("botUserId").asLong())
                        .addValue("symbol", p.path("symbol").asText()).addValue("enabled", p.path("enabled").asBoolean(true))
                        .addValue("spread", p.path("spreadBps").asInt()).addValue("qty", p.path("quoteQty").asLong())
                        .addValue("maxInv", p.path("maxInventory").asLong())
                        .addValue("skew", p.path("skewFactor").decimalValue()).addValue("refresh", p.path("refreshMs").asInt())
                        .addValue("updatedAt", updatedAt.atOffset(java.time.ZoneOffset.UTC)));
    }
}
