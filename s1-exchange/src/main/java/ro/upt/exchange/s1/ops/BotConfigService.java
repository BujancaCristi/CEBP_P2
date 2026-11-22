package ro.upt.exchange.s1.ops;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ro.upt.exchange.s1.config.ExchangeTopics;
import ro.upt.exchange.s1.domain.BotConfig;
import ro.upt.exchange.s1.domain.BotConfigRepository;
import ro.upt.exchange.s1.domain.Stock;
import ro.upt.exchange.s1.domain.StockRepository;
import ro.upt.exchange.s1.events.BotConfigChangedPayload;
import ro.upt.exchange.s1.events.EventTypes;
import ro.upt.exchange.s1.platform.EventOutbox;
import ro.upt.exchange.s1.realtime.LivePublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Market-maker settings (owner: Paul). Only an admin changes them, through the web app; every change
 * publishes {@code BotConfigChanged} to Kafka (for S2) and pushes it over STOMP to the bot.
 */
@Service
public class BotConfigService {

    /** Validation limits shared with the MCP propose tool (contracts/mcp-tools.md). */
    public static final int MIN_SPREAD_BPS = 1;
    public static final int MAX_SPREAD_BPS = 2000;
    public static final int MIN_REFRESH_MS = 250;

    public record BotConfigView(long configId, long botUserId, String symbol, boolean enabled, int spreadBps,
                                long quoteQty, long maxInventory, BigDecimal skewFactor, int refreshMs,
                                String updatedBy, Instant updatedAt, long version) {}

    public record UpdateRequest(Boolean enabled, Integer spreadBps, Long quoteQty, Long maxInventory,
                                BigDecimal skewFactor, Integer refreshMs) {}

    private final BotConfigRepository configs;
    private final StockRepository stocks;
    private final EventOutbox outbox;
    private final ExchangeTopics topics;
    private final LivePublisher live;

    public BotConfigService(BotConfigRepository configs, StockRepository stocks, EventOutbox outbox,
                            ExchangeTopics topics, LivePublisher live) {
        this.configs = configs;
        this.stocks = stocks;
        this.outbox = outbox;
        this.topics = topics;
        this.live = live;
    }

    @Transactional(readOnly = true)
    public List<BotConfigView> all() {
        return configs.findAll().stream().map(this::view)
                .sorted((a, b) -> a.symbol().compareTo(b.symbol())).toList();
    }

    @Transactional(readOnly = true)
    public List<BotConfigView> forBot(long botUserId) {
        return configs.findByBotUserIdOrderByStockId(botUserId).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public BotConfigView one(long id) {
        return view(find(id));
    }

    @Transactional
    public BotConfigView update(long id, UpdateRequest req, String updatedBy) {
        BotConfig c = find(id);
        if (req.enabled() != null) c.setEnabled(req.enabled());
        if (req.spreadBps() != null) {
            require(req.spreadBps() >= MIN_SPREAD_BPS && req.spreadBps() <= MAX_SPREAD_BPS,
                    "spreadBps must be between " + MIN_SPREAD_BPS + " and " + MAX_SPREAD_BPS);
            c.setSpreadBps(req.spreadBps());
        }
        if (req.quoteQty() != null) {
            require(req.quoteQty() > 0, "quoteQty must be positive");
            c.setQuoteQty(req.quoteQty());
        }
        if (req.maxInventory() != null) {
            require(req.maxInventory() > 0, "maxInventory must be positive");
            c.setMaxInventory(req.maxInventory());
        }
        if (req.skewFactor() != null) {
            require(req.skewFactor().signum() >= 0 && req.skewFactor().compareTo(BigDecimal.ONE) <= 0,
                    "skewFactor must be between 0 and 1");
            c.setSkewFactor(req.skewFactor());
        }
        if (req.refreshMs() != null) {
            require(req.refreshMs() >= MIN_REFRESH_MS, "refreshMs must be at least " + MIN_REFRESH_MS);
            c.setRefreshMs(req.refreshMs());
        }
        require(c.getQuoteQty() <= c.getMaxInventory(), "quoteQty cannot exceed maxInventory");
        c.setUpdatedAt(Instant.now());
        c.setUpdatedBy(updatedBy);
        configs.saveAndFlush(c);

        BotConfigView v = view(c);
        BotConfigChangedPayload payload = new BotConfigChangedPayload(v.configId(), v.botUserId(), v.symbol(),
                v.enabled(), v.spreadBps(), v.quoteQty(), v.maxInventory(), v.skewFactor(), v.refreshMs(),
                v.updatedBy(), v.updatedAt());
        outbox.add(topics.bots(), v.symbol(), EventTypes.BOT_CONFIG_CHANGED, payload);
        afterCommit(() -> live.botConfigChanged(payload));
        return v;
    }

    /** Publishes the current settings of every bot (used once after seeding so S2 knows them). */
    @Transactional
    public void publishAll(String updatedBy) {
        for (BotConfig c : configs.findAll()) {
            BotConfigView v = view(c);
            outbox.add(topics.bots(), v.symbol(), EventTypes.BOT_CONFIG_CHANGED,
                    new BotConfigChangedPayload(v.configId(), v.botUserId(), v.symbol(), v.enabled(), v.spreadBps(),
                            v.quoteQty(), v.maxInventory(), v.skewFactor(), v.refreshMs(), updatedBy, v.updatedAt()));
        }
    }

    private BotConfig find(long id) {
        return configs.findById(id).orElseThrow(() -> new IllegalArgumentException("Bot config " + id + " not found"));
    }

    private BotConfigView view(BotConfig c) {
        String symbol = stocks.findById(c.getStockId()).map(Stock::getSymbol).orElse("?");
        return new BotConfigView(c.getId(), c.getBotUserId(), symbol, c.isEnabled(), c.getSpreadBps(), c.getQuoteQty(),
                c.getMaxInventory(), c.getSkewFactor(), c.getRefreshMs(), c.getUpdatedBy(), c.getUpdatedAt(),
                c.getVersion());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private static void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    r.run();
                }
            });
        } else {
            r.run();
        }
    }
}
