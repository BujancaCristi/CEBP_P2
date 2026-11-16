package ro.upt.exchange.s1.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Order book grouped by price level. Returned by {@code GET /api/stocks/{symbol}/book} and pushed
 * on {@code /topic/book/{symbol}} by realtime/ (Paul) - same shape in both places.
 */
public record BookSnapshot(
        String symbol,
        BigDecimal lastPrice,
        BigDecimal bestBid,
        BigDecimal bestAsk,
        List<Level> bids,
        List<Level> asks,
        Instant asOf) {

    public record Level(BigDecimal price, long qty, long orders) {
    }
}
