package ro.upt.exchange.s1.events;

import java.time.Instant;

/** Payload of {@code exchange.incidents}: IncidentRaised, IncidentUpdated. Produced by ops/ (Paul). */
public record IncidentEventPayload(
        long incidentId,
        String type,
        String severity,
        String symbol,
        Long orderId,
        Long userId,
        String message,
        String status,
        Instant createdAt) {
}
