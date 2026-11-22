package ro.upt.exchange.bot.service;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ro.upt.exchange.bot.client.ExchangeApiClient;
import ro.upt.exchange.bot.client.ExchangeStompClient;
import ro.upt.exchange.bot.config.BotProperties;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Headless Automated Market Maker Quoting Engine.
 * <b>Owner: Paul.</b>
 *
 * Implements spread quoting, inventory skew, cancel/replace loop, and WebSocket reactions.
 */
@Service
public class MarketMakerService {

    private static final Logger log = LoggerFactory.getLogger(MarketMakerService.class);

    private final ExchangeApiClient apiClient;
    private final ExchangeStompClient stompClient;
    private final BotProperties props;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final Map<String, ScheduledFuture<?>> stockRefreshTasks = new ConcurrentHashMap<>();

    // Stock Symbol -> Active bot config
    private final Map<String, Map<String, Object>> botConfigs = new ConcurrentHashMap<>();

    // Stock Symbol -> Currently resting quote order IDs (side -> orderId)
    private final Map<String, Long> activeBuyOrders = new ConcurrentHashMap<>();
    private final Map<String, Long> activeSellOrders = new ConcurrentHashMap<>();

    // Stock Symbol -> Last known inventory
    private final Map<String, Long> inventoryCache = new ConcurrentHashMap<>();

    public MarketMakerService(ExchangeApiClient apiClient, ExchangeStompClient stompClient, BotProperties props) {
        this.apiClient = apiClient;
        this.stompClient = stompClient;
        this.props = props;
    }

    @PostConstruct
    public void init() {
        stompClient.setOnOrderFillListener(this::handleOrderUpdate);
        stompClient.setOnConfigChangeListener(this::handleConfigUpdate);

        if (props.autostart()) {
            scheduler.schedule(this::start, 1000, TimeUnit.MILLISECONDS);
        }
    }

    @PreDestroy
    public void cleanup() {
        stop();
        scheduler.shutdownNow();
    }

    public synchronized boolean start() {
        if (running.get()) {
            log.info("Market Maker is already running.");
            return true;
        }

        log.info("Starting Market Maker bot...");
        boolean loggedIn = apiClient.login();
        if (!loggedIn) {
            log.warn("Login failed. Will retry in {} ms", props.connectRetryIntervalMs());
            scheduler.schedule(this::start, props.connectRetryIntervalMs(), TimeUnit.MILLISECONDS);
            return false;
        }

        // Connect STOMP WebSocket in background
        scheduler.execute(() -> {
            try {
                stompClient.connect();
            } catch (Exception e) {
                log.warn("Could not immediately connect STOMP: {}", e.getMessage());
            }
        });

        // Load configs
        reloadConfigs();

        running.set(true);

        // Start quoting tasks per stock
        for (Map<String, Object> config : botConfigs.values()) {
            scheduleStockQuoting(config);
        }

        log.info("Market Maker started successfully for {} stocks.", botConfigs.size());
        return true;
    }

    public synchronized void stop() {
        if (!running.get()) return;

        log.info("Stopping Market Maker bot and canceling all active quotes...");
        running.set(false);

        // Cancel all scheduled tasks
        stockRefreshTasks.values().forEach(task -> task.cancel(true));
        stockRefreshTasks.clear();

        // Cancel all active quotes
        cancelAllQuotes();
        log.info("Market Maker stopped.");
    }

    public boolean isRunning() {
        return running.get();
    }

    public Map<String, Map<String, Object>> getConfigs() {
        return Collections.unmodifiableMap(botConfigs);
    }

    public Map<String, Long> getInventory() {
        updateInventoryFromPortfolio();
        return Collections.unmodifiableMap(inventoryCache);
    }

    public synchronized void reloadConfigs() {
        List<Map<String, Object>> configs = apiClient.getMyConfigs();
        botConfigs.clear();
        for (Map<String, Object> cfg : configs) {
            String symbol = String.valueOf(cfg.get("symbol"));
            botConfigs.put(symbol, cfg);
        }
        log.info("Loaded {} bot configs from S1.", botConfigs.size());
    }

