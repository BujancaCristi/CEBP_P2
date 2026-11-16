package ro.upt.exchange.s1.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Table {@code accounts}: the money side of a user (1:1 through {@code user_id UNIQUE}).
 * DB CHECKs: cash_balance >= 0, reserved_cash >= 0, reserved_cash <= cash_balance.
 */
@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "cash_balance", nullable = false, precision = 18, scale = 2)
    private BigDecimal cashBalance = BigDecimal.ZERO;

    @Column(name = "reserved_cash", nullable = false, precision = 18, scale = 2)
    private BigDecimal reservedCash = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency = "EUR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(nullable = false)
    private long version;

    public Account(Long userId, BigDecimal cashBalance) {
        this.userId = userId;
        this.cashBalance = cashBalance;
    }

    /** Cash not locked by open BUY orders. */
    public BigDecimal availableCash() {
        return cashBalance.subtract(reservedCash);
    }
}
