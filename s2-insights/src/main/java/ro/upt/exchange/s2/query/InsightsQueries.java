package ro.upt.exchange.s2.query;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** Read side of the read model. Plain SQL over the *_view tables. */
@Repository
public class InsightsQueries {

    public record DailyStat(String symbol, LocalDate day, BigDecimal open, BigDecimal high, BigDecimal low,
                            BigDecimal close, BigDecimal vwap, long volume, long tradeCount,
                            long ordersPlaced, long ordersCancelled, long ordersRejected, BigDecimal changePct) {}
    public record HistoryPoint(LocalDate day, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, long volume) {}
    public record Mover(String symbol, BigDecimal open, BigDecimal close, BigDecimal changePct, long volume, long tradeCount) {}
    public record Movers(LocalDate date, List<Mover> gainers, List<Mover> losers, List<Mover> mostTraded) {}
    public record TradeRow(long tradeId, String symbol, long buyerUserId, long sellerUserId, long qty,
                           BigDecimal price, BigDecimal totalValue, Instant executedAt) {}
    public record BotStat(long botUserId, String symbol, LocalDate day, long quotesPlaced, long fills, long boughtQty,
                          long soldQty, long endInventory, BigDecimal realizedPnl, BigDecimal avgSpreadBps) {}
    public record BotConfig(long configId, long botUserId, String symbol, boolean enabled, int spreadBps, long quoteQty,
                            long maxInventory, BigDecimal skewFactor, int refreshMs, Instant updatedAt) {}
    public record IncidentRow(long incidentId, String type, String severity, String symbol, Long orderId, String message,
                              String status, Instant createdAt) {}
    public record IncidentGroup(String type, String symbol, long count, String exampleMessage) {}

    private final NamedParameterJdbcTemplate jdbc;

    public InsightsQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static BigDecimal pct(BigDecimal open, BigDecimal close) {
        if (open == null || close == null || open.signum() == 0) return BigDecimal.ZERO;
        return close.subtract(open).multiply(BigDecimal.valueOf(100)).divide(open, 2, java.math.RoundingMode.HALF_UP);
    }

    public List<DailyStat> daily(LocalDate day, String symbol) {
        return jdbc.query("""
                SELECT * FROM daily_stock_stats WHERE day = :day AND (CAST(:symbol AS TEXT) IS NULL OR symbol = :symbol) ORDER BY symbol""",
                new MapSqlParameterSource("day", day).addValue("symbol", symbol),
                (rs, i) -> new DailyStat(rs.getString("symbol"), rs.getObject("day", LocalDate.class),
                        rs.getBigDecimal("open"), rs.getBigDecimal("high"), rs.getBigDecimal("low"), rs.getBigDecimal("close"),
                        rs.getBigDecimal("vwap"), rs.getLong("volume"), rs.getLong("trade_count"), rs.getLong("orders_placed"),
                        rs.getLong("orders_cancelled"), rs.getLong("orders_rejected"),
                        pct(rs.getBigDecimal("open"), rs.getBigDecimal("close"))));
    }

    public List<HistoryPoint> history(String symbol, int days) {
        return jdbc.query("""
                SELECT day, open, high, low, close, volume FROM daily_stock_stats
                WHERE symbol = :symbol AND day >= :from ORDER BY day""",
                new MapSqlParameterSource("symbol", symbol).addValue("from", LocalDate.now().minusDays(days)),
                (rs, i) -> new HistoryPoint(rs.getObject("day", LocalDate.class), rs.getBigDecimal("open"),
                        rs.getBigDecimal("high"), rs.getBigDecimal("low"), rs.getBigDecimal("close"), rs.getLong("volume")));
    }

    public Movers movers(LocalDate day, int limit) {
        List<Mover> all = jdbc.query("SELECT * FROM daily_stock_stats WHERE day = :day",
                new MapSqlParameterSource("day", day),
                (rs, i) -> new Mover(rs.getString("symbol"), rs.getBigDecimal("open"), rs.getBigDecimal("close"),
                        pct(rs.getBigDecimal("open"), rs.getBigDecimal("close")), rs.getLong("volume"), rs.getLong("trade_count")));
        var gainers = all.stream().filter(m -> m.changePct().signum() > 0)
                .sorted((a, b) -> b.changePct().compareTo(a.changePct())).limit(limit).toList();
        var losers = all.stream().filter(m -> m.changePct().signum() < 0)
                .sorted((a, b) -> a.changePct().compareTo(b.changePct())).limit(limit).toList();
        var traded = all.stream().sorted((a, b) -> Long.compare(b.volume(), a.volume())).limit(limit).toList();
        return new Movers(day, gainers, losers, traded);
    }