    private void scheduleStockQuoting(Map<String, Object> config) {
        String symbol = String.valueOf(config.get("symbol"));
        boolean enabled = Boolean.TRUE.equals(config.get("enabled"));

        // Cancel existing task if any
        ScheduledFuture<?> existing = stockRefreshTasks.remove(symbol);
        if (existing != null) existing.cancel(true);

        if (!enabled || !running.get()) {
            cancelQuotesForStock(symbol);
            return;
        }

        int refreshMs = config.get("refreshMs") != null ? Integer.parseInt(String.valueOf(config.get("refreshMs"))) : 2000;
        refreshMs = Math.max(200, refreshMs);

        ScheduledFuture<?> task = scheduler.scheduleWithFixedDelay(
                () -> quoteStock(symbol),
                500,
                refreshMs,
                TimeUnit.MILLISECONDS
        );
        stockRefreshTasks.put(symbol, task);
    }

    /**
     * Quoting loop logic for a single stock:
     * 1. Works out mid price
     * 2. Shifts by inventory skew
     * 3. Cancels old quotes
     * 4. Places new BUY and SELL quotes
     */
    public void quoteStock(String symbol) {
        if (!running.get()) return;

        Map<String, Object> config = botConfigs.get(symbol);
        if (config == null || !Boolean.TRUE.equals(config.get("enabled"))) {
            cancelQuotesForStock(symbol);
            return;
        }

        try {
            // Determine mid price
            BigDecimal midPrice = calculateMidPrice(symbol);
            if (midPrice == null || midPrice.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("Could not determine mid price for {}, skipping quote.", symbol);
                return;
            }

            int spreadBps = config.get("spreadBps") != null ? Integer.parseInt(String.valueOf(config.get("spreadBps"))) : 80;
            long quoteQty = config.get("quoteQty") != null ? Long.parseLong(String.valueOf(config.get("quoteQty"))) : 50;
            long maxInventory = config.get("maxInventory") != null ? Long.parseLong(String.valueOf(config.get("maxInventory"))) : 2000;
            double skewFactor = config.get("skewFactor") != null ? Double.parseDouble(String.valueOf(config.get("skewFactor"))) : 0.5;

            // Update inventory
            long currentInventory = inventoryCache.getOrDefault(symbol, 0L);

            // Compute half spread fraction (e.g. 80 bps -> 0.0080 / 2 = 0.0040)
            BigDecimal halfSpreadFrac = BigDecimal.valueOf(spreadBps).divide(BigDecimal.valueOf(20000), 6, RoundingMode.HALF_UP);

            // Inventory skew: shift prices based on holding
            double halfMax = maxInventory / 2.0;
            double normalizedInventorySkew = halfMax > 0 ? (currentInventory - halfMax) / halfMax : 0.0;
            // Bound skew between -1.0 and 1.0
            normalizedInventorySkew = Math.max(-1.0, Math.min(1.0, normalizedInventorySkew));

            // Price shift factor
            BigDecimal skewShift = BigDecimal.valueOf(normalizedInventorySkew * skewFactor).multiply(halfSpreadFrac);

            BigDecimal skewedMid = midPrice.subtract(midPrice.multiply(skewShift));

            BigDecimal bidPrice = skewedMid.multiply(BigDecimal.ONE.subtract(halfSpreadFrac)).setScale(2, RoundingMode.FLOOR);
            BigDecimal askPrice = skewedMid.multiply(BigDecimal.ONE.add(halfSpreadFrac)).setScale(2, RoundingMode.CEILING);

            // Ensure ask > bid
            if (askPrice.compareTo(bidPrice) <= 0) {
                askPrice = bidPrice.add(new BigDecimal("0.01"));
            }

            // Cancel previous active orders
            cancelQuotesForStock(symbol);

            // Place BUY quote if inventory < maxInventory
            if (currentInventory < maxInventory) {
                Long newBuyId = apiClient.placeLimitOrder(symbol, "BUY", bidPrice, quoteQty);
                if (newBuyId != null) {
                    activeBuyOrders.put(symbol, newBuyId);
                }
            } else {
                log.info("Inventory limit reached for {} (holding {} >= {}). BUY side suppressed.",
                        symbol, currentInventory, maxInventory);
            }

            // Place SELL quote
            Long newSellId = apiClient.placeLimitOrder(symbol, "SELL", askPrice, quoteQty);
            if (newSellId != null) {
                activeSellOrders.put(symbol, newSellId);
            }

            log.debug("Quoted {}: Bid {} @ {}, Ask {} @ {} (Inventory: {})",
                    symbol, quoteQty, bidPrice, quoteQty, askPrice, currentInventory);

        } catch (Exception e) {
            log.error("Error in quoting loop for {}: {}", symbol, e.getMessage());
        }
    }

