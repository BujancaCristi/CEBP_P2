package ro.upt.exchange.s1.trading.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * {@code POST /api/offers/{orderId}/buy} body.
 *
 * @param qty         shares to buy from this offer (≤ remaining)
 * @param maxPrice    the price the buyer saw and accepts; the buy fails with 409 PRICE_CHANGED if
 *                    the offer is now more expensive
 * @param seenVersion the offer's {@code version} as shown to the buyer (concurrency case 2)
 */
public record BuyOfferRequest(
        @Positive @Max(1_000_000_000) long qty,
        @NotNull @Positive @Digits(integer = 14, fraction = 4) BigDecimal maxPrice,
        @PositiveOrZero long seenVersion) {
}
