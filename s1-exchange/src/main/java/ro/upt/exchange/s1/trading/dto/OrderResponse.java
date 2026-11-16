package ro.upt.exchange.s1.trading.dto;

import ro.upt.exchange.s1.domain.Order;
import ro.upt.exchange.s1.domain.OrderSide;
import ro.upt.exchange.s1.domain.OrderSource;
import ro.upt.exchange.s1.domain.OrderStatus;
import ro.upt.exchange.s1.domain.OrderType;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponse(
        long id,
        String symbol,
        OrderSide side,
        OrderType type,
        BigDecimal price,
        long qty,
        long remainingQty,
        long filledQty,
        OrderStatus status,
        String rejectReason,
        OrderSource source,
        Instant createdTime,
        Instant updatedAt,
        long version) {

    public static OrderResponse from(Order o, String symbol) {
        return new OrderResponse(o.getId(), symbol, o.getSide(), o.getType(), o.getPrice(), o.getQty(),
                o.getRemainingQty(), o.filledQty(), o.getStatus(), o.getRejectReason(), o.getSource(),
                o.getCreatedTime(), o.getUpdatedAt(), o.getVersion());
    }
}
