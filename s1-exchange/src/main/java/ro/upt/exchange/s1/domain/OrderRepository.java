package ro.upt.exchange.s1.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Orders. The three concurrency cases live in the UPDATE statements below:
 *
 * <ul>
 *   <li><b>Case 1</b> (two buyers) - {@link #takeOffer}: {@code AND remaining_qty >= :qty}. PostgreSQL
 *       re-checks the WHERE after waiting for the other transaction's row lock, so the loser
 *       updates 0 rows.</li>
 *   <li><b>Case 2</b> (price changed) - {@link #takeOffer}: {@code AND version = :seenVersion}.
 *       {@link #reprice} bumps the version, so a buy with a stale version updates 0 rows.</li>
 *   <li><b>Case 3</b> (demand > supply) - {@link #fill} is written as an atomic decrement; ordering
 *       comes from the one-consumer-per-symbol Kafka partition.</li>
 * </ul>
 *
 * The {@code *Unsafe} variants are the deliberately broken versions selected by
 * {@code exchange.concurrency-mode=UNSAFE} (read in Java, then write the computed value).
 */
public interface OrderRepository extends JpaRepository<Order, Long> {

    // ------------------------------------------------------------------ reads

    Optional<Order> findByIdAndUserId(Long id, Long userId);

    Page<Order> findByUserIdOrderByCreatedTimeDesc(Long userId, Pageable pageable);

    /** Offers: open SELL orders of a stock, best price first, oldest first. */
    @Query("""
            SELECT o FROM Order o
             WHERE o.stockId = :stockId AND o.side = ro.upt.exchange.s1.domain.OrderSide.SELL
               AND o.status IN (ro.upt.exchange.s1.domain.OrderStatus.OPEN,
                                ro.upt.exchange.s1.domain.OrderStatus.PARTIALLY_FILLED)
             ORDER BY o.price ASC, o.createdTime ASC, o.id ASC
            """)
    List<Order> findOffers(@Param("stockId") long stockId);

    /** Resting SELL side for matching an incoming BUY: lowest ask first (self-trades excluded). */
    @Query("""
            SELECT o FROM Order o
             WHERE o.stockId = :stockId AND o.side = ro.upt.exchange.s1.domain.OrderSide.SELL
               AND o.status IN (ro.upt.exchange.s1.domain.OrderStatus.OPEN,
                                ro.upt.exchange.s1.domain.OrderStatus.PARTIALLY_FILLED)
               AND o.userId <> :excludeUserId
             ORDER BY o.price ASC, o.createdTime ASC, o.id ASC
            """)
    List<Order> findRestingSells(@Param("stockId") long stockId, @Param("excludeUserId") long excludeUserId, Pageable page);

    /** Resting BUY side for matching an incoming SELL: highest bid first (self-trades excluded). */
    @Query("""
            SELECT o FROM Order o
             WHERE o.stockId = :stockId AND o.side = ro.upt.exchange.s1.domain.OrderSide.BUY
               AND o.status IN (ro.upt.exchange.s1.domain.OrderStatus.OPEN,
                                ro.upt.exchange.s1.domain.OrderStatus.PARTIALLY_FILLED)
               AND o.userId <> :excludeUserId
             ORDER BY o.price DESC, o.createdTime ASC, o.id ASC
            """)
    List<Order> findRestingBuys(@Param("stockId") long stockId, @Param("excludeUserId") long excludeUserId, Pageable page);

    /** Orders stuck in PENDING (command never reached the matcher); re-published by the sweeper. */
    List<Order> findByStatusAndCreatedTimeBefore(OrderStatus status, java.time.Instant before);

    /** Order book grouped by price level. */
    interface BookLevel {
        BigDecimal getPrice();
        long getQty();
        long getOrders();
    }

    @Query(value = """
            SELECT price AS price, SUM(remaining_qty) AS qty, COUNT(*) AS orders
              FROM orders
             WHERE stock_id = :stockId AND side = :side
               AND status IN ('OPEN', 'PARTIALLY_FILLED')
             GROUP BY price
             ORDER BY CASE WHEN :side = 'BUY' THEN -price ELSE price END
             LIMIT :depth
            """, nativeQuery = true)
    List<BookLevel> bookSide(@Param("stockId") long stockId, @Param("side") String side, @Param("depth") int depth);

    @Query(value = """
            SELECT MAX(price) FROM orders
             WHERE stock_id = :stockId AND side = 'BUY' AND status IN ('OPEN', 'PARTIALLY_FILLED')
            """, nativeQuery = true)
    BigDecimal bestBid(@Param("stockId") long stockId);

    @Query(value = """
            SELECT MIN(price) FROM orders
             WHERE stock_id = :stockId AND side = 'SELL' AND status IN ('OPEN', 'PARTIALLY_FILLED')
            """, nativeQuery = true)
    BigDecimal bestAsk(@Param("stockId") long stockId);

    // ------------------------------------------------- buy-from-offer (cases 1 + 2)

    /**
     * SAFE. One conditional UPDATE: still open, enough shares (case 1), version unchanged (case 2),
     * price not above what the buyer accepts. Returns 1 if the buyer got the shares, else 0.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE orders
               SET remaining_qty = remaining_qty - :qty,
                   status  = CASE WHEN remaining_qty - :qty = 0 THEN 'FILLED' ELSE 'PARTIALLY_FILLED' END,
                   version = version + 1,
                   updated_at = now()
             WHERE id = :offerId
               AND side = 'SELL'
               AND status IN ('OPEN', 'PARTIALLY_FILLED')
               AND remaining_qty >= :qty
               AND version = :seenVersion
               AND price <= :maxPrice
            """, nativeQuery = true)
    int takeOffer(@Param("offerId") long offerId, @Param("qty") long qty,
                  @Param("seenVersion") long seenVersion, @Param("maxPrice") BigDecimal maxPrice);

    /**
     * UNSAFE case 1 + 2: blind write of a value computed in Java, no quantity check, no version
     * check, no price check. Two buyers who both read 100 both write 0 (lost update).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE orders
               SET remaining_qty = :newRemaining,
                   status  = CASE WHEN :newRemaining = 0 THEN 'FILLED' ELSE 'PARTIALLY_FILLED' END,
                   version = version + 1,
                   updated_at = now()
             WHERE id = :offerId
            """, nativeQuery = true)
    int takeOfferUnsafe(@Param("offerId") long offerId, @Param("newRemaining") long newRemaining);

    // ---------------------------------------------------- matching (case 3)

    /** SAFE fill: atomic decrement, 0 rows means the resting order changed under us. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE orders
               SET remaining_qty = remaining_qty - :qty,
                   status  = CASE WHEN remaining_qty - :qty = 0 THEN 'FILLED' ELSE 'PARTIALLY_FILLED' END,
                   version = version + 1,
                   updated_at = now()
             WHERE id = :orderId
               AND status IN ('PENDING', 'OPEN', 'PARTIALLY_FILLED')
               AND remaining_qty >= :qty
            """, nativeQuery = true)
    int fill(@Param("orderId") long orderId, @Param("qty") long qty);

    /** UNSAFE case 3: read-check-write of a stale remaining_qty. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE orders
               SET remaining_qty = :newRemaining,
                   status  = CASE WHEN :newRemaining = 0 THEN 'FILLED'
                                  WHEN :newRemaining = qty THEN 'OPEN'
                                  ELSE 'PARTIALLY_FILLED' END,
                   version = version + 1,
                   updated_at = now()
             WHERE id = :orderId
            """, nativeQuery = true)
    int fillUnsafe(@Param("orderId") long orderId, @Param("newRemaining") long newRemaining);

    // ---------------------------------------------------- status transitions

    /** PENDING → OPEN when the matcher leaves a remainder in the book. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE orders
               SET status = CASE WHEN remaining_qty = qty THEN 'OPEN' ELSE 'PARTIALLY_FILLED' END,
                   version = version + 1, updated_at = now()
             WHERE id = :orderId AND status = 'PENDING'
            """, nativeQuery = true)
    int open(@Param("orderId") long orderId);

    /**
     * Cancel, compare-and-set on {@code version}: the matcher read the order (to know how much
     * reservation to release) and only cancels if nobody (e.g. buy-from-offer) changed it since.
     * 0 rows = re-read and retry.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE orders
               SET status = 'CANCELLED', reject_reason = :reason, version = version + 1, updated_at = now()
             WHERE id = :orderId
               AND status IN ('PENDING', 'OPEN', 'PARTIALLY_FILLED')
               AND version = :expectedVersion
            """, nativeQuery = true)
    int cancel(@Param("orderId") long orderId, @Param("reason") String reason, @Param("expectedVersion") long expectedVersion);

    /**
     * Modify price and/or quantity of a live order (compare-and-set on {@code version}). The bump
     * of {@code version} is what makes a concurrent buy-from-offer with the old version fail
     * (case 2). The new quantity keeps the filled part: remaining = newQty − filled, and must stay > 0.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE orders
               SET price = :price,
                   remaining_qty = :newQty - (qty - remaining_qty),
                   qty = :newQty,
                   version = version + 1,
                   updated_at = now()
             WHERE id = :orderId
               AND status IN ('OPEN', 'PARTIALLY_FILLED')
               AND version = :expectedVersion
               AND :newQty - (qty - remaining_qty) > 0
            """, nativeQuery = true)
    int reprice(@Param("orderId") long orderId, @Param("price") BigDecimal price, @Param("newQty") long newQty,
                @Param("expectedVersion") long expectedVersion);
}
