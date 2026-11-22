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
import ro.upt.exchange.s1.domain.IncidentType;
import ro.upt.exchange.s1.domain.Stock;
import ro.upt.exchange.s1.domain.StockRepository;
import ro.upt.exchange.s1.events.EventTypes;
import ro.upt.exchange.s1.events.IncidentEventPayload;
import ro.upt.exchange.s1.platform.EventOutbox;

import java.time.Instant;
import java.util.Map;

/**
 * Real IncidentService implementation. <b>Owner: Paul.</b>
 * Saves the incident in a dedicated transaction (REQUIRES_NEW) and writes IncidentRaised to the outbox.
 */
@Service
public class OpsIncidentService implements IncidentService {

    private static final Logger log = LoggerFactory.getLogger(OpsIncidentService.class);

    private final IncidentRepository incidentRepository;
    private final StockRepository stockRepository;
    private final EventOutbox outbox;
    private final ExchangeTopics topics;

    public OpsIncidentService(IncidentRepository incidentRepository,
                              StockRepository stockRepository,
                              EventOutbox outbox,
                              ExchangeTopics topics) {
        this.incidentRepository = incidentRepository;
        this.stockRepository = stockRepository;
        this.outbox = outbox;
        this.topics = topics;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long raise(IncidentType type, IncidentSeverity severity, Long stockId, Long orderId, Long userId,
                      String message, Map<String, Object> details) {
        Incident incident = new Incident(type, severity, stockId, orderId, userId, message, details);
        incident = incidentRepository.saveAndFlush(incident);

        String symbol = null;
        if (stockId != null) {
            symbol = stockRepository.findById(stockId).map(Stock::getSymbol).orElse(null);
        }
        String key = symbol != null ? symbol : EventTypes.GLOBAL_KEY;

        Instant createdAt = incident.getCreatedAt() != null ? incident.getCreatedAt() : Instant.now();
        IncidentEventPayload payload = new IncidentEventPayload(
                incident.getId(),
                type.name(),
                severity.name(),
                symbol,
                orderId,
                userId,
                message,
                incident.getStatus().name(),
                createdAt
        );

        outbox.add(topics.incidents(), key, EventTypes.INCIDENT_RAISED, payload);
        log.info("Raised incident #{}: {} {} stock={} (symbol={})", incident.getId(), type, severity, stockId, symbol);
        return incident.getId();
    }
}
