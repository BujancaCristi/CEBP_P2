package ro.upt.exchange.s1.trading.concurrency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import ro.upt.exchange.s1.domain.OrderSide;
import ro.upt.exchange.s1.domain.OrderType;
import ro.upt.exchange.s1.domain.Stock;
import ro.upt.exchange.s1.platform.AuthenticatedUser;
import ro.upt.exchange.s1.testsupport.AbstractIntegrationTest;
import ro.upt.exchange.s1.testsupport.Fixtures;
import ro.upt.exchange.s1.trading.TradingProperties;
import ro.upt.exchange.s1.trading.dto.BuyOfferRequest;
import ro.upt.exchange.s1.trading.dto.PlaceOrderRequest;
import ro.upt.exchange.s1.trading.error.OfferConflictException;
import ro.upt.exchange.s1.trading.service.OfferService;
import ro.upt.exchange.s1.trading.service.OrderService;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.awaitility.Awaitility.await;

/**
 * The three concurrency cases of Application_Description.pdf §6, each run {@link #ROUNDS} times.
 * Subclasses fix the mode (SAFE asserts 0 violations, UNSAFE asserts many) and the results feed
 * docs/concurrency-analysis.md.
 *
 * <p>Every round uses a fresh stock, seller and buyers. The seller's position is seeded with a
 * large reservation (as if it had other open SELL orders) so that the database CHECK constraints
 * on {@code reserved_qty} do not mask a lost update - that is exactly what the UNSAFE variants
 * are meant to show (see the PDF: "The CHECK constraints … do not catch a lost update").
 */
@Import(Fixtures.class)
public abstract class ConcurrencyScenarios extends AbstractIntegrationTest {

    protected static final Logger log = LoggerFactory.getLogger(ConcurrencyScenarios.class);
    protected static final int ROUNDS = Integer.getInteger("concurrency.rounds", 200);
    private static final BigDecimal CASH = new BigDecimal("100000.00");
    private static final BigDecimal TEN = new BigDecimal("10.0000");
    private static final BigDecimal TWELVE = new BigDecimal("12.0000");

    @Autowired protected Fixtures fx;
    @Autowired protected OfferService offers;
    @Autowired protected OrderService orders;
    @Autowired protected TradingProperties props;

    private final ExecutorService pool = Executors.newFixedThreadPool(4);

    public record Outcome(String name, int rounds, int violations, String detail) {
        @Override public String toString() {
            return "CONCURRENCY-RESULT %s rounds=%d violations=%d %s".formatted(name, rounds, violations, detail);
        }
    }

    // ------------------------------------------------------------ case 1

    /** One offer of 100; two buyers try to take all 100 at the same time. Exactly one may succeed. */
    protected Outcome case1TwoBuyers() {
        int violations = 0, bothWon = 0, overSold = 0;
        for (int i = 0; i < ROUNDS; i++) {
            Stock stock = fx.stock(TEN);
            AuthenticatedUser seller = fx.trader("c1s", CASH);
            fx.position(seller, stock, 1000, 1000);          // other open sells lock 1000 shares
            long offer = fx.offer(seller, stock, TEN, 100);
            long v = fx.version(offer);
            AuthenticatedUser a = fx.trader("c1a", CASH);
            AuthenticatedUser b = fx.trader("c1b", CASH);

            List<Boolean> won = race(
                    () -> tryBuy(a, offer, new BuyOfferRequest(100, TEN, v)),
                    () -> tryBuy(b, offer, new BuyOfferRequest(100, TEN, v)));

            long sold = fx.soldFrom(offer);
            boolean violation = (won.get(0) && won.get(1)) || sold != 100;
            if (won.get(0) && won.get(1)) bothWon++;
            if (sold > 100) overSold++;
            if (violation) violations++;
        }
        return new Outcome("case1-two-buyers/" + props.concurrencyMode(), ROUNDS, violations,
                "bothSucceeded=" + bothWon + " overSold=" + overSold);
    }

    // ------------------------------------------------------------ case 2

