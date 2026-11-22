package ro.upt.exchange.s1.ops;

import ro.upt.exchange.s1.domain.BotConfig;

import java.math.BigDecimal;
import java.time.Instant;

public record BotConfigDto(
        long id,
        long botUserId,
        long stockId,
        String symbol,
        boolean enabled,
        int spreadBps,
        long quoteQty,
        long maxInventory,
        BigDecimal skewFactor,
        int refreshMs,
        String updatedBy,
        Instant updatedAt,
        long version
) {
    public static BotConfigDto from(BotConfig config, String symbol) {
        return new BotConfigDto(
                config.getId(),
                config.getBotUserId(),
                config.getStockId(),
                symbol,
                config.isEnabled(),
                config.getSpreadBps(),
                config.getQuoteQty(),
                config.getMaxInventory(),
                config.getSkewFactor(),
                config.getRefreshMs(),
                config.getUpdatedBy(),
                config.getUpdatedAt(),
                config.getVersion()
        );
    }
}
