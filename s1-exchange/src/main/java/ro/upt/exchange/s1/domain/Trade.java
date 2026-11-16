package ro.upt.exchange.s1.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/** Table {@code trades}: an executed match. Never updated after insert. */
@Entity
@Table(name = "trades")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_id", nullable = false)
    private Long stockId;

    @Column(name = "buy_order_id", nullable = false)
    private Long buyOrderId;

    @Column(name = "sell_order_id", nullable = false)
    private Long sellOrderId;

    @Column(name = "buyer_user_id", nullable = false)
    private Long buyerUserId;

    @Column(name = "seller_user_id", nullable = false)
    private Long sellerUserId;

    @Column(nullable = false)
    private long qty;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal price;

    @Column(name = "total_value", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalValue;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt = Instant.now();

    public Trade(Order buy, Order sell, long qty, BigDecimal price) {
        if (buy.getSide() != OrderSide.BUY || sell.getSide() != OrderSide.SELL) {
            throw new IllegalArgumentException("Trade needs one BUY and one SELL order");
        }
        this.stockId = buy.getStockId();
        this.buyOrderId = buy.getId();
        this.sellOrderId = sell.getId();
        this.buyerUserId = buy.getUserId();
        this.sellerUserId = sell.getUserId();
        this.qty = qty;
        this.price = price;
        this.totalValue = totalOf(qty, price);
    }

    public static BigDecimal totalOf(long qty, BigDecimal price) {
        return price.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
    }
}
