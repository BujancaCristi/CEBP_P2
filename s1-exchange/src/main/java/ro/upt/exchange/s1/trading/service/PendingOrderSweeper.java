package ro.upt.exchange.s1.trading.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ro.upt.exchange.s1.domain.Order;
import ro.upt.exchange.s1.domain.OrderRepository;
import ro.upt.exchange.s1.domain.OrderStatus;
import ro.upt.exchange.s1.trading.TradingProperties;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Safety net for the one gap in "save, commit, then publish the command": if Kafka was down for
 * that instant, the order would stay PENDING forever. Every few seconds, re-publish PlaceOrder
 * for PENDING orders older than {@code exchange.matching.pending-timeout-seconds}. Commands are
 * idempotent (the matcher skips non-PENDING orders), so a duplicate is harmless.
 */
@Component
public class PendingOrderSweeper {

    private static final Logger log = LoggerFactory.getLogger(PendingOrderSweeper.class);

    private final OrderRepository orders;
    private final StockLookup stocks;
    private final OrderCommandPublisher commands;
    private final TradingProperties props;

    public PendingOrderSweeper(OrderRepository orders, StockLookup stocks, OrderCommandPublisher commands, TradingProperties props) {
        this.orders = orders;
        this.stocks = stocks;
        this.commands = commands;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${exchange.matching.sweep-interval-ms:5000}", initialDelay = 15_000)
    @Transactional(readOnly = true)
    public void sweep() {
        Instant cutoff = Instant.now().minus(Duration.ofSeconds(props.matching().pendingTimeoutSeconds()));
        List<Order> stuck = orders.findByStatusAndCreatedTimeBefore(OrderStatus.PENDING, cutoff);
        if (stuck.isEmpty()) return;
        log.warn("Re-publishing PlaceOrder for {} order(s) still PENDING since before {}", stuck.size(), cutoff);
        for (Order o : stuck) {
            commands.place(o, stocks.byId(o.getStockId()).getSymbol());
        }
    }
}
