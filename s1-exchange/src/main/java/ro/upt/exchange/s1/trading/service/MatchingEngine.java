package ro.upt.exchange.s1.trading.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.upt.exchange.s1.domain.*;
import ro.upt.exchange.s1.events.EventEnvelope;
import ro.upt.exchange.s1.events.EventTypes;
import ro.upt.exchange.s1.events.OrderCommandPayload;
import ro.upt.exchange.s1.ops.IncidentService;
import ro.upt.exchange.s1.platform.AccountLedger;
import ro.upt.exchange.s1.platform.InsufficientFundsException;
import ro.upt.exchange.s1.platform.InsufficientSharesException;
import ro.upt.exchange.s1.trading.error.CommandRejectedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * The matching engine (Application_Description.pdf §3.1 steps 3-5; concurrency case 3).
 *
 * <p>Runs on the consumer thread of {@code exchange.order-commands}. Commands are keyed by symbol,
 * so all commands of one stock arrive on one partition and are applied by one thread, in order:
 * the queue is the lock. Each command is one PostgreSQL transaction: orders, trades, cash,
 * shares, stock value and outbox rows all commit together or not at all.
 *
 * <p>Price-time priority: the best price first, then the oldest; a match executes at the
 * resting order's price for {@code min(remaining, remaining)} shares. MARKET orders are
 * immediate-or-cancel: whatever is not filled against the book is cancelled.
 */
@Service
public class MatchingEngine {

    private static final Logger log = LoggerFactory.getLogger(MatchingEngine.class);
    private static final int MAX_FILLS_PER_COMMAND = 10_000;
    private static final int CAS_RETRIES = 5;

    private final OrderRepository orders;
    private final TradeRepository trades;
    private final StockRepository stockRepo;
    private final AccountLedger ledger;
    private final OrderWriteStrategy writes;
    private final TradingEvents events;
    private final IncidentService incidents;

    public MatchingEngine(OrderRepository orders, TradeRepository trades, StockRepository stockRepo,
                          AccountLedger ledger, OrderWriteStrategy writes, TradingEvents events, IncidentService incidents) {
        this.orders = orders;
        this.trades = trades;
        this.stockRepo = stockRepo;
        this.ledger = ledger;
        this.writes = writes;
        this.events = events;
        this.incidents = incidents;
    }

    @Transactional
    public void handle(EventEnvelope<OrderCommandPayload> command) {
        OrderCommandPayload c = command.payload();
        switch (command.type()) {
            case EventTypes.PLACE_ORDER -> place(c.orderId());
            case EventTypes.MODIFY_ORDER -> modify(c.orderId(), c.price(), c.qty());
            case EventTypes.CANCEL_ORDER -> cancel(c.orderId());
            default -> log.warn("Unknown order command type {} (event {})", command.type(), command.eventId());
        }
    }

    // ------------------------------------------------------------------ place

