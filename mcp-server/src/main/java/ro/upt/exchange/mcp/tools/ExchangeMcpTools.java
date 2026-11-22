package ro.upt.exchange.mcp.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import ro.upt.exchange.mcp.service.EmailService;
import ro.upt.exchange.mcp.service.S2Client;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * The 7 MCP tools for the Stock Exchange AI nightly report agent.
 * <b>Owner: Paul.</b>
 *
 * 5 read tools, 1 propose tool, 1 action tool (sending email).
 * Zero database writes.
 */
@Component
public class ExchangeMcpTools {

    private static final Logger log = LoggerFactory.getLogger(ExchangeMcpTools.class);

    private final S2Client s2Client;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    public ExchangeMcpTools(S2Client s2Client, EmailService emailService, ObjectMapper objectMapper) {
        this.s2Client = s2Client;
        this.emailService = emailService;
        this.objectMapper = objectMapper;
    }

    @Tool(name = "get_market_summary", description = "Returns daily market overview statistics (open, high, low, close, volume, trades, etc.) per stock for the specified date.")
    public String getMarketSummary(
            @ToolParam(description = "ISO date YYYY-MM-DD (defaults to target date / yesterday)") String date) {
        log.info("Tool called: get_market_summary(date={})", date);
        return s2Client.getMarketSummary(date);
    }

    @Tool(name = "get_incidents", description = "Returns incidents recorded on the exchange, grouped by type and stock with counts and example messages.")
    public String getIncidents(
            @ToolParam(description = "ISO instant or date-time since when to query incidents") String since,
            @ToolParam(description = "Optional filter by incident type, e.g. BOT_INVENTORY_LIMIT, CONCURRENCY_CONFLICT", required = false) String type) {
        log.info("Tool called: get_incidents(since={}, type={})", since, type);
        return s2Client.getIncidents(since, type);
    }

    @Tool(name = "get_bot_performance", description = "Returns trading performance, inventory change, and realised P&L of the market-maker bot per stock for the specified date.")
    public String getBotPerformance(
            @ToolParam(description = "ISO date YYYY-MM-DD") String date,
            @ToolParam(description = "Optional stock symbol filter", required = false) String symbol) {
        log.info("Tool called: get_bot_performance(date={}, symbol={})", date, symbol);
        return s2Client.getBotPerformance(date, symbol);
    }

    @Tool(name = "get_bot_configs", description = "Returns current bot configurations (spread, quote quantity, max inventory, refresh interval, enabled status) for all stocks or a specific symbol.")
    public String getBotConfigs(
            @ToolParam(description = "Optional stock symbol filter", required = false) String symbol) {
        log.info("Tool called: get_bot_configs(symbol={})", symbol);
        return s2Client.getBotConfigs(symbol);
    }

    @Tool(name = "get_trades", description = "Returns recent trade executions for a stock to spot unusual price swings or trading activity.")
    public String getTrades(
            @ToolParam(description = "Stock symbol, e.g. ACME") String symbol,
            @ToolParam(description = "Optional ISO date-time since when to query", required = false) String since,
            @ToolParam(description = "Max trades to return (default 200)", required = false) Integer limit) {
        log.info("Tool called: get_trades(symbol={}, since={}, limit={})", symbol, since, limit);
        return s2Client.getTrades(symbol, since, limit);
    }

