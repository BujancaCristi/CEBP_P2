package ro.upt.exchange.s1.ops;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ro.upt.exchange.s1.config.ExchangeTopics;
import ro.upt.exchange.s1.domain.*;
import ro.upt.exchange.s1.events.EventTypes;
import ro.upt.exchange.s1.events.IncidentEventPayload;
import ro.upt.exchange.s1.platform.AuthenticatedUser;
import ro.upt.exchange.s1.platform.EventOutbox;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for managing Incidents. <b>Owner: Paul.</b>
 * Provides admin endpoints to view and resolve incidents.
 */
@RestController
@RequestMapping("/api/admin/incidents")
public class IncidentController {

    private final IncidentRepository incidentRepository;
    private final StockRepository stockRepository;
    private final EventOutbox outbox;
    private final ExchangeTopics topics;

    public IncidentController(IncidentRepository incidentRepository,
                              StockRepository stockRepository,
                              EventOutbox outbox,
                              ExchangeTopics topics) {
        this.incidentRepository = incidentRepository;
        this.stockRepository = stockRepository;
        this.outbox = outbox;
        this.topics = topics;
    }

    @GetMapping
    public Page<IncidentDto> listIncidents(@RequestParam(name = "status", required = false) IncidentStatus status,
                                           @RequestParam(name = "type", required = false) IncidentType type,
                                           @RequestParam(name = "page", defaultValue = "0") int page,
                                           @RequestParam(name = "size", defaultValue = "50") int size,
                                           AuthenticatedUser user) {
        if (!user.is(Role.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only ADMIN can list incidents");
        }

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200));
        Page<Incident> pageResult;

        if (status != null) {
            pageResult = incidentRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        } else if (type != null) {
            pageResult = incidentRepository.findByTypeOrderByCreatedAtDesc(type, pageable);
        } else {
            pageResult = incidentRepository.findAllByOrderByCreatedAtDesc(pageable);
        }

        Map<Long, String> symbols = stockRepository.findAll().stream()
                .collect(Collectors.toMap(Stock::getId, Stock::getSymbol));

        return pageResult.map(inc -> {
            String symbol = inc.getStockId() != null ? symbols.get(inc.getStockId()) : null;
            return IncidentDto.from(inc, symbol);
        });
    }

    @PatchMapping("/{id}")
    @Transactional
    public IncidentDto updateIncidentStatus(@PathVariable("id") long id,
                                            @Valid @RequestBody UpdateIncidentStatusRequest req,
                                            AuthenticatedUser user) {
        if (!user.is(Role.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only ADMIN can update incidents");
        }

        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found: " + id));

        incident.setStatus(req.status());
        if (req.status() == IncidentStatus.RESOLVED) {
            incident.setResolvedAt(Instant.now());
        }
        incidentRepository.save(incident);

        String symbol = null;
        if (incident.getStockId() != null) {
            symbol = stockRepository.findById(incident.getStockId()).map(Stock::getSymbol).orElse(null);
        }
        String key = symbol != null ? symbol : EventTypes.GLOBAL_KEY;

        Instant createdAt = incident.getCreatedAt() != null ? incident.getCreatedAt() : Instant.now();
        IncidentEventPayload payload = new IncidentEventPayload(
                incident.getId(),
                incident.getType().name(),
                incident.getSeverity().name(),
                symbol,
                incident.getOrderId(),
                incident.getUserId(),
                incident.getMessage(),
                incident.getStatus().name(),
                createdAt
        );

        outbox.add(topics.incidents(), key, EventTypes.INCIDENT_UPDATED, payload);

        return IncidentDto.from(incident, symbol);
    }
}
