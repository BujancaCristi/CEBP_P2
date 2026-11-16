package ro.upt.exchange.s2.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ro.upt.exchange.s2.projector.EventProjector;

/**
 * Consumer group {@code s2-insights} on the five public topics. {@code ack-mode: record} commits the
 * offset only after {@link EventProjector#project} returned, i.e. after its transaction committed.
 * A crash in between replays the record; {@code processed_events} makes the replay a no-op.
 */
@Component
public class ExchangeEventListener {

    private final EventProjector projector;

    public ExchangeEventListener(EventProjector projector) {
        this.projector = projector;
    }

    @KafkaListener(topics = "#{'${insights.topics}'.split(',')}", groupId = "s2-insights")
    public void onEvent(ConsumerRecord<String, String> record) {
        projector.project(record.topic(), record.value());
    }
}