    void place(long orderId) {
        Order order = orders.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("PlaceOrder for unknown order {}", orderId);
            return;
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            return; // replayed command: already matched
        }
        match(order);
    }

    /** Match a PENDING or live order against the opposite side, then rest, fill or cancel it. */
    private void match(Order order) {
        Stock stock = stockRepo.findById(order.getStockId()).orElseThrow();
        boolean buy = order.getSide() == OrderSide.BUY;
        boolean market = order.getType() == OrderType.MARKET;
        long remaining = order.getRemainingQty();
        boolean fundsExhausted = false;

        for (int i = 0; i < MAX_FILLS_PER_COMMAND && remaining > 0; i++) {
            Order resting = bestResting(order, buy);
            if (resting == null || (!market && !crosses(order.getPrice(), resting.getPrice(), buy))) {
                break;
            }
            long qty = Math.min(remaining, resting.getRemainingQty());
            BigDecimal price = resting.getPrice();
            BigDecimal total = Trade.totalOf(qty, price);

            if (buy && market) {
                try {
                    ledger.reserveCash(order.getAccountId(), total);
                } catch (InsufficientFundsException e) {
                    fundsExhausted = true;
                    break;
                }
            }

            if (writes.fill(resting, qty) == 0) {
                // the resting order changed underneath (a buy-from-offer took it): re-read the book
                if (buy && market) ledger.releaseCash(order.getAccountId(), total);
                continue;
            }
            if (writes.fill(order, qty) == 0) {
                throw new IllegalStateException("Incoming order " + order.getId() + " lost " + qty + " shares mid-match");
            }

            Trade trade = trades.save(buy ? new Trade(order, resting, qty, price) : new Trade(resting, order, qty, price));
            ledger.settleTrade(trade);
            if (buy && !market) {
                // reserved at the limit, paid at the resting price: give back the difference
                BigDecimal excess = Trade.totalOf(qty, order.getPrice()).subtract(total);
                ledger.releaseCash(order.getAccountId(), excess);
            }
            stockRepo.updateValue(stock.getId(), price);

            Order restingNow = orders.findById(resting.getId()).orElseThrow();
            events.tradeExecuted(trade, stock);
            events.priceUpdated(stock, price);
            events.orderChanged(restingNow, stock);
            remaining -= qty;
            order.setRemainingQty(remaining); // local bookkeeping only (the row was written by fill)
        }

        Order fin = orders.findById(order.getId()).orElseThrow();
        if (fin.getRemainingQty() == 0) {
            events.orderChanged(fin, stock);                   // FILLED
        } else if (market || fundsExhausted) {
            // immediate-or-cancel: keep the fills that happened, cancel the rest
            String reason = fundsExhausted ? "INSUFFICIENT_FUNDS" : "NO_LIQUIDITY";
            cancelWithRelease(fin, stock, reason);
            if (fundsExhausted) {
                incidents.raise(IncidentType.INSUFFICIENT_FUNDS, IncidentSeverity.LOW, stock.getId(), fin.getId(), fin.getUserId(),
                        "MARKET BUY " + fin.getId() + " ran out of cash after " + fin.filledQty() + " of " + fin.getQty() + " shares",
                        Map.of("filledQty", fin.filledQty(), "qty", fin.getQty()));
            }
        } else {
            orders.open(fin.getId());                          // PENDING → OPEN / PARTIALLY_FILLED
            events.orderChanged(orders.findById(fin.getId()).orElseThrow(), stock);
        }
        events.bookChanged(stock);
    }

    private Order bestResting(Order order, boolean buy) {
        List<Order> best = buy
                ? orders.findRestingSells(order.getStockId(), order.getUserId(), PageRequest.of(0, 1))
                : orders.findRestingBuys(order.getStockId(), order.getUserId(), PageRequest.of(0, 1));
        return best.isEmpty() ? null : best.get(0);
    }

    private static boolean crosses(BigDecimal limit, BigDecimal restingPrice, boolean buy) {
        return buy ? limit.compareTo(restingPrice) >= 0 : limit.compareTo(restingPrice) <= 0;
    }

    // ------------------------------------------------------------------ cancel

    void cancel(long orderId) {
        for (int attempt = 0; attempt < CAS_RETRIES; attempt++) {
            Order o = orders.findById(orderId).orElse(null);
            if (o == null || !OrderStatus.CANCELLABLE.contains(o.getStatus())) {
                return; // already final (or replayed command)
            }
            Stock stock = stockRepo.findById(o.getStockId()).orElseThrow();
            if (orders.cancel(orderId, "CANCELLED_BY_USER", o.getVersion()) == 1) {
                release(o, stock);
                events.orderChanged(orders.findById(orderId).orElseThrow(), stock);
                events.bookChanged(stock);
                return;
            }
            // somebody (buy-from-offer) changed the row between our read and the CAS: re-read
        }
        throw new IllegalStateException("Cancel of order " + orderId + " kept losing the CAS race");
    }

    private void cancelWithRelease(Order o, Stock stock, String reason) {
        if (orders.cancel(o.getId(), reason, o.getVersion()) == 1) {
            release(o, stock);
            events.orderChanged(orders.findById(o.getId()).orElseThrow(), stock);
        }
    }

    /** Give back what is still reserved for the unfilled remainder. */
    private void release(Order o, Stock stock) {
        if (o.getRemainingQty() == 0) return;
        if (o.getSide() == OrderSide.SELL) {
            ledger.releaseShares(o.getAccountId(), stock.getId(), o.getRemainingQty());
        } else if (o.getType() == OrderType.LIMIT) {
            ledger.releaseCash(o.getAccountId(), Trade.totalOf(o.getRemainingQty(), o.getPrice()));
        }
        // MARKET BUY reserves per fill, nothing left to release
    }

    // ------------------------------------------------------------------ modify

    void modify(long orderId, BigDecimal newPrice, Long newQty) {
        for (int attempt = 0; attempt < CAS_RETRIES; attempt++) {
            Order o = orders.findById(orderId).orElse(null);
            if (o == null || !o.getStatus().isLive() || o.getType() != OrderType.LIMIT) {
                return;
            }
            Stock stock = stockRepo.findById(o.getStockId()).orElseThrow();
            BigDecimal price = newPrice != null ? newPrice : o.getPrice();
            long qty = newQty != null ? newQty : o.getQty();
            long newRemaining = qty - o.filledQty();
            if (newRemaining <= 0) {
                throw new CommandRejectedException(IncidentType.ORDER_REJECTED, orderId, o.getUserId(), stock.getId(),
                        "Modify rejected: new qty " + qty + " is not above the filled quantity " + o.filledQty());
            }

            if (orders.reprice(orderId, price, qty, o.getVersion()) == 0) {
                continue; // CAS lost: re-read
            }
            // Adjust the reservation to the new remaining × price. Done after the CAS so that a
            // failure here rolls back both (the exception propagates out of the transaction).
            try {
                if (o.getSide() == OrderSide.BUY) {
                    BigDecimal before = Trade.totalOf(o.getRemainingQty(), o.getPrice());
                    BigDecimal after = Trade.totalOf(newRemaining, price);
                    int cmp = after.compareTo(before);
                    if (cmp > 0) ledger.reserveCash(o.getAccountId(), after.subtract(before));
                    else if (cmp < 0) ledger.releaseCash(o.getAccountId(), before.subtract(after));
                } else {
                    long delta = newRemaining - o.getRemainingQty();
                    if (delta > 0) ledger.reserveShares(o.getAccountId(), stock.getId(), delta);
                    else if (delta < 0) ledger.releaseShares(o.getAccountId(), stock.getId(), -delta);
                }
            } catch (InsufficientFundsException e) {
                throw new CommandRejectedException(IncidentType.INSUFFICIENT_FUNDS, orderId, o.getUserId(), stock.getId(),
                        "Modify rejected: " + e.getMessage());
            } catch (InsufficientSharesException e) {
                throw new CommandRejectedException(IncidentType.INSUFFICIENT_SHARES, orderId, o.getUserId(), stock.getId(),
                        "Modify rejected: " + e.getMessage());
            }

            Order updated = orders.findById(orderId).orElseThrow();
            events.orderChanged(updated, stock);
            match(updated); // the new price may cross the book
            return;
        }
        throw new IllegalStateException("Modify of order " + orderId + " kept losing the CAS race");
    }
}
