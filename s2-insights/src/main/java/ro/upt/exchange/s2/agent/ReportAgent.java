package ro.upt.exchange.s2.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ro.upt.exchange.s2.config.InsightsProperties;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Nightly market report agent. Spring AI {@link ChatClient} + the MCP server's tools (read-only + send_report).
 * Safeguards: at most {@code insights.agent.max-tool-calls} tool calls per run (enforced in code), nothing is
 * stored, and a failed run sends a short "report failed" email through {@code send_report}.
 */
@Service
public class ReportAgent {

    private static final Logger log = LoggerFactory.getLogger(ReportAgent.class);

    public record ReportResult(LocalDate date, boolean sent, String report, int toolCalls, Instant generatedAt, String error) {}

    static final String SYSTEM_PROMPT = """
            You are the night-shift analyst of a stock exchange. Write the report for %s.
            1. Call get_market_summary, get_incidents, get_bot_performance and get_bot_configs for that date.
            2. Find: stocks that moved more than 5%%, incident types that appeared more than 10 times,
               stocks where the market maker lost money or ended above 80%% of max inventory.
            3. Recommend changes only with propose_bot_config_change, and only by these rules:
               - bot lost money on a stock that moved more than 5%%  -> spreadBps x 1.5
               - inventory above 80%% of max                         -> quoteQty x 0.5
               - more than 3 BOT_INVENTORY_LIMIT incidents           -> enabled = false
            4. Finish by calling send_report exactly once, with a short title, a markdown summary
               (overview, per-stock findings, incidents) and all recommendations.
            After send_report, answer with the same markdown report and nothing else.
            You may call at most %d tools in total.
            """;

    private final ChatClient.Builder chatClientBuilder;
    private final ObjectProvider<ToolCallbackProvider> toolProviders;
    private final InsightsProperties props;

    public ReportAgent(ChatClient.Builder chatClientBuilder, ObjectProvider<ToolCallbackProvider> toolProviders,
                       InsightsProperties props) {
        this.chatClientBuilder = chatClientBuilder;
        this.toolProviders = toolProviders;
        this.props = props;
    }

    @Scheduled(cron = "${insights.agent.cron}", zone = "${insights.zone}")
    public void nightly() {
        run(LocalDate.now(props.zoneId()).minusDays(1));
    }

    public ReportResult run(LocalDate date) {
        int max = props.agent().maxToolCalls();
        AtomicInteger calls = new AtomicInteger();
        List<ToolCallback> tools = toolProviders.stream()
                .flatMap(p -> Arrays.stream(p.getToolCallbacks()))
                .map(t -> new CountingToolCallback(t, calls, max))
                .map(t -> (ToolCallback) t)
                .toList();
        if (tools.isEmpty()) {
            return failed(date, calls.get(), "no MCP tools available (is the MCP server reachable?)", null);
        }
        try {
            String report = chatClientBuilder.build().prompt()
                    .system(SYSTEM_PROMPT.formatted(date, max))
                    .user("Write the report for " + date + ".")
                    .toolCallbacks(tools)
                    .call()
                    .content();
            boolean sent = CountingToolCallback.wasCalled(tools, "send_report");
            return new ReportResult(date, sent, report, calls.get(), Instant.now(), null);
        } catch (Exception e) {
            log.error("Report agent failed for {}", date, e);
            return failed(date, calls.get(), e.getMessage(), tools);
        }
    }

    private ReportResult failed(LocalDate date, int calls, String error, List<ToolCallback> tools) {
        if (tools != null) {
            tools.stream().filter(t -> t.getToolDefinition().name().equals("send_report")).findFirst().ifPresent(t -> {
                try {
                    t.call("{\"subject\":\"Report failed for " + date + "\",\"markdown\":\"The nightly report for "
                            + date + " failed: " + error.replace("\"", "'") + "\",\"recommendations\":[]}");
                } catch (Exception ex) {
                    log.warn("Could not send the 'report failed' email: {}", ex.getMessage());
                }
            });
        }
        return new ReportResult(date, false, null, calls, Instant.now(), error);
    }
}
