package ro.upt.exchange.s1.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Table {@code orders}. Open SELL orders are the <em>offers</em> shown to buyers.
 * {@code version} is the optimistic lock used by concurrency case 2 (the client sends the
 * version it saw; the buy UPDATE filters on it).
 *
 * <p>Quantity changes made by the matching engine and by buy-from-offer go through the
 * conditional UPDATE statements in {@link OrderRepository}, never through dirty checking,
 * because the WHERE clause is what makes them safe across processes.
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "stock_id", nullable = false)
    private Long stockId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private OrderSide side;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private OrderType type;

    /** NULL for MARKET orders (CHECK limit_needs_price). */
    @Column(precision = 18, scale = 4)
    private BigDecimal price;

    @Column(nullable = false)
    private long qty;

    @Column(name = "remaining_qty", nullable = false)
    private long remainingQty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrderStatus status;

    @Column(name = "reject_reason", length = 255)
    private String rejectReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private OrderSource source = OrderSource.WEB;

    @Column(name = "created_time", nullable = false, insertable = false, updatable = false)
    private Instant createdTime;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    @Column(nullable = false)
    private long version;

    public Order(Long userId, Long accountId, Long stockId, OrderSide side, OrderType type,
                 BigDecimal price, long qty, OrderSource source) {
        this.userId = userId;
        this.accountId = accountId;
        this.stockId = stockId;
        this.side = side;
        this.type = type;
        this.price = price;
        this.qty = qty;
        this.remainingQty = qty;
        this.status = OrderStatus.PENDING;
        this.source = source;
    }

    public long filledQty() {
        return qty - remainingQty;
    }

    /** price × remaining_qty, the "totalPrice" shown on an offer. */
    public BigDecimal totalPrice() {
        return price == null ? null : price.multiply(BigDecimal.valueOf(remainingQty));
    }

    /** Status that remaining_qty implies for a live order. */
    public static OrderStatus statusFor(long qty, long remaining) {
        if (remaining == 0) return OrderStatus.FILLED;
        if (remaining == qty) return OrderStatus.OPEN;
        return OrderStatus.PARTIALLY_FILLED;
    }
}
