package ro.upt.exchange.mcp.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ro.upt.exchange.mcp.tools.ExchangeMcpTools;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Universal JSON-RPC / MCP HTTP & SSE endpoint for MCP clients, MCP Inspector, and direct verification.
 */
@RestController
@CrossOrigin(origins = "*")
public class DirectMcpController {

    private static final Logger log = LoggerFactory.getLogger(DirectMcpController.class);

    private final ExchangeMcpTools tools;
    private final ObjectMapper objectMapper;
    private final Map<String, SseEmitter> activeEmitters = new ConcurrentHashMap<>();

    public DirectMcpController(ExchangeMcpTools tools, ObjectMapper objectMapper) {
        this.tools = tools;
        this.objectMapper = objectMapper;
    }

    @GetMapping(value = {"/mcp", "/api/mcp"})
    public Map<String, Object> getServerInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", "exchange-mcp-server");
        info.put("version", "1.0.0");
        info.put("protocolVersion", "2024-11-05");
        info.put("tools", listToolsDefinitions());
        return info;
    }

    @GetMapping(value = {"/mcp/sse", "/sse"}, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connectSse() {
        String sessionId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(180_000L);
        activeEmitters.put(sessionId, emitter);

        emitter.onCompletion(() -> activeEmitters.remove(sessionId));
        emitter.onTimeout(() -> activeEmitters.remove(sessionId));
        emitter.onError(e -> activeEmitters.remove(sessionId));

        try {
            emitter.send(SseEmitter.event()
                    .name("endpoint")
                    .data("/mcp/message?sessionId=" + sessionId));
        } catch (IOException e) {
            emitter.complete();
        }

        return emitter;
    }

    @PostMapping(value = {"/mcp", "/mcp/message", "/api/mcp"}, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> handleJsonRpc(@RequestBody Map<String, Object> request) {
        Object id = request.get("id");
        String method = (String) request.get("method");

        log.debug("MCP JSON-RPC request method={} id={}", method, id);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);

        if ("initialize".equals(method)) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("protocolVersion", "2024-11-05");
            result.put("capabilities", Map.of("tools", Map.of("listChanged", false)));
            result.put("serverInfo", Map.of("name", "exchange-mcp-server", "version", "1.0.0"));
            response.put("result", result);
            return ResponseEntity.ok(response);
        }

        if ("ping".equals(method)) {
            response.put("result", Map.of());
            return ResponseEntity.ok(response);
        }

        if ("tools/list".equals(method)) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("tools", listToolsDefinitions());
            response.put("result", result);
            return ResponseEntity.ok(response);
        }

        if ("tools/call".equals(method)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) request.get("params");
            if (params == null || !params.containsKey("name")) {
                response.put("error", Map.of("code", -32602, "message", "Missing tool name in params"));
                return ResponseEntity.ok(response);
            }
            String toolName = (String) params.get("name");
            @SuppressWarnings("unchecked")
            Map<String, Object> args = (Map<String, Object>) params.getOrDefault("arguments", Collections.emptyMap());

            try {
                String toolResult = executeTool(toolName, args);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("content", List.of(Map.of("type", "text", "text", toolResult)));
                response.put("result", result);
                return ResponseEntity.ok(response);
            } catch (Exception e) {
                log.error("Tool execution error in {}: {}", toolName, e.getMessage(), e);
                response.put("error", Map.of("code", -32603, "message", "Internal tool execution error: " + e.getMessage()));
                return ResponseEntity.ok(response);
            }
        }

        response.put("error", Map.of("code", -32601, "message", "Method not found: " + method));
        return ResponseEntity.ok(response);
    }

    private String executeTool(String toolName, Map<String, Object> args) {
        return switch (toolName) {
            case "get_market_summary" -> {
                String date = (String) args.get("date");
                yield tools.getMarketSummary(date);
            }
            case "get_incidents" -> {
                String since = (String) args.get("since");
                String type = (String) args.get("type");
                yield tools.getIncidents(since, type);
            }
            case "get_bot_performance" -> {
                String date = (String) args.get("date");
                String symbol = (String) args.get("symbol");
                yield tools.getBotPerformance(date, symbol);
            }
            case "get_bot_configs" -> {
                String symbol = (String) args.get("symbol");
                yield tools.getBotConfigs(symbol);
            }
            case "get_trades" -> {
                String symbol = (String) args.get("symbol");
                String since = (String) args.get("since");
                Integer limit = args.get("limit") != null ? Integer.parseInt(String.valueOf(args.get("limit"))) : null;
                yield tools.getTrades(symbol, since, limit);
            }
            case "propose_bot_config_change" -> {
                Long configId = args.get("configId") != null ? Long.parseLong(String.valueOf(args.get("configId"))) : null;
                Integer spreadBps = args.get("spreadBps") != null ? Integer.parseInt(String.valueOf(args.get("spreadBps"))) : null;
                Long quoteQty = args.get("quoteQty") != null ? Long.parseLong(String.valueOf(args.get("quoteQty"))) : null;
                Long maxInventory = args.get("maxInventory") != null ? Long.parseLong(String.valueOf(args.get("maxInventory"))) : null;
                BigDecimal skewFactor = args.get("skewFactor") != null ? new BigDecimal(String.valueOf(args.get("skewFactor"))) : null;
                Integer refreshMs = args.get("refreshMs") != null ? Integer.parseInt(String.valueOf(args.get("refreshMs"))) : null;
                Boolean enabled = args.get("enabled") != null ? Boolean.parseBoolean(String.valueOf(args.get("enabled"))) : null;
                String reason = (String) args.get("reason");
                yield tools.proposeBotConfigChange(configId, spreadBps, quoteQty, maxInventory, skewFactor, refreshMs, enabled, reason);
            }
            case "send_report" -> {
                String subject = (String) args.get("subject");
                String markdown = (String) args.get("markdown");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> recs = (List<Map<String, Object>>) args.get("recommendations");
                yield tools.sendReport(subject, markdown, recs);
            }
            default -> throw new IllegalArgumentException("Unknown tool: " + toolName);
        };
    }

    private List<Map<String, Object>> listToolsDefinitions() {
        List<Map<String, Object>> list = new ArrayList<>();

        list.add(Map.of(
                "name", "get_market_summary",
                "description", "Returns daily market overview statistics (open, high, low, close, volume, trades, etc.) per stock for the specified date.",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of("date", Map.of("type", "string", "description", "ISO date YYYY-MM-DD")),
                        "required", List.of("date")
                )
        ));

        list.add(Map.of(
                "name", "get_incidents",
                "description", "Returns incidents recorded on the exchange, grouped by type and stock with counts and example messages.",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "since", Map.of("type", "string", "description", "ISO timestamp"),
                                "type", Map.of("type", "string", "description", "Incident type")
                        ),
                        "required", List.of("since")
                )
        ));

        list.add(Map.of(
                "name", "get_bot_performance",
                "description", "Returns trading performance, inventory change, and realised P&L of the market-maker bot per stock for the specified date.",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "date", Map.of("type", "string", "description", "ISO date YYYY-MM-DD"),
                                "symbol", Map.of("type", "string", "description", "Stock symbol")
                        ),
                        "required", List.of("date")
                )
        ));

        list.add(Map.of(
                "name", "get_bot_configs",
                "description", "Returns current bot configurations for all stocks or a specific symbol.",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of("symbol", Map.of("type", "string", "description", "Stock symbol"))
                )
        ));

        list.add(Map.of(
                "name", "get_trades",
                "description", "Returns recent trade executions for a stock to spot unusual price swings.",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "symbol", Map.of("type", "string", "description", "Stock symbol"),
                                "since", Map.of("type", "string", "description", "ISO timestamp"),
                                "limit", Map.of("type", "integer", "description", "Max count")
                        ),
                        "required", List.of("symbol")
                )
        ));

        list.add(Map.of(
                "name", "propose_bot_config_change",
                "description", "Validates proposed bot configuration adjustments and generates an administrative recommendation URL. Writes nothing.",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "configId", Map.of("type", "integer"),
                                "spreadBps", Map.of("type", "integer"),
                                "quoteQty", Map.of("type", "integer"),
                                "maxInventory", Map.of("type", "integer"),
                                "skewFactor", Map.of("type", "number"),
                                "refreshMs", Map.of("type", "integer"),
                                "enabled", Map.of("type", "boolean"),
                                "reason", Map.of("type", "string")
                        ),
                        "required", List.of("configId", "reason")
                )
        ));

        list.add(Map.of(
                "name", "send_report",
                "description", "Renders an HTML email and sends it over SMTP to REPORT_RECIPIENTS.",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "subject", Map.of("type", "string"),
                                "markdown", Map.of("type", "string"),
                                "recommendations", Map.of("type", "array", "items", Map.of("type", "object"))
                        ),
                        "required", List.of("subject", "markdown")
                )
        ));

        return list;
    }
}
