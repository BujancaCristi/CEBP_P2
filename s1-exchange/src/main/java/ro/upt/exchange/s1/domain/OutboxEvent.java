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
import java.util.UUID;

/**
 * Table {@code event_outbox}: transactional outbox. Inserted in the same transaction as the
 * business change; Cezar's relay publishes it to Kafka and sets {@code published_at}.
 *
 * <p>{@code id} is the event's {@code eventId} in the envelope, so S2 can de-duplicate.
 * {@code payload} is the full envelope ({eventId, type, occurredAt, schemaVersion, payload}).
 */
@Entity
@Table(name = "event_outbox")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(nullable = false, length = 60)
    private String topic;

    @Column(name = "msg_key", nullable = false, length = 60)
    private String msgKey;

    @Column(nullable = false, length = 40)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private int attempts;

    public OutboxEvent(UUID id, String topic, String msgKey, String type, Map<String, Object> payload) {
        this.id = id;
        this.topic = topic;
        this.msgKey = msgKey;
        this.type = type;
        this.payload = payload;
    }
}
