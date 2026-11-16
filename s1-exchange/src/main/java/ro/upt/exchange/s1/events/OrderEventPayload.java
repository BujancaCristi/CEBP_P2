package ro.upt.exchange.s1.events;

import ro.upt.exchange.s1.domain.Order;

import java.math.BigDecimal;
import java.time.Instant;

/** Payload of {@code exchange.orders}: OrderPlaced, OrderUpdated, OrderCancelled, OrderRejected. */
public record OrderEventPayload(
        long orderId,
        long userId,
        String symbol,
        String side,
        String type,
        BigDecimal price,
        long qty,
        long remainingQty,
        String status,
        String source,
        String reason,
        long version,
        Instant updatedAt) {

    public static OrderEventPayload from(Order o, String symbol) {
        return new OrderEventPayload(
                o.getId(), o.getUserId(), symbol, o.getSide().name(), o.getType().name(), o.getPrice(),
                o.getQty(), o.getRemainingQty(), o.getStatus().name(), o.getSource().name(),
                o.getRejectReason(), o.getVersion(), o.getUpdatedAt());
    }
}
