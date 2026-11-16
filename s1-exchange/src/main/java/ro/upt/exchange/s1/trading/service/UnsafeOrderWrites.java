package ro.upt.exchange.s1.trading.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ro.upt.exchange.s1.domain.Order;
import ro.upt.exchange.s1.domain.OrderRepository;
import ro.upt.exchange.s1.trading.ConcurrencyMode;

import java.math.BigDecimal;

/**
 * UNSAFE - deliberately broken, for the concurrency demonstration only.
 *
 * <ul>
 *   <li>Case 1: reads {@code remaining_qty}, checks it in Java, writes the computed value. Two
 *       buyers who both read 100 both write 0 → lost update, both get a trade.</li>
 *   <li>Case 2: ignores {@code seenVersion} and {@code maxPrice}: buys at whatever the price is now.</li>
 *   <li>Case 3: commands keyed by order id → orders for one stock are matched in parallel, and
 *       fills are read-check-write on a stale {@code remaining_qty}.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(prefix = "exchange", name = "concurrency-mode", havingValue = "UNSAFE")
public class UnsafeOrderWrites implements OrderWriteStrategy {

    private static final Logger log = LoggerFactory.getLogger(UnsafeOrderWrites.class);

    private final OrderRepository orders;

    public UnsafeOrderWrites(OrderRepository orders) {
        this.orders = orders;
        log.warn("exchange.concurrency-mode=UNSAFE - trading writes are deliberately broken (demo mode)");
    }

    @Override
    public ConcurrencyMode mode() {
        return ConcurrencyMode.UNSAFE;
    }

    @Override
    public int takeOffer(Order offer, long qty, long seenVersion, BigDecimal maxPrice) {
        // "check it in Java": the value was read by a plain SELECT a few statements ago
        if (!offer.getStatus().isLive() || offer.getRemainingQty() < qty) {
            return 0;
        }
        long computed = offer.getRemainingQty() - qty;
        return orders.takeOfferUnsafe(offer.getId(), computed);
    }

    @Override
    public int fill(Order order, long qty) {
        long computed = order.getRemainingQty() - qty;
        if (computed < 0) {
            return 0;
        }
        return orders.fillUnsafe(order.getId(), computed);
    }

    @Override
    public String commandKey(long orderId, String symbol) {
        return Long.toString(orderId);
    }
}
