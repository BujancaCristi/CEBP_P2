package ro.upt.exchange.s1.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {

    Optional<Stock> findBySymbol(String symbol);

    Optional<Stock> findBySymbolIgnoreCase(String symbol);

    List<Stock> findByActiveTrueOrderBySymbol();

    /** Last traded price, written by settlement. Plain update: the matcher is the single writer per stock. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE stocks
               SET value = :value, updated_at = now(), version = version + 1
             WHERE id = :stockId
            """, nativeQuery = true)
    int updateValue(@Param("stockId") long stockId, @Param("value") BigDecimal value);
}
