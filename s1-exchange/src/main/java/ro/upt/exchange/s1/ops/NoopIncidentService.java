package ro.upt.exchange.s1.ops;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ro.upt.exchange.s1.domain.IncidentSeverity;
import ro.upt.exchange.s1.domain.IncidentType;

import java.util.Map;

/**
 * STUB - owned by Paul, delete when the real {@code IncidentService} lands.
 * Logs the incident and returns 0 so trading/ and platform/ compile and run.
 */
@Service
public class NoopIncidentService implements IncidentService {

    private static final Logger log = LoggerFactory.getLogger(NoopIncidentService.class);

    @Override
    public long raise(IncidentType type, IncidentSeverity severity, Long stockId, Long orderId, Long userId,
                      String message, Map<String, Object> details) {
        log.info("[incident-stub] {} {} stock={} order={} user={} :: {} {}", type, severity, stockId, orderId, userId, message,
                details == null ? "" : details);
        return 0L;
    }
}
