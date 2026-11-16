package ro.upt.exchange.s1.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** Positions. Same atomic-conditional-update style as {@link AccountRepository}. */
public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

    Optional<Portfolio> findByAccountIdAndStockId(Long accountId, Long stockId);

    List<Portfolio> findByAccountId(Long accountId);

    /** Lock shares for a SELL order. 0 rows = not enough free shares. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE portfolio
               SET reserved_qty = reserved_qty + :qty, updated_at = now(), version = version + 1
             WHERE account_id = :accountId AND stock_id = :stockId
               AND quantity - reserved_qty >= :qty
            """, nativeQuery = true)
    int reserveShares(@Param("accountId") long accountId, @Param("stockId") long stockId, @Param("qty") long qty);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE portfolio
               SET reserved_qty = reserved_qty - :qty, updated_at = now(), version = version + 1
             WHERE account_id = :accountId AND stock_id = :stockId
               AND reserved_qty >= :qty
            """, nativeQuery = true)
    int releaseShares(@Param("accountId") long accountId, @Param("stockId") long stockId, @Param("qty") long qty);

    /** Seller delivers: shares leave both quantity and the reservation. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE portfolio
               SET quantity = quantity - :qty, reserved_qty = reserved_qty - :qty,
                   updated_at = now(), version = version + 1
             WHERE account_id = :accountId AND stock_id = :stockId
               AND reserved_qty >= :qty
            """, nativeQuery = true)
    int deliverReserved(@Param("accountId") long accountId, @Param("stockId") long stockId, @Param("qty") long qty);

    /** Buyer receives: upsert the position and move the average cost. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO portfolio (account_id, stock_id, quantity, reserved_qty, avg_cost, updated_at, version)
            VALUES (:accountId, :stockId, :qty, 0, :price, now(), 0)
            ON CONFLICT (account_id, stock_id) DO UPDATE
               SET avg_cost   = CASE WHEN portfolio.quantity + EXCLUDED.quantity = 0 THEN 0
                                     ELSE (portfolio.avg_cost * portfolio.quantity + EXCLUDED.avg_cost * EXCLUDED.quantity)
                                          / (portfolio.quantity + EXCLUDED.quantity) END,
                   quantity   = portfolio.quantity + EXCLUDED.quantity,
                   updated_at = now(),
                   version    = portfolio.version + 1
            """, nativeQuery = true)
    int receive(@Param("accountId") long accountId, @Param("stockId") long stockId,
                @Param("qty") long qty, @Param("price") BigDecimal price);
}
