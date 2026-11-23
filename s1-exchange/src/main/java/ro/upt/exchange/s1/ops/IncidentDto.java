package ro.upt.exchange.s1.ops;

import ro.upt.exchange.s1.domain.Incident;
import ro.upt.exchange.s1.domain.IncidentSeverity;
import ro.upt.exchange.s1.domain.IncidentStatus;
import ro.upt.exchange.s1.domain.IncidentType;

import java.time.Instant;
import java.util.Map;

public record IncidentDto(
        long id,
        IncidentType type,
        IncidentSeverity severity,
        Long stockId,
        String symbol,
        Long orderId,
        Long userId,
        String message,
        Map<String, Object> details,
        IncidentStatus status,
        Instant createdAt,
        Instant resolvedAt
) {
    public static IncidentDto from(Incident incident, String symbol) {
        return new IncidentDto(
                incident.getId(),
                incident.getType(),
                incident.getSeverity(),
                incident.getStockId(),
                symbol,
                incident.getOrderId(),
                incident.getUserId(),
                incident.getMessage(),
                incident.getDetails(),
                incident.getStatus(),
                incident.getCreatedAt(),
                incident.getResolvedAt()
        );
    }
}
