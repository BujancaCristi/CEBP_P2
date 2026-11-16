package ro.upt.exchange.s1.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ro.upt.exchange.s1.domain.OutboxEvent;
import ro.upt.exchange.s1.domain.OutboxEventRepository;
import ro.upt.exchange.s1.events.EventEnvelope;

import java.util.Map;

/**
 * STUB - owned by Cezar (replace or keep; it is a complete "insert" side). Patric ships it so
 * trading can write events from day 2. The Kafka relay is NOT here: that is Cezar's
 * {@code OutboxRelay} (SELECT … FOR UPDATE SKIP LOCKED every 200 ms, see
 * {@code OutboxEventRepository#lockUnpublishedBatch}).
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class JpaEventOutbox implements EventOutbox {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public JpaEventOutbox(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void add(String topic, String key, String type, Object payload) {
        EventEnvelope<Object> envelope = EventEnvelope.of(type, payload);
        Map<String, Object> json = objectMapper.convertValue(envelope, MAP);
        repository.save(new OutboxEvent(envelope.eventId(), topic, key, type, json));
    }
}
