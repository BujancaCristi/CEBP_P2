package ro.upt.exchange.mm;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure quote pricing (no I/O), so it can be unit-tested.
 *
 * <pre>
 *   half   = spread / 2                       (spread = spreadBps / 10 000)
 *   skew   = skewFactor * (inventory - max/2) / (max/2)      in [-skewFactor, +skewFactor]
 *   bid    = mid * (1 - half - skew * half)   rounded down to the tick
 *   ask    = mid * (1 + half - skew * half)   rounded up to the tick
 * </pre>
 * Holding more than half of max inventory gives a positive skew, which moves both prices down so the
 * bot sells more easily; holding less moves them up. At max inventory the BUY side is dropped; with fewer
 * shares than one quote the SELL side is dropped.
 */
public final class QuotePricer {

    public record Quote(BigDecimal bid, BigDecimal ask, boolean quoteBid, boolean quoteAsk, boolean atInventoryLimit) {}

    private QuotePricer() {}

    public static Quote price(BigDecimal mid, int spreadBps, BigDecimal skewFactor, long inventory,
                              long maxInventory, long quoteQty, BigDecimal tick) {
        if (mid == null || mid.signum() <= 0) throw new IllegalArgumentException("mid must be positive");
        double m = mid.doubleValue();
        double half = spreadBps / 20_000.0;
        double halfMax = Math.max(1.0, maxInventory / 2.0);
        double skew = skewFactor.doubleValue() * Math.max(-1.0, Math.min(1.0, (inventory - halfMax) / halfMax));

        BigDecimal bid = toTick(m * (1 - half - skew * half), tick, RoundingMode.FLOOR);
        BigDecimal ask = toTick(m * (1 + half - skew * half), tick, RoundingMode.CEILING);
        if (ask.compareTo(bid) <= 0) ask = bid.add(tick);
        if (bid.signum() <= 0) bid = tick;

        boolean atLimit = inventory >= maxInventory;
        boolean quoteBid = !atLimit;
        boolean quoteAsk = inventory >= quoteQty;
        return new Quote(bid, ask, quoteBid, quoteAsk, atLimit);
    }

    static BigDecimal toTick(double price, BigDecimal tick, RoundingMode mode) {
        BigDecimal ticks = BigDecimal.valueOf(price).divide(tick, 0, mode);
        return ticks.multiply(tick).setScale(tick.scale(), RoundingMode.UNNECESSARY);
    }
}
