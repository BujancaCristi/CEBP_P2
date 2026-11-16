package ro.upt.exchange.s1.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /** For Cezar's relay: unpublished rows, oldest first, skipping rows another relay holds. */
    @Query(value = """
            SELECT * FROM event_outbox
             WHERE published_at IS NULL
             ORDER BY created_at
             LIMIT :batch
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockUnpublishedBatch(@Param("batch") int batch);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE event_outbox SET published_at = :at, attempts = attempts + 1 WHERE id = :id", nativeQuery = true)
    int markPublished(@Param("id") UUID id, @Param("at") Instant at);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE event_outbox SET attempts = attempts + 1 WHERE id = :id", nativeQuery = true)
    int markAttempt(@Param("id") UUID id);

    List<OutboxEvent> findByTopicOrderByCreatedAt(String topic);

    long countByPublishedAtIsNull();
}
