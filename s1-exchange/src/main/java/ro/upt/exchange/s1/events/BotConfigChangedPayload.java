package ro.upt.exchange.s1.events;

import java.math.BigDecimal;
import java.time.Instant;

/** Payload of {@code exchange.bots}: BotConfigChanged. Produced by ops/ (Paul). */
public record BotConfigChangedPayload(
        long configId,
        long botUserId,
        String symbol,
        boolean enabled,
        int spreadBps,
        long quoteQty,
        long maxInventory,
        BigDecimal skewFactor,
        int refreshMs,
        String updatedBy,
        Instant updatedAt) {
}
