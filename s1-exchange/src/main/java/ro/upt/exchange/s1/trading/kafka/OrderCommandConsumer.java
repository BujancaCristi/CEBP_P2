package ro.upt.exchange.s1.trading.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ro.upt.exchange.s1.domain.IncidentSeverity;
import ro.upt.exchange.s1.events.EventEnvelope;
import ro.upt.exchange.s1.events.OrderCommandPayload;
import ro.upt.exchange.s1.ops.IncidentService;
import ro.upt.exchange.s1.trading.error.CommandRejectedException;
import ro.upt.exchange.s1.trading.service.MatchingEngine;

import java.util.Map;

/**
 * Consumer of {@code exchange.order-commands}. {@code concurrency} = partitions (3), so each
 * partition - and with symbol keys, each stock - has exactly one matcher thread.
 *
 * <p>Offsets are committed per record, after {@link MatchingEngine#handle} returns, i.e. after its
 * transaction committed. A business rejection is recorded as an incident and acknowledged; an
 * infrastructure error is retried by {@link MatchingKafkaConfig}'s error handler.
 */
@Component
public class OrderCommandConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderCommandConsumer.class);
    private static final TypeReference<EventEnvelope<OrderCommandPayload>> COMMAND = new TypeReference<>() {};

    private final MatchingEngine engine;
    private final ObjectMapper mapper;
    private final IncidentService incidents;

    public OrderCommandConsumer(MatchingEngine engine, ObjectMapper mapper, IncidentService incidents) {
        this.engine = engine;
        this.mapper = mapper;
        this.incidents = incidents;
    }

    @KafkaListener(
            id = "order-commands",
            topics = "${exchange.topics.order-commands}",
            groupId = "${exchange.matching.consumer-group}",
            concurrency = "${exchange.matching.concurrency}")
    public void onCommand(ConsumerRecord<String, String> record) {
        EventEnvelope<OrderCommandPayload> command;
        try {
            command = mapper.readValue(record.value(), COMMAND);
        } catch (Exception e) {
            log.error("Skipping unreadable order command at {}-{}@{}: {}", record.topic(), record.partition(), record.offset(), e.toString());
            return;
        }
        try {
            engine.handle(command);
        } catch (CommandRejectedException e) {
            log.info("Command {} for order {} rejected: {}", command.type(), e.getOrderId(), e.getMessage());
            incidents.raise(e.getIncidentType(), IncidentSeverity.LOW, e.getStockId(), e.getOrderId(), e.getUserId(),
                    e.getMessage(), Map.of("command", command.type(), "eventId", command.eventId().toString()));
        }
    }
}
