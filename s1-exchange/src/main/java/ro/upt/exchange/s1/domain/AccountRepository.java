package ro.upt.exchange.s1.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Accounts. The cash movements are written as atomic conditional UPDATEs so that the
 * availability check and the change happen in one statement under the row lock
 * (see Database_Schema.pdf §4 "Atomic conditional updates"). Each returns the number of rows
 * changed: 1 = done, 0 = the condition failed (insufficient cash).
 */
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByUserId(Long userId);

    /** Lock cash for a BUY order. 0 rows = insufficient available cash. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE accounts
               SET reserved_cash = reserved_cash + :amount, version = version + 1
             WHERE id = :accountId
               AND status = 'ACTIVE'
               AND cash_balance - reserved_cash >= :amount
            """, nativeQuery = true)
    int reserveCash(@Param("accountId") long accountId, @Param("amount") BigDecimal amount);

    /** Unlock cash (cancel, or the limit-vs-execution difference after a fill). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE accounts
               SET reserved_cash = reserved_cash - :amount, version = version + 1
             WHERE id = :accountId
               AND reserved_cash >= :amount
            """, nativeQuery = true)
    int releaseCash(@Param("accountId") long accountId, @Param("amount") BigDecimal amount);

    /** Buyer pays: the amount leaves both the balance and the reservation. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE accounts
               SET cash_balance  = cash_balance  - :amount,
                   reserved_cash = reserved_cash - :amount,
                   version = version + 1
             WHERE id = :accountId
               AND reserved_cash >= :amount
            """, nativeQuery = true)
    int debitReserved(@Param("accountId") long accountId, @Param("amount") BigDecimal amount);

    /** Seller is paid. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE accounts
               SET cash_balance = cash_balance + :amount, version = version + 1
             WHERE id = :accountId
            """, nativeQuery = true)
    int credit(@Param("accountId") long accountId, @Param("amount") BigDecimal amount);
}
