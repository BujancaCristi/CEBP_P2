package ro.upt.exchange.s1.trading.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ro.upt.exchange.s1.domain.Order;
import ro.upt.exchange.s1.domain.OrderRepository;
import ro.upt.exchange.s1.trading.ConcurrencyMode;

import java.math.BigDecimal;

/**
 * SAFE: every write is one conditional UPDATE whose WHERE clause carries the business rule
 * (enough shares, same version, price accepted). PostgreSQL evaluates the WHERE again after
 * waiting for a competing transaction's row lock, so of two racing writers exactly one changes
 * the row. Commands are keyed by symbol → one matcher thread per stock.
 */
@Component
@ConditionalOnProperty(prefix = "exchange", name = "concurrency-mode", havingValue = "SAFE", matchIfMissing = true)
public class SafeOrderWrites implements OrderWriteStrategy {

    private final OrderRepository orders;

    public SafeOrderWrites(OrderRepository orders) {
        this.orders = orders;
    }

    @Override
    public ConcurrencyMode mode() {
        return ConcurrencyMode.SAFE;
    }

    @Override
    public int takeOffer(Order offer, long qty, long seenVersion, BigDecimal maxPrice) {
        return orders.takeOffer(offer.getId(), qty, seenVersion, maxPrice);
    }

    @Override
    public int fill(Order order, long qty) {
        return orders.fill(order.getId(), qty);
    }

    @Override
    public String commandKey(long orderId, String symbol) {
        return symbol;
    }
}
