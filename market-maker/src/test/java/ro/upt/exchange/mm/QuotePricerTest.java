package ro.upt.exchange.mm;

import org.junit.jupiter.api.Test;
import ro.upt.exchange.mm.QuotePricer.Quote;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class QuotePricerTest {

    static final BigDecimal TICK = new BigDecimal("0.01");
    static final BigDecimal HALF_SKEW = new BigDecimal("0.5");

    @Test
    void neutralInventoryQuotesSymmetricallyAroundMid() {
        // 100 bps spread around 100.00 -> 99.50 / 100.50
        Quote q = QuotePricer.price(new BigDecimal("100.00"), 100, HALF_SKEW, 5_000, 10_000, 20, TICK);
        assertThat(q.bid()).isEqualByComparingTo("99.50");
        assertThat(q.ask()).isEqualByComparingTo("100.50");
        assertThat(q.quoteBid()).isTrue();
        assertThat(q.quoteAsk()).isTrue();
    }

    @Test
    void longInventoryShiftsBothPricesDown() {
        Quote neutral = QuotePricer.price(new BigDecimal("100.00"), 100, HALF_SKEW, 5_000, 10_000, 20, TICK);
        Quote heavy = QuotePricer.price(new BigDecimal("100.00"), 100, HALF_SKEW, 9_000, 10_000, 20, TICK);
        assertThat(heavy.bid()).isLessThan(neutral.bid());
        assertThat(heavy.ask()).isLessThan(neutral.ask());
    }

    @Test
    void shortInventoryShiftsBothPricesUp() {
        Quote neutral = QuotePricer.price(new BigDecimal("100.00"), 100, HALF_SKEW, 5_000, 10_000, 20, TICK);
        Quote light = QuotePricer.price(new BigDecimal("100.00"), 100, HALF_SKEW, 1_000, 10_000, 20, TICK);
        assertThat(light.bid()).isGreaterThan(neutral.bid());
        assertThat(light.ask()).isGreaterThan(neutral.ask());
    }

    @Test
    void atMaxInventoryTheBuySideStops() {
        Quote q = QuotePricer.price(new BigDecimal("50.00"), 50, HALF_SKEW, 10_000, 10_000, 20, TICK);
        assertThat(q.atInventoryLimit()).isTrue();
        assertThat(q.quoteBid()).isFalse();
        assertThat(q.quoteAsk()).isTrue();
    }

    @Test
    void withoutSharesTheSellSideStops() {
        Quote q = QuotePricer.price(new BigDecimal("50.00"), 50, HALF_SKEW, 5, 10_000, 20, TICK);
        assertThat(q.quoteAsk()).isFalse();
        assertThat(q.quoteBid()).isTrue();
    }

    @Test
    void pricesAreOnTheTickAndAskAboveBidEvenForTinySpreads() {
        Quote q = QuotePricer.price(new BigDecimal("15.20"), 1, HALF_SKEW, 5_000, 10_000, 20, TICK);
        assertThat(q.ask()).isGreaterThan(q.bid());
        assertThat(q.bid().remainder(TICK)).isEqualByComparingTo("0");
        assertThat(q.ask().remainder(TICK)).isEqualByComparingTo("0");
    }
}
