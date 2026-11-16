package ro.upt.exchange.s1.trading;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** {@code exchange.*} settings owned by trading/. */
@Validated
@ConfigurationProperties(prefix = "exchange")
public record TradingProperties(
        @NotNull @DefaultValue("SAFE") ConcurrencyMode concurrencyMode,
        @NotNull Matching matching) {

    public record Matching(
            @NotBlank @DefaultValue("s1-matching") String consumerGroup,
            @Min(1) @DefaultValue("3") int concurrency,
            /** A PENDING order older than this is re-published by the sweeper. */
            @Min(1) @DefaultValue("10") int pendingTimeoutSeconds) {
    }

    public boolean unsafe() {
        return concurrencyMode == ConcurrencyMode.UNSAFE;
    }
}
