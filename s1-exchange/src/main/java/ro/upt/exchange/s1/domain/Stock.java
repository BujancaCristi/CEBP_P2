package ro.upt.exchange.s1.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** Table {@code stocks}: a tradable share of a company. {@code value} is the last traded price. */
@Entity
@Table(name = "stocks")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false, unique = true, length = 10)
    private String symbol;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal value;

    @Column(name = "previous_close", nullable = false, precision = 18, scale = 4)
    private BigDecimal previousClose;

    @Column(name = "shares_outstanding", nullable = false)
    private long sharesOutstanding;

    @Column(name = "tick_size", nullable = false, precision = 10, scale = 4)
    private BigDecimal tickSize = new BigDecimal("0.01");

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    @Column(nullable = false)
    private long version;

    public Stock(Long companyId, String symbol, BigDecimal value, long sharesOutstanding) {
        this.companyId = companyId;
        this.symbol = symbol;
        this.value = value;
        this.previousClose = value;
        this.sharesOutstanding = sharesOutstanding;
    }
}