    private BigDecimal calculateMidPrice(String symbol) {
        // 1. Try order book bestBid and bestAsk
        Map<String, Object> book = apiClient.getBook(symbol);
        if (book != null && book.containsKey("bestBid") && book.containsKey("bestAsk")) {
            Object bestBidObj = book.get("bestBid");
            Object bestAskObj = book.get("bestAsk");
            if (bestBidObj != null && bestAskObj != null) {
                BigDecimal bBid = new BigDecimal(String.valueOf(bestBidObj));
                BigDecimal bAsk = new BigDecimal(String.valueOf(bestAskObj));
                if (bBid.compareTo(BigDecimal.ZERO) > 0 && bAsk.compareTo(BigDecimal.ZERO) > 0) {
                    return bBid.add(bAsk).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
                }
            }
        }

        // 2. Fallback to stock's current traded value
        Map<String, Object> stock = apiClient.getStock(symbol);
        if (stock != null && stock.containsKey("value")) {
            return new BigDecimal(String.valueOf(stock.get("value")));
        }

        return null;
    }

    private void cancelQuotesForStock(String symbol) {
        Long buyId = activeBuyOrders.remove(symbol);
        if (buyId != null) {
            apiClient.cancelOrder(buyId);
        }
        Long sellId = activeSellOrders.remove(symbol);
        if (sellId != null) {
            apiClient.cancelOrder(sellId);
        }
    }

    private void cancelAllQuotes() {
        for (String symbol : new HashSet<>(activeBuyOrders.keySet())) {
            cancelQuotesForStock(symbol);
        }
    }

    private void updateInventoryFromPortfolio() {
        try {
            List<Map<String, Object>> portfolio = apiClient.getPortfolio();
            for (Map<String, Object> pos : portfolio) {
                String symbol = String.valueOf(pos.get("symbol"));
                long qty = pos.get("quantity") != null ? Long.parseLong(String.valueOf(pos.get("quantity"))) : 0L;
                inventoryCache.put(symbol, qty);
            }
        } catch (Exception e) {
            log.warn("Could not refresh portfolio inventory: {}", e.getMessage());
        }
    }

    private void handleOrderUpdate(JsonNode order) {
        String status = order.has("status") ? order.get("status").asText() : "";
        String symbol = order.has("symbol") ? order.get("symbol").asText() : "";

        if ("FILLED".equals(status) || "PARTIALLY_FILLED".equals(status)) {
            log.info("Bot order fill detected on {}! Triggering immediate refresh.", symbol);
            updateInventoryFromPortfolio();
            if (symbol != null && !symbol.isBlank()) {
                scheduler.execute(() -> quoteStock(symbol));
            }
        }
    }

    private void handleConfigUpdate(JsonNode config) {
        String symbol = config.has("symbol") ? config.get("symbol").asText() : "";
        log.info("Bot config update received for {}! Re-scheduling quotes.", symbol);
        reloadConfigs();
        if (symbol != null && !symbol.isBlank()) {
            Map<String, Object> updated = botConfigs.get(symbol);
            if (updated != null) {
                scheduleStockQuoting(updated);
                scheduler.execute(() -> quoteStock(symbol));
            }
        }
    }
}
