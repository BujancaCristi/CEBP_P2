package ro.upt.exchange.s1.events;

import java.math.BigDecimal;

/** Payload of {@code exchange.prices}: PriceUpdated. */
public record PriceUpdatedPayload(String symbol, BigDecimal value, BigDecimal previousClose) {
}
