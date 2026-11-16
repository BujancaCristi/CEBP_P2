package ro.upt.exchange.s1.trading.dto;

import ro.upt.exchange.s1.domain.Order;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * An offer = an open SELL order. {@code totalPrice = price × remainingQty}; {@code version} must be
 * sent back as {@code seenVersion} when buying.
 */
public record OfferResponse(
        long orderId,
        String symbol,
        long sellerUserId,
        BigDecimal price,
        long remainingQty,
        BigDecimal totalPrice,
        long version,
        String status,
        Instant createdTime) {

    public static OfferResponse from(Order o, String symbol) {
        return new OfferResponse(o.getId(), symbol, o.getUserId(), o.getPrice(), o.getRemainingQty(),
                o.totalPrice(), o.getVersion(), o.getStatus().name(), o.getCreatedTime());
    }
}
