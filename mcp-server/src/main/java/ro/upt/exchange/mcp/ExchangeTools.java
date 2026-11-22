package ro.upt.exchange.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The seven MCP tools (contracts/mcp-tools.md): five reads over S2, one propose (validates and returns a
 * recommendation, writes nothing) and one action (emails the report). No tool touches a database.
 */
@Component
public class ExchangeTools {

    /** Same limits as S1's BotConfigService, so a recommendation can always be saved by the admin. */
    static final int MIN_SPREAD_BPS = 1;
    static final int MAX_SPREAD_BPS = 2000;

    public record Recommendation(String id, long configId, String symbol, Map<String, Object> current,
                                 Map<String, Object> proposed, String reason, String applyUrl) {}

    private final S2Client s2;
    private final McpProperties props;
    private final ReportMailer mailer;
    private final ObjectMapper mapper;

    public ExchangeTools(S2Client s2, McpProperties props, ReportMailer mailer, ObjectMapper mapper) {
        this.s2 = s2;
        this.props = props;
        this.mailer = mailer;
        this.mapper = mapper;
    }

    // ---------------------------------------------------------------------------------- reads

    @Tool(name = "get_market_summary",
            description = "Per stock for one day: open, close, high, low, % change, VWAP, volume, trade count, "
                    + "orders placed, cancelled and rejected.")
    public JsonNode getMarketSummary(@ToolParam(description = "Day as YYYY-MM-DD") String date) {
        return s2.get(u -> u.path("/api/stats/daily").queryParam("date", LocalDate.parse(date)).build());
    }

    @Tool(name = "get_incidents",
            description = "Incidents since a moment, grouped by type and stock with counts and an example message. "
                    + "Optionally filtered by type (e.g. BOT_INVENTORY_LIMIT, CONCURRENCY_CONFLICT).")
    public JsonNode getIncidents(
            @ToolParam(description = "Start, as YYYY-MM-DD or an ISO instant") String since,
            @ToolParam(description = "Incident type to keep; omit for all", required = false) String type) {
        Instant from = toInstant(since);
        JsonNode groups = s2.get(u -> u.path("/api/incidents/summary").queryParam("since", from).build());
        if (type == null || type.isBlank() || !groups.isArray()) return groups;
        ArrayNode filtered = mapper.createArrayNode();
        groups.forEach(g -> { if (type.equalsIgnoreCase(g.path("type").asText())) filtered.add(g); });
        return filtered;
    }

    @Tool(name = "get_bot_performance",
            description = "Market maker per stock for one day: quotes, fills, bought and sold shares, end inventory "
                    + "(net shares that day), realised cash P&L.")
    public JsonNode getBotPerformance(
            @ToolParam(description = "Day as YYYY-MM-DD") String date,
            @ToolParam(description = "Stock symbol; omit for all", required = false) String symbol) {
        return s2.get(u -> {
            u.path("/api/bots/stats").queryParam("date", LocalDate.parse(date));
            if (symbol != null && !symbol.isBlank()) u.queryParam("symbol", symbol);
            return u.build();
        });
    }

    @Tool(name = "get_bot_configs",
            description = "Current market-maker settings per stock: configId, enabled, spreadBps, quoteQty, maxInventory, "
                    + "skewFactor, refreshMs.")
    public JsonNode getBotConfigs(@ToolParam(description = "Stock symbol; omit for all", required = false) String symbol) {
        return s2.get(u -> {
            u.path("/api/bots/configs");
            if (symbol != null && !symbol.isBlank()) u.queryParam("symbol", symbol);
            return u.build();
        });
    }

    @Tool(name = "get_trades", description = "Recent trades of one stock since a moment, newest first (max 200).")
    public JsonNode getTrades(
            @ToolParam(description = "Stock symbol") String symbol,
            @ToolParam(description = "Start, as YYYY-MM-DD or an ISO instant") String since) {
        Instant from = toInstant(since);
        return s2.get(u -> u.path("/api/trades").queryParam("symbol", symbol).queryParam("since", from)
                .queryParam("limit", 200).build());
    }

    // -------------------------------------------------------------------------------- propose

