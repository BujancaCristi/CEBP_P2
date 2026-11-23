package ro.upt.exchange.s1.ops;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

public record UpdateBotConfigRequest(
        Boolean enabled,
        @Min(1) @Max(1000) Integer spreadBps,
        @Min(1) Long quoteQty,
        @Min(1) Long maxInventory,
        BigDecimal skewFactor,
        @Min(200) Integer refreshMs
) {
}
