package ro.upt.exchange.s1.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Table {@code portfolio}: one row per position (UNIQUE account_id, stock_id).
 * DB CHECKs: quantity >= 0, reserved_qty >= 0, reserved_qty <= quantity.
 */
@Entity
@Table(name = "portfolio")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "stock_id", nullable = false)
    private Long stockId;

    @Column(nullable = false)
    private long quantity;

    @Column(name = "reserved_qty", nullable = false)
    private long reservedQty;

    @Column(name = "avg_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal avgCost = BigDecimal.ZERO;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    @Column(nullable = false)
    private long version;

    public Portfolio(Long accountId, Long stockId) {
        this.accountId = accountId;
        this.stockId = stockId;
    }

    /** Shares not locked by open SELL orders. */
    public long availableQty() {
        return quantity - reservedQty;
    }
}