    public List<TradeRow> trades(String symbol, Instant since, int limit) {
        return jdbc.query("""
                SELECT * FROM trades_view WHERE (CAST(:symbol AS TEXT) IS NULL OR symbol = :symbol) AND executed_at >= :since
                ORDER BY executed_at DESC LIMIT :limit""",
                new MapSqlParameterSource("symbol", symbol).addValue("since", since.atOffset(java.time.ZoneOffset.UTC))
                        .addValue("limit", limit),
                (rs, i) -> new TradeRow(rs.getLong("trade_id"), rs.getString("symbol"), rs.getLong("buyer_user_id"),
                        rs.getLong("seller_user_id"), rs.getLong("qty"), rs.getBigDecimal("price"), rs.getBigDecimal("total_value"),
                        rs.getObject("executed_at", OffsetDateTime.class).toInstant()));
    }

    public List<BotStat> botStats(LocalDate day, String symbol) {
        return jdbc.query("""
                SELECT * FROM bot_daily_stats WHERE day = :day AND (CAST(:symbol AS TEXT) IS NULL OR symbol = :symbol) ORDER BY symbol""",
                new MapSqlParameterSource("day", day).addValue("symbol", symbol),
                (rs, i) -> new BotStat(rs.getLong("bot_user_id"), rs.getString("symbol"), rs.getObject("day", LocalDate.class),
                        rs.getLong("quotes_placed"), rs.getLong("fills"), rs.getLong("bought_qty"), rs.getLong("sold_qty"),
                        rs.getLong("end_inventory"), rs.getBigDecimal("realized_pnl"), rs.getBigDecimal("avg_spread_bps")));
    }

    public List<BotConfig> botConfigs(String symbol) {
        return jdbc.query("SELECT * FROM bot_configs_view WHERE (CAST(:symbol AS TEXT) IS NULL OR symbol = :symbol) ORDER BY symbol",
                new MapSqlParameterSource("symbol", symbol),
                (rs, i) -> new BotConfig(rs.getLong("config_id"), rs.getLong("bot_user_id"), rs.getString("symbol"),
                        rs.getBoolean("enabled"), rs.getInt("spread_bps"), rs.getLong("quote_qty"), rs.getLong("max_inventory"),
                        rs.getBigDecimal("skew_factor"), rs.getInt("refresh_ms"),
                        rs.getObject("updated_at", OffsetDateTime.class).toInstant()));
    }

    public List<IncidentRow> incidents(Instant since, String type, int limit) {
        return jdbc.query("""
                SELECT * FROM incidents_view WHERE created_at >= :since AND (CAST(:type AS TEXT) IS NULL OR type = :type)
                ORDER BY created_at DESC LIMIT :limit""",
                new MapSqlParameterSource("since", since.atOffset(java.time.ZoneOffset.UTC)).addValue("type", type)
                        .addValue("limit", limit),
                (rs, i) -> new IncidentRow(rs.getLong("incident_id"), rs.getString("type"), rs.getString("severity"),
                        rs.getString("symbol"), (Long) rs.getObject("order_id"), rs.getString("message"), rs.getString("status"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()));
    }

    public List<IncidentGroup> incidentSummary(Instant since) {
        return jdbc.query("""
                SELECT type, symbol, COUNT(*) AS cnt, MIN(message) AS example FROM incidents_view
                WHERE created_at >= :since GROUP BY type, symbol ORDER BY cnt DESC""",
                new MapSqlParameterSource("since", since.atOffset(java.time.ZoneOffset.UTC)),
                (rs, i) -> new IncidentGroup(rs.getString("type"), rs.getString("symbol"), rs.getLong("cnt"), rs.getString("example")));
    }

    public Map<String, Object> counts() {
        return jdbc.queryForMap("""
                SELECT (SELECT COUNT(*) FROM processed_events) AS events, (SELECT COUNT(*) FROM trades_view) AS trades,
                       (SELECT COUNT(*) FROM incidents_view) AS incidents""", new MapSqlParameterSource());
    }
}
