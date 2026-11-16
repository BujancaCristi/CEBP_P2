package ro.upt.exchange.s1.trading.dto;

import jakarta.validation.constraints.*;
import ro.upt.exchange.s1.domain.OrderSide;
import ro.upt.exchange.s1.domain.OrderType;

import java.math.BigDecimal;

/** {@code POST /api/orders} body. {@code price} is required for LIMIT and must be absent for MARKET. */
public record PlaceOrderRequest(
        @NotBlank @Size(max = 10) String symbol,
        @NotNull OrderSide side,
        @NotNull OrderType type,
        @Positive @Digits(integer = 14, fraction = 4) BigDecimal price,
        @Positive @Max(1_000_000_000) long qty) {

    @AssertTrue(message = "price is required for LIMIT orders and must be omitted for MARKET orders")
    public boolean isPriceConsistentWithType() {
        if (type == null) return true; // reported by @NotNull
        return type == OrderType.LIMIT ? price != null : price == null;
    }
}
