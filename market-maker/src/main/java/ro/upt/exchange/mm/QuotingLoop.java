package ro.upt.exchange.mm;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ro.upt.exchange.mm.MarketMakerApplication.MmProperties;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The quoting loop (Application_Description 3.3). Every tick it re-reads its settings (so an admin's change,
 * BotConfigChanged, applies within a tick), and for each enabled stock whose {@code refreshMs} has elapsed it
 * cancels its previous quotes and places a new BUY and SELL around the last traded price.
 */
@Component
public class QuotingLoop {

    private static final Logger log = LoggerFactory.getLogger(QuotingLoop.class);

    private final S1Api s1;
    private final MmProperties props;
    private final Map<String, List<Long>> liveQuotes = new HashMap<>();
    private final Map<String, Long> lastQuoteAt = new HashMap<>();
    private final Map<String, String> lastSettings = new HashMap<>();
    private final Map<String, Boolean> limitReported = new HashMap<>();
    private volatile boolean running;
    private boolean cleanedUp;

    public QuotingLoop(S1Api s1, MmProperties props) {
        this.s1 = s1;
        this.props = props;
        this.running = props.autostart();
    }

    public void start() { running = true; }

    public void stop() {
        running = false;
        liveQuotes.values().forEach(ids -> ids.forEach(s1::cancel));
        liveQuotes.clear();
    }

    @Scheduled(fixedDelayString = "${mm.tick-ms:1000}", initialDelay = 2000)
    public void tick() {
        if (!running) return;
        try {
            if (!s1.loggedIn()) {
                s1.login();
                log.info("Logged in to S1 as {}", props.username());
            }
            if (!cleanedUp) {
                List<Long> stale = s1.myOpenOrderIds();
                stale.forEach(s1::cancel);
                log.info("Cancelled {} quotes left from a previous run", stale.size());
                cleanedUp = true;
            }
            quoteAll();
        } catch (Exception e) {
            log.warn("Quoting tick failed (will retry): {}", e.getMessage());
        }
    }

    private void quoteAll() {
        Map<String, JsonNode> stocks = new HashMap<>();
        s1.stocks().forEach(s -> stocks.put(s.path("symbol").asText(), s));
        Map<String, Long> inventory = new HashMap<>();
        s1.portfolio().forEach(p -> inventory.put(p.path("symbol").asText(), p.path("quantity").asLong()));
        long now = System.currentTimeMillis();

        for (JsonNode c : s1.myConfigs()) {
            String symbol = c.path("symbol").asText();
            String settings = c.toString();
            boolean changed = !settings.equals(lastSettings.get(symbol));
            if (changed && lastSettings.containsKey(symbol)) {
                log.info("Settings changed for {} (spread {} bps, qty {}, enabled {}); re-quoting now", symbol,
                        c.path("spreadBps").asInt(), c.path("quoteQty").asLong(), c.path("enabled").asBoolean());
            }
            lastSettings.put(symbol, settings);

            if (!c.path("enabled").asBoolean()) {
                cancelQuotes(symbol);
                continue;
            }
            boolean due = now - lastQuoteAt.getOrDefault(symbol, 0L) >= c.path("refreshMs").asLong(2000);
            if (!due && !changed) continue;

            JsonNode stock = stocks.get(symbol);
            if (stock == null) continue;
            requote(symbol, c, stock, inventory.getOrDefault(symbol, 0L));
            lastQuoteAt.put(symbol, now);
        }
    }

    private void requote(String symbol, JsonNode c, JsonNode stock, long inv) {
        BigDecimal mid = stock.path("value").decimalValue();
        BigDecimal tick = stock.hasNonNull("tickSize") ? stock.path("tickSize").decimalValue() : new BigDecimal("0.01");
        long qty = c.path("quoteQty").asLong();
        long max = c.path("maxInventory").asLong();
        QuotePricer.Quote q = QuotePricer.price(mid, c.path("spreadBps").asInt(), c.path("skewFactor").decimalValue(),
                inv, max, qty, tick);

        if (q.atInventoryLimit() && !limitReported.getOrDefault(symbol, false)) {
            s1.reportInventoryLimit(symbol, inv, max);
            limitReported.put(symbol, true);
            log.warn("{}: inventory {} reached max {}; BUY side paused", symbol, inv, max);
        } else if (!q.atInventoryLimit()) {
            limitReported.put(symbol, false);
        }

        cancelQuotes(symbol);
        List<Long> ids = new ArrayList<>();
        if (q.quoteBid()) s1.placeLimit(symbol, "BUY", q.bid(), qty).ifPresent(ids::add);
        if (q.quoteAsk()) s1.placeLimit(symbol, "SELL", q.ask(), qty).ifPresent(ids::add);
        liveQuotes.put(symbol, ids);
        log.debug("{}: bid {} ask {} (mid {}, inventory {})", symbol, q.bid(), q.ask(), mid, inv);
    }

    private void cancelQuotes(String symbol) {
        List<Long> old = liveQuotes.remove(symbol);
        if (old != null) old.forEach(s1::cancel);
    }
}
