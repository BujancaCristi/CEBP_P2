package ro.upt.exchange.s1.trading.service;

import ro.upt.exchange.s1.domain.Order;
import ro.upt.exchange.s1.trading.ConcurrencyMode;

import java.math.BigDecimal;

/**
 * The two quantity-changing writes whose correctness the concurrency cases are about.
 * One implementation per {@link ConcurrencyMode}.
 */
public interface OrderWriteStrategy {

    ConcurrencyMode mode();

    /**
     * Buy-from-offer (cases 1 and 2). Takes {@code qty} shares from {@code offer}.
     *
     * @return 1 if the shares were taken, 0 if the offer changed / is gone / is pricier than maxPrice
     */
    int takeOffer(Order offer, long qty, long seenVersion, BigDecimal maxPrice);

    /**
     * Matching (case 3). Fills {@code qty} of {@code order} (incoming or resting).
     *
     * @return 1 if applied, 0 if the order's remaining quantity changed underneath
     */
    int fill(Order order, long qty);

    /** Kafka key for a command about this order: what decides which consumer thread matches it. */
    String commandKey(long orderId, String symbol);
}
