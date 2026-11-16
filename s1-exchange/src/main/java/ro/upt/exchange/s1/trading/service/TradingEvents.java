package ro.upt.exchange.s1.trading.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ro.upt.exchange.s1.config.ExchangeTopics;
import ro.upt.exchange.s1.domain.Order;
import ro.upt.exchange.s1.domain.Stock;
import ro.upt.exchange.s1.domain.Trade;
import ro.upt.exchange.s1.events.EventTypes;
import ro.upt.exchange.s1.events.OrderEventPayload;
import ro.upt.exchange.s1.events.PriceUpdatedPayload;
import ro.upt.exchange.s1.events.TradeExecutedPayload;
import ro.upt.exchange.s1.platform.EventOutbox;
import ro.upt.exchange.s1.realtime.LivePublisher;

import java.math.BigDecimal;

/**
 * One place where trading emits its side effects: a row in the outbox (same transaction, Kafka
 * after the relay) and a live push over STOMP (after commit, so the web app never sees a change
 * that rolls back).
 */
@Component
public class TradingEvents {

    private static final Logger log = LoggerFactory.getLogger(TradingEvents.class);

    private final EventOutbox outbox;
    private final LivePublisher live;
    private final ExchangeTopics topics;
    private final BookService books;

    public TradingEvents(EventOutbox outbox, LivePublisher live, ExchangeTopics topics, BookService books) {
        this.outbox = outbox;
        this.live = live;
        this.topics = topics;
        this.books = books;
    }

    public void orderPlaced(Order o, Stock s) {
        emitOrder(EventTypes.ORDER_PLACED, o, s);
    }

    /** Picks OrderUpdated / OrderCancelled / OrderRejected from the order's status. */
    public void orderChanged(Order o, Stock s) {
        String type = switch (o.getStatus()) {
            case CANCELLED -> EventTypes.ORDER_CANCELLED;
            case REJECTED -> EventTypes.ORDER_REJECTED;
            default -> EventTypes.ORDER_UPDATED;
        };
        emitOrder(type, o, s);
    }

    public void tradeExecuted(Trade t, Stock s) {
        TradeExecutedPayload p = TradeExecutedPayload.from(t, s.getSymbol());
        outbox.add(topics.trades(), s.getSymbol(), EventTypes.TRADE_EXECUTED, p);
        afterCommit(() -> live.tradeExecuted(p));
    }

    public void priceUpdated(Stock s, BigDecimal value) {
        PriceUpdatedPayload p = new PriceUpdatedPayload(s.getSymbol(), value, s.getPreviousClose());
        outbox.add(topics.prices(), s.getSymbol(), EventTypes.PRICE_UPDATED, p);
        afterCommit(() -> live.priceChanged(p));
    }

    /** Live-only (the book is derived state; S2 rebuilds it from order events if it ever needs to). */
    public void bookChanged(Stock s) {
        afterCommit(() -> live.bookChanged(books.snapshot(s, BookService.DEFAULT_DEPTH)));
    }

    private void emitOrder(String type, Order o, Stock s) {
        OrderEventPayload p = OrderEventPayload.from(o, s.getSymbol());
        outbox.add(topics.orders(), s.getSymbol(), type, p);
        afterCommit(() -> live.orderUpdated(o.getUserId(), p));
    }

    static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safely(action);
                }
            });
        } else {
            safely(action);
        }
    }

    private static void safely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            // a live push must never break a committed trade
            log.warn("Post-commit action failed: {}", e.toString());
        }
    }
}
