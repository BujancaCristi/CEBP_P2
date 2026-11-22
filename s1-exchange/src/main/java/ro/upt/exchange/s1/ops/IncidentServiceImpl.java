package ro.upt.exchange.s1.ops;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ro.upt.exchange.s1.config.ExchangeTopics;
import ro.upt.exchange.s1.domain.Incident;
import ro.upt.exchange.s1.domain.IncidentRepository;
import ro.upt.exchange.s1.domain.IncidentSeverity;
import ro.upt.exchange.s1.domain.IncidentStatus;
import ro.upt.exchange.s1.domain.IncidentType;
import ro.upt.exchange.s1.domain.Stock;
import ro.upt.exchange.s1.domain.StockRepository;
import ro.upt.exchange.s1.events.EventTypes;
import ro.upt.exchange.s1.events.IncidentEventPayload;
import ro.upt.exchange.s1.platform.EventOutbox;

import java.time.Instant;
import java.util.Map;

/**
 * Incidents (owner: Paul). Each incident is stored and published in its own transaction
 * ({@code REQUIRES_NEW}), so an incident raised while the caller's business transaction rolls back
 * (a 409 on buy-from-offer, a rejected order) is still recorded and still reaches S2.
 */
@Service
public class IncidentServiceImpl implements IncidentService {

    private static final Logger log = LoggerFactory.getLogger(IncidentServiceImpl.class);

    private final IncidentRepository incidents;
    private final StockRepository stocks;
    private final EventOutbox outbox;
    private final ExchangeTopics topics;

    public IncidentServiceImpl(IncidentRepository incidents, StockRepository stocks, EventOutbox outbox,
                               ExchangeTopics topics) {
        this.incidents = incidents;
        this.stocks = stocks;
        this.outbox = outbox;
        this.topics = topics;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long raise(IncidentType type, IncidentSeverity severity, Long stockId, Long orderId, Long userId,
                      String message, Map<String, Object> details) {
        Incident incident = incidents.saveAndFlush(
                new Incident(type, severity, stockId, orderId, userId, message, details));
        publish(incident, EventTypes.INCIDENT_RAISED);
        log.info("Incident {} {} {}: {}", incident.getId(), type, severity, message);
        return incident.getId();
    }

    /** Admin status change: OPEN to ACKNOWLEDGED or RESOLVED. Publishes IncidentUpdated. */
    @Transactional
    public Incident changeStatus(long incidentId, IncidentStatus status) {
        Incident incident = incidents.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("Incident " + incidentId + " not found"));
        incident.setStatus(status);
        incident.setResolvedAt(status == IncidentStatus.RESOLVED ? Instant.now() : null);
        publish(incident, EventTypes.INCIDENT_UPDATED);
        return incident;
    }

    public IncidentEventPayload toPayload(Incident i) {
        String symbol = symbolOf(i.getStockId());
        return new IncidentEventPayload(i.getId(), i.getType().name(), i.getSeverity().name(), symbol,
                i.getOrderId(), i.getUserId(), i.getMessage(), i.getStatus().name(),
                i.getCreatedAt() != null ? i.getCreatedAt() : Instant.now());
    }

    private void publish(Incident incident, String eventType) {
        IncidentEventPayload payload = toPayload(incident);
        String key = payload.symbol() != null ? payload.symbol() : EventTypes.GLOBAL_KEY;
        outbox.add(topics.incidents(), key, eventType, payload);
    }

    private String symbolOf(Long stockId) {
        return stockId == null ? null : stocks.findById(stockId).map(Stock::getSymbol).orElse(null);
    }
}
