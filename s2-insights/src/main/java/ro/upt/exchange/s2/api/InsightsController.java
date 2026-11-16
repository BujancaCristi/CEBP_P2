package ro.upt.exchange.s2.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ro.upt.exchange.s2.config.InsightsProperties;
import ro.upt.exchange.s2.query.InsightsQueries;
import ro.upt.exchange.s2.query.InsightsQueries.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** All S2 read endpoints (contracts/s2-openapi.yaml). */
@RestController
@RequestMapping("/api")
public class InsightsController {

    private final InsightsQueries q;
    private final InsightsProperties props;

    public InsightsController(InsightsQueries q, InsightsProperties props) {
        this.q = q;
        this.props = props;
    }

    private LocalDate dayOrToday(LocalDate d) {
        return d != null ? d : LocalDate.now(props.zoneId());
    }

    private Instant sinceOrDayAgo(Instant s) {
        return s != null ? s : Instant.now().minusSeconds(86_400);
    }

    @Tag(name = "Stats")
    @Operation(operationId = "dailyStats", summary = "OHLC, VWAP, volume and order counts per stock per day")
    @GetMapping("/stats/daily")
    public List<DailyStat> daily(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                 @RequestParam(required = false) String symbol) {
        return q.daily(dayOrToday(date), symbol);
    }

    @Tag(name = "Stats")
    @Operation(operationId = "history", summary = "Daily price history for charts")
    @GetMapping("/stats/history/{symbol}")
    public List<HistoryPoint> history(@PathVariable String symbol, @RequestParam(defaultValue = "30") int days) {
        return q.history(symbol, Math.min(days, 365));
    }

    @Tag(name = "Stats")
    @Operation(operationId = "movers", summary = "Biggest gainers, losers and most traded stocks of a day")
    @GetMapping("/stats/movers")
    public Movers movers(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                         @RequestParam(defaultValue = "5") int limit) {
        return q.movers(dayOrToday(date), limit);
    }

    @Tag(name = "Trades")
    @Operation(operationId = "trades", summary = "Recent trades (also for the MCP server)")
    @GetMapping("/trades")
    public List<TradeRow> trades(@RequestParam(required = false) String symbol,
                                 @RequestParam(required = false) Instant since,
                                 @RequestParam(defaultValue = "200") int limit) {
        return q.trades(symbol, sinceOrDayAgo(since), Math.min(limit, 1000));
    }

    @Tag(name = "Bots")
    @Operation(operationId = "botStats", summary = "Market maker performance per stock per day")
    @GetMapping("/bots/stats")
    public List<BotStat> botStats(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                  @RequestParam(required = false) String symbol) {
        return q.botStats(dayOrToday(date), symbol);
    }

    @Tag(name = "Bots")
    @Operation(operationId = "botConfigs", summary = "Current bot settings as seen through events")
    @GetMapping("/bots/configs")
    public List<BotConfig> botConfigs(@RequestParam(required = false) String symbol) {
        return q.botConfigs(symbol);
    }

    @Tag(name = "Incidents")
    @Operation(operationId = "incidents", summary = "Raw incidents")
    @GetMapping("/incidents")
    public List<IncidentRow> incidents(@RequestParam(required = false) Instant since,
                                       @RequestParam(required = false) String type,
                                       @RequestParam(defaultValue = "200") int limit) {
        return q.incidents(sinceOrDayAgo(since), type, Math.min(limit, 1000));
    }

    @Tag(name = "Incidents")
    @Operation(operationId = "incidentSummary", summary = "Incidents grouped by type and stock")
    @GetMapping("/incidents/summary")
    public List<IncidentGroup> incidentSummary(@RequestParam(required = false) Instant since) {
        return q.incidentSummary(sinceOrDayAgo(since));
    }
}