    @Tool(name = "propose_bot_config_change",
            description = "Checks a proposed change to one bot setting against the current values and returns a pending "
                    + "recommendation {id, symbol, current, proposed, reason, applyUrl}. Writes nothing: an admin applies "
                    + "it by opening applyUrl and pressing Save.")
    public Recommendation proposeBotConfigChange(
            @ToolParam(description = "configId from get_bot_configs") long configId,
            @ToolParam(description = "New spread in basis points", required = false) Integer spreadBps,
            @ToolParam(description = "New quote size in shares", required = false) Long quoteQty,
            @ToolParam(description = "New max inventory in shares", required = false) Long maxInventory,
            @ToolParam(description = "Enable or disable quoting", required = false) Boolean enabled,
            @ToolParam(description = "Why, in one sentence") String reason) {
        JsonNode current = null;
        for (JsonNode c : s2.get(u -> u.path("/api/bots/configs").build())) {
            if (c.path("configId").asLong() == configId) current = c;
        }
        if (current == null) {
            throw new IllegalArgumentException("Unknown configId " + configId + "; call get_bot_configs first");
        }
        return buildRecommendation(current, spreadBps, quoteQty, maxInventory, enabled, reason);
    }

    Recommendation buildRecommendation(JsonNode current, Integer spreadBps, Long quoteQty, Long maxInventory,
                                       Boolean enabled, String reason) {
        long configId = current.path("configId").asLong();
        Map<String, Object> cur = new LinkedHashMap<>();
        Map<String, Object> next = new LinkedHashMap<>();
        StringBuilder query = new StringBuilder();
        long effQuote = quoteQty != null ? quoteQty : current.path("quoteQty").asLong();
        long effMax = maxInventory != null ? maxInventory : current.path("maxInventory").asLong();

        if (spreadBps != null) {
            if (spreadBps < MIN_SPREAD_BPS || spreadBps > MAX_SPREAD_BPS) {
                throw new IllegalArgumentException("spreadBps must be between " + MIN_SPREAD_BPS + " and " + MAX_SPREAD_BPS);
            }
            change(cur, next, query, "spreadBps", current.path("spreadBps").asInt(), spreadBps);
        }
        if (quoteQty != null) {
            if (quoteQty <= 0) throw new IllegalArgumentException("quoteQty must be positive");
            change(cur, next, query, "quoteQty", current.path("quoteQty").asLong(), quoteQty);
        }
        if (maxInventory != null) {
            if (maxInventory <= 0) throw new IllegalArgumentException("maxInventory must be positive");
            change(cur, next, query, "maxInventory", current.path("maxInventory").asLong(), maxInventory);
        }
        if (effQuote > effMax) throw new IllegalArgumentException("quoteQty cannot exceed maxInventory");
        if (enabled != null) change(cur, next, query, "enabled", current.path("enabled").asBoolean(), enabled);
        if (next.isEmpty()) throw new IllegalArgumentException("Nothing to change: give at least one new value");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason is required");

        String applyUrl = props.webUrl().replaceAll("/$", "") + "/admin/bots/" + configId + "?" + query
                + "&reason=" + java.net.URLEncoder.encode(reason, java.nio.charset.StandardCharsets.UTF_8);
        return new Recommendation(UUID.randomUUID().toString(), configId, current.path("symbol").asText(), cur, next,
                reason, applyUrl);
    }

    private static void change(Map<String, Object> cur, Map<String, Object> next, StringBuilder query,
                               String field, Object oldValue, Object newValue) {
        cur.put(field, oldValue);
        next.put(field, newValue);
        if (!query.isEmpty()) query.append('&');
        query.append(field).append('=').append(newValue);
    }

    // --------------------------------------------------------------------------------- action

    @Tool(name = "send_report",
            description = "Renders the report as an HTML email (summary, findings, a table of recommendations with Apply "
                    + "links) and sends it to the configured recipients. Call exactly once, at the end. Returns the message id.")
    public Map<String, Object> sendReport(
            @ToolParam(description = "Short email subject") String subject,
            @ToolParam(description = "The report body in markdown") String markdown,
            @ToolParam(description = "Recommendations returned by propose_bot_config_change", required = false)
            List<Recommendation> recommendations) {
        String messageId = mailer.send(subject, markdown, recommendations == null ? List.of() : recommendations);
        return Map.of("messageId", messageId, "recipients", props.recipients(), "sent", true);
    }

    static Instant toInstant(String since) {
        if (since == null || since.isBlank()) return Instant.now().minusSeconds(86_400);
        return since.length() == 10 ? LocalDate.parse(since).atStartOfDay().toInstant(ZoneOffset.UTC) : Instant.parse(since);
    }
}
