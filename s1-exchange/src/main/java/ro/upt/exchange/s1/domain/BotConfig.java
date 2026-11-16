package ro.upt.exchange.s1.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** Table {@code bot_configs}: the market-maker's settings, one row per stock. Changed only by an admin. */
@Entity
@Table(name = "bot_configs")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BotConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bot_user_id", nullable = false)
    private Long botUserId;

    @Column(name = "stock_id", nullable = false)
    private Long stockId;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "spread_bps", nullable = false)
    private int spreadBps;

    @Column(name = "quote_qty", nullable = false)
    private long quoteQty;

    @Column(name = "max_inventory", nullable = false)
    private long maxInventory;

    @Column(name = "skew_factor", nullable = false, precision = 6, scale = 3)
    private BigDecimal skewFactor = new BigDecimal("0.500");

    @Column(name = "refresh_ms", nullable = false)
    private int refreshMs = 2000;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by", nullable = false, length = 64)
    private String updatedBy = "seed";

    @Version
    @Column(nullable = false)
    private long version;

    public BotConfig(Long botUserId, Long stockId, int spreadBps, long quoteQty, long maxInventory) {
        this.botUserId = botUserId;
        this.stockId = stockId;
        this.spreadBps = spreadBps;
        this.quoteQty = quoteQty;
        this.maxInventory = maxInventory;
    }
}
