package ro.upt.exchange.s1.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    @Query("""
            SELECT t FROM Trade t
             WHERE t.buyerUserId = :userId OR t.sellerUserId = :userId
             ORDER BY t.executedAt DESC, t.id DESC
            """)
    Page<Trade> findMine(@Param("userId") long userId, Pageable pageable);

    List<Trade> findByStockIdOrderByExecutedAtDesc(Long stockId, Pageable pageable);

    List<Trade> findByBuyOrderIdOrSellOrderId(Long buyOrderId, Long sellOrderId);

    @Query("SELECT COALESCE(SUM(t.qty), 0) FROM Trade t WHERE t.sellOrderId = :sellOrderId")
    long totalSoldFrom(@Param("sellOrderId") long sellOrderId);
}