    @Tool(name = "propose_bot_config_change", description = "Validates proposed bot configuration adjustments and generates an administrative recommendation URL. Writes nothing to any database.")
    public String proposeBotConfigChange(
            @ToolParam(description = "ID of the bot configuration to modify") Long configId,
            @ToolParam(description = "Proposed spread in basis points (1 to 1000)", required = false) Integer spreadBps,
            @ToolParam(description = "Proposed quote quantity (shares per side)", required = false) Long quoteQty,
            @ToolParam(description = "Proposed maximum inventory limit", required = false) Long maxInventory,
            @ToolParam(description = "Proposed inventory skew factor", required = false) BigDecimal skewFactor,
            @ToolParam(description = "Proposed refresh interval in milliseconds (min 200)", required = false) Integer refreshMs,
            @ToolParam(description = "Proposed active status", required = false) Boolean enabled,
            @ToolParam(description = "Reason and rationale for this proposed adjustment") String reason) {
        log.info("Tool called: propose_bot_config_change(configId={}, spreadBps={}, quoteQty={}, maxInventory={}, enabled={}, reason={})",
                configId, spreadBps, quoteQty, maxInventory, enabled, reason);

        Map<String, Object> currentSettings = new LinkedHashMap<>();
        Map<String, Object> proposedSettings = new LinkedHashMap<>();
        String symbol = "UNKNOWN";

        try {
            String configsJson = s2Client.getBotConfigs(null);
            List<Map<String, Object>> configs = objectMapper.readValue(configsJson, new TypeReference<>() {});
            for (Map<String, Object> cfg : configs) {
                Object cid = cfg.get("configId");
                if (cid != null && Long.parseLong(String.valueOf(cid)) == configId) {
                    symbol = String.valueOf(cfg.getOrDefault("symbol", "UNKNOWN"));
                    currentSettings.put("spreadBps", cfg.get("spreadBps"));
                    currentSettings.put("quoteQty", cfg.get("quoteQty"));
                    currentSettings.put("maxInventory", cfg.get("maxInventory"));
                    currentSettings.put("skewFactor", cfg.get("skewFactor"));
                    currentSettings.put("refreshMs", cfg.get("refreshMs"));
                    currentSettings.put("enabled", cfg.get("enabled"));
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("Could not retrieve current config for #{}: {}", configId, e.getMessage());
        }

        if (spreadBps != null) proposedSettings.put("spreadBps", Math.max(1, Math.min(1000, spreadBps)));
        if (quoteQty != null) proposedSettings.put("quoteQty", Math.max(1, quoteQty));
        if (maxInventory != null) proposedSettings.put("maxInventory", Math.max(1, maxInventory));
        if (skewFactor != null) proposedSettings.put("skewFactor", skewFactor);
        if (refreshMs != null) proposedSettings.put("refreshMs", Math.max(200, refreshMs));
        if (enabled != null) proposedSettings.put("enabled", enabled);

        StringBuilder urlBuilder = new StringBuilder("/admin/bots?");
        urlBuilder.append("configId=").append(configId);
        if (spreadBps != null) urlBuilder.append("&spreadBps=").append(spreadBps);
        if (quoteQty != null) urlBuilder.append("&quoteQty=").append(quoteQty);
        if (maxInventory != null) urlBuilder.append("&maxInventory=").append(maxInventory);
        if (skewFactor != null) urlBuilder.append("&skewFactor=").append(skewFactor);
        if (refreshMs != null) urlBuilder.append("&refreshMs=").append(refreshMs);
        if (enabled != null) urlBuilder.append("&enabled=").append(enabled);
        if (reason != null) urlBuilder.append("&reason=").append(URLEncoder.encode(reason, StandardCharsets.UTF_8));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", "rec-" + UUID.randomUUID().toString().substring(0, 8));
        result.put("configId", configId);
        result.put("symbol", symbol);
        result.put("current", currentSettings);
        result.put("proposed", proposedSettings);
        result.put("reason", reason);
        result.put("applyUrl", urlBuilder.toString());

        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return "{\"configId\":" + configId + ",\"applyUrl\":\"" + urlBuilder + "\"}";
        }
    }

    @Tool(name = "send_report", description = "Renders an HTML email (summary, findings, table of recommendations with 'Apply' links) and sends it over SMTP to REPORT_RECIPIENTS.")
    public String sendReport(
            @ToolParam(description = "Email subject line") String subject,
            @ToolParam(description = "Markdown text of the report") String markdown,
            @ToolParam(description = "List of recommendations from propose_bot_config_change", required = false) List<Map<String, Object>> recommendations) {
        log.info("Tool called: send_report(subject='{}', recs={})", subject,
                recommendations != null ? recommendations.size() : 0);

        String messageId = emailService.sendReport(subject, markdown, recommendations);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("messageId", messageId);
        resp.put("sent", true);
        resp.put("timestamp", Instant.now().toString());

        try {
            return objectMapper.writeValueAsString(resp);
        } catch (Exception e) {
            return "{\"sent\":true,\"messageId\":\"" + messageId + "\"}";
        }
    }
}
