package ro.upt.exchange.s1.trading.concurrency;

import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * exchange.concurrency-mode=UNSAFE: the deliberately broken variants must show many violations.
 * "Many" = at least 10% of the rounds; the actual counts are what the analysis document reports.
 *
 * <p>This context uses its own command topic and consumer group: the SAFE contexts cached by
 * the other integration tests keep their matcher threads alive, and they must not pick up (and
 * correctly match!) the commands of this one.
 */
@TestPropertySource(properties = {
        "exchange.concurrency-mode=UNSAFE",
        "exchange.topics.order-commands=exchange.order-commands-unsafe-test",
        "exchange.matching.consumer-group=s1-matching-unsafe-test"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UnsafeConcurrencyIT extends ConcurrencyScenarios {

    private static final int MANY = ROUNDS / 10;

    @Test
    void case1_twoBuyers_lostUpdateLetsBothWin() {
        Outcome o = case1TwoBuyers();
        report(o);
        assertThat(o.violations()).as(o.toString()).isGreaterThanOrEqualTo(MANY);
    }

    @Test
    void case2_priceChange_buysAboveSeenPrice() {
        Outcome o = case2PriceChange();
        report(o);
        assertThat(o.violations()).as(o.toString()).isGreaterThanOrEqualTo(MANY);
    }

    @Test
    void case3_demandExceedsSupply_overSells() {
        Outcome o = case3DemandExceedsSupply();
        report(o);
        assertThat(o.violations()).as(o.toString()).isGreaterThanOrEqualTo(MANY);
    }
}
