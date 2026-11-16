package ro.upt.exchange.s1.trading.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** {@code PATCH /api/orders/{id}} body: new price and/or new total quantity (at least one). */
public record ModifyOrderRequest(
        @Positive @Digits(integer = 14, fraction = 4) BigDecimal price,
        @Positive @Max(1_000_000_000) Long qty) {

    @AssertTrue(message = "at least one of price, qty must be given")
    public boolean isSomethingToChange() {
        return price != null || qty != null;
    }
}
