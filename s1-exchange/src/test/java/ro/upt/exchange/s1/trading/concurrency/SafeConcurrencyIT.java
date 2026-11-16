package ro.upt.exchange.s1.trading.concurrency;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** exchange.concurrency-mode=SAFE: every case must show 0 violations in 200 rounds. */
@TestPropertySource(properties = "exchange.concurrency-mode=SAFE")
class SafeConcurrencyIT extends ConcurrencyScenarios {

    @Test
    void case1_twoBuyers_exactlyOneSucceeds() {
        Outcome o = case1TwoBuyers();
        report(o);
        assertThat(o.violations()).as(o.toString()).isZero();
    }

    @Test
    void case2_priceChange_neverBuysAboveSeenPrice() {
        Outcome o = case2PriceChange();
        report(o);
        assertThat(o.violations()).as(o.toString()).isZero();
    }

    @Test
    void case3_demandExceedsSupply_totalSoldIsExactly100() {
        Outcome o = case3DemandExceedsSupply();
        report(o);
        assertThat(o.violations()).as(o.toString()).isZero();
    }
}
