package ro.upt.exchange.s1.ops;

import ro.upt.exchange.s1.domain.IncidentSeverity;
import ro.upt.exchange.s1.domain.IncidentType;

import java.util.Map;

/**
 * Incidents. <b>Owner: Paul.</b> Signature fixed in Phase 0.
 *
 * <p>Called by trading/ (Patric) for INSUFFICIENT_FUNDS, INSUFFICIENT_SHARES, ORDER_REJECTED and
 * CONCURRENCY_CONFLICT, and by platform/ (Cezar). The implementation inserts the {@code incidents}
 * row and adds an {@code IncidentRaised} event to the outbox. It should run in its own
 * transaction ({@code REQUIRES_NEW}) so that an incident raised while a business transaction is
 * about to roll back (e.g. a 409 on buy-from-offer) is still recorded.
 *
 * @param stockId nullable
 * @param orderId nullable
 * @param userId  nullable
 * @param details nullable, stored as JSONB
 * @return the new incident id
 */
public interface IncidentService {

    long raise(IncidentType type, IncidentSeverity severity, Long stockId, Long orderId, Long userId,
               String message, Map<String, Object> details);
}
