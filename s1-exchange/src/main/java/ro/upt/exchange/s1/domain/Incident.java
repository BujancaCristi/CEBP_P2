package ro.upt.exchange.s1.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/** Table {@code incidents}: something that went wrong. Read by the nightly report through S2. */
@Entity
@Table(name = "incidents")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IncidentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private IncidentSeverity severity;

    @Column(name = "stock_id")
    private Long stockId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private IncidentStatus status = IncidentStatus.OPEN;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    public Incident(IncidentType type, IncidentSeverity severity, Long stockId, Long orderId,
                    Long userId, String message, Map<String, Object> details) {
        this.type = type;
        this.severity = severity;
        this.stockId = stockId;
        this.orderId = orderId;
        this.userId = userId;
        this.message = message;
        this.details = details;
    }
}
