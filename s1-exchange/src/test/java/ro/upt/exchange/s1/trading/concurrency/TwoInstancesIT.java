package ro.upt.exchange.s1.trading.concurrency;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import ro.upt.exchange.s1.S1Application;
import ro.upt.exchange.s1.domain.Stock;
import ro.upt.exchange.s1.platform.AuthenticatedUser;
import ro.upt.exchange.s1.testsupport.AbstractIntegrationTest;
import ro.upt.exchange.s1.testsupport.Fixtures;
import ro.upt.exchange.s1.trading.dto.BuyOfferRequest;
import ro.upt.exchange.s1.trading.error.OfferConflictException;
import ro.upt.exchange.s1.trading.service.OfferService;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two running copies of S1 on the same database (concurrency case 1, two buyers):
 *
 * <ol>
 *   <li>SAFE: buyer A on instance 1, buyer B on instance 2 - the conditional UPDATE is enforced
 *       by PostgreSQL's row lock, which does not care how many JVMs there are: 0 violations.</li>
 *   <li>UNSAFE + an in-memory {@link ReentrantLock} around the buy: inside one instance the lock
 *       serialises the two buyers and hides the bug (0 violations); with two instances each has
 *       its own lock object, the race is back and both buyers win. This is why an in-JVM lock
 *       is not a concurrency mechanism for a service that may run more than once.</li>
 * </ol>
 *
 * This class's own context is SAFE; the extra instances are booted here on the same containers
 * with their own Kafka group/topic so they never match another context's commands.
 */
@Import(Fixtures.class)
class TwoInstancesIT extends AbstractIntegrationTest {

    private static final int ROUNDS = Integer.getInteger("concurrency.rounds2", 50);
    private static final BigDecimal CASH = new BigDecimal("100000.00");
    private static final BigDecimal TEN = new BigDecimal("10.0000");

    private static ConfigurableApplicationContext safeB;
    private static ConfigurableApplicationContext unsafeA;
    private static ConfigurableApplicationContext unsafeB;

    @Autowired Fixtures fx;
    @Autowired OfferService safeA;

    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    @BeforeAll
    static void bootOtherInstances() {
        safeB = boot("SAFE", "2inst-safe-b");
        unsafeA = boot("UNSAFE", "2inst-unsafe-a");
        unsafeB = boot("UNSAFE", "2inst-unsafe-b");
    }

    @AfterAll
    static void stopOtherInstances() {
        for (var ctx : List.of(unsafeB, unsafeA, safeB)) {
            if (ctx != null) ctx.close();
        }
    }

    private static ConfigurableApplicationContext boot(String mode, String tag) {
        // command-line args: highest precedence, so application.yml's localhost defaults never win
        return new SpringApplicationBuilder(S1Application.class)
                .profiles("test")
                .run(
                        "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "--spring.datasource.username=" + POSTGRES.getUsername(),
                        "--spring.datasource.password=" + POSTGRES.getPassword(),
                        "--spring.kafka.bootstrap-servers=" + KAFKA.getBootstrapServers(),
                        "--server.port=0",
                        "--exchange.concurrency-mode=" + mode,
                        "--exchange.topics.order-commands=exchange.order-commands-" + tag,
                        "--exchange.matching.consumer-group=s1-matching-" + tag,
                        "--spring.jmx.enabled=false");
    }

    @Test
    void safe_twoInstances_rowLockWorksAcrossProcesses() {
        OfferService b = safeB.getBean(OfferService.class);
        int violations = run(safeA, b, null, null);
        System.out.println("CONCURRENCY-RESULT case1-two-instances/SAFE rounds=" + ROUNDS + " violations=" + violations);
        assertThat(violations).isZero();
    }

    @Test
    void unsafe_inMemoryLock_hidesTheBugOnlyWithinOneInstance() {
        OfferService a = unsafeA.getBean(OfferService.class);
        OfferService b = unsafeB.getBean(OfferService.class);
        ReentrantLock lockOfA = new ReentrantLock();
        ReentrantLock lockOfB = new ReentrantLock();

        int oneInstance = run(a, a, lockOfA, lockOfA);
        System.out.println("CONCURRENCY-RESULT case1-one-instance-with-lock/UNSAFE rounds=" + ROUNDS + " violations=" + oneInstance);
        int twoInstances = run(a, b, lockOfA, lockOfB);
        System.out.println("CONCURRENCY-RESULT case1-two-instances-with-local-locks/UNSAFE rounds=" + ROUNDS + " violations=" + twoInstances);

        assertThat(oneInstance).as("a JVM lock serialises the two buyers inside one instance").isZero();
        assertThat(twoInstances).as("two instances, two lock objects: the race is back").isGreaterThanOrEqualTo(ROUNDS / 10);
    }

    // ------------------------------------------------------------------ helpers

    private int run(OfferService first, OfferService second, ReentrantLock lock1, ReentrantLock lock2) {
        int violations = 0;
        for (int i = 0; i < ROUNDS; i++) {
            Stock stock = fx.stock(TEN);
            AuthenticatedUser seller = fx.trader("2is", CASH);
            fx.position(seller, stock, 1000, 1000);
            long offer = fx.offer(seller, stock, TEN, 100);
            long v = fx.version(offer);
            AuthenticatedUser a = fx.trader("2ia", CASH);
            AuthenticatedUser b = fx.trader("2ib", CASH);

            List<Boolean> won = race(
                    () -> locked(lock1, () -> tryBuy(first, a, offer, v)),
                    () -> locked(lock2, () -> tryBuy(second, b, offer, v)));
            if ((won.get(0) && won.get(1)) || fx.soldFrom(offer) != 100) violations++;
        }
        return violations;
    }

    private static boolean tryBuy(OfferService svc, AuthenticatedUser who, long offer, long v) {
        try {
            svc.buy(who, offer, new BuyOfferRequest(100, TEN, v));
            return true;
        } catch (OfferConflictException e) {
            return false;
        }
    }

    private static <T> T locked(ReentrantLock lock, Supplier<T> s) {
        if (lock == null) return s.get();
        lock.lock();
        try {
            return s.get();
        } finally {
            lock.unlock();
        }
    }

    @SafeVarargs
    private <T> List<T> race(Supplier<T>... tasks) {
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
}