    /**
     * The buyer saw 10.00 (version v) and decides to buy; meanwhile the seller raises the price to
     * 12.00 (version v+1). The buy arrives with the stale version. No trade may execute above the
     * 10.00 the buyer saw: SAFE answers 409 PRICE_CHANGED, UNSAFE sells at 12.00.
     */
    protected Outcome case2PriceChange() {
        int violations = 0, conflicts = 0, bought = 0;
        for (int i = 0; i < ROUNDS; i++) {
            Stock stock = fx.stock(TEN);
            AuthenticatedUser seller = fx.trader("c2s", CASH);
            long offer = fx.offer(seller, stock, TEN, 100);
            long seen = fx.version(offer);                       // what the buyer's screen shows
            AuthenticatedUser buyer = fx.trader("c2b", CASH);

            if (!fx.reprice(offer, TWELVE, seen)) throw new AssertionError("fixture: reprice failed");
            boolean won = tryBuy(buyer, offer, new BuyOfferRequest(100, TEN, seen));

            BigDecimal paid = fx.maxTradePrice(offer);
            if (paid.compareTo(TEN) > 0) violations++;
            if (won) bought++; else conflicts++;
        }
        return new Outcome("case2-price-change/" + props.concurrencyMode(), ROUNDS, violations,
                "buyerGot409=" + conflicts + " boughtAtNewPrice=" + bought);
    }

    // ------------------------------------------------------------ case 3

    /** 100 available; buyers want 70 + 50 through the matching engine. Total sold must be 100. */
    protected Outcome case3DemandExceedsSupply() {
        int violations = 0, overSold = 0;
        for (int i = 0; i < ROUNDS; i++) {
            Stock stock = fx.stock(TEN);
            AuthenticatedUser seller = fx.trader("c3s", CASH);
            fx.position(seller, stock, 1000, 1000);
            long offer = fx.offer(seller, stock, TEN, 100);
            AuthenticatedUser a = fx.trader("c3a", CASH);
            AuthenticatedUser b = fx.trader("c3b", CASH);

            List<Long> ids = race(
                    () -> orders.place(a, new PlaceOrderRequest(stock.getSymbol(), OrderSide.BUY, OrderType.LIMIT, TEN, 70)).order().id(),
                    () -> orders.place(b, new PlaceOrderRequest(stock.getSymbol(), OrderSide.BUY, OrderType.LIMIT, TEN, 50)).order().id());

            await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(20))
                    .until(() -> !fx.status(ids.get(0)).equals("PENDING") && !fx.status(ids.get(1)).equals("PENDING"));

            long sold = fx.soldFrom(offer);
            long remaining = fx.remaining(offer);
            boolean violation = sold != 100 || remaining != 0;
            if (sold > 100) overSold++;
            if (violation) violations++;
        }
        return new Outcome("case3-demand-exceeds-supply/" + props.concurrencyMode(), ROUNDS, violations,
                "overSold=" + overSold + " keyedBy=" + (props.unsafe() ? "orderId" : "symbol"));
    }

    // ------------------------------------------------------------ helpers

    protected boolean tryBuy(AuthenticatedUser who, long offer, BuyOfferRequest req) {
        try {
            offers.buy(who, offer, req);
            return true;
        } catch (OfferConflictException e) {
            return false;
        }
    }

    /** Runs both suppliers at the same instant (barrier) and returns their results in order. */
    @SafeVarargs
    protected final <T> List<T> race(Supplier<T>... tasks) {
        CyclicBarrier go = new CyclicBarrier(tasks.length);
        List<Future<T>> futures = new java.util.ArrayList<>();
        for (Supplier<T> t : tasks) {
            futures.add(pool.submit(() -> {
                go.await(5, TimeUnit.SECONDS);
                return t.get();
            }));
        }
        List<T> out = new java.util.ArrayList<>();
        for (Future<T> f : futures) {
            try {
                out.add(f.get(60, TimeUnit.SECONDS));
            } catch (ExecutionException e) {
                throw new AssertionError("racing task failed", e.getCause());
            } catch (InterruptedException | TimeoutException e) {
                throw new AssertionError("racing task did not finish", e);
            }
        }
        return out;
    }

    protected static void report(Outcome o) {
        log.info("{}", o);
        System.out.println(o);
    }
}
