package ro.upt.exchange.s1.platform;

import org.springframework.kafka.core.KafkaTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.upt.exchange.s1.domain.OutboxEvent;
import ro.upt.exchange.s1.domain.OutboxEventRepository;

import java.time.Instant;

@Service
public class OutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private final OutboxEventRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;

    public OutboxRelay(OutboxEventRepository outbox, KafkaTemplate<String, String> kafka,
                       com.fasterxml.jackson.databind.ObjectMapper mapper) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.mapper = mapper;
    }

    @Scheduled(fixedDelayString = "${exchange.platform.outbox-interval-ms:200}")
    @Transactional
    public void publishBatch() {
        for (OutboxEvent event : outbox.lockUnpublishedBatch(100)) {
            try {
                kafka.send(event.getTopic(), event.getMsgKey(), mapper.writeValueAsString(event.getPayload())).get();
                outbox.markPublished(event.getId(), Instant.now());
            } catch (Exception ex) {
                outbox.markAttempt(event.getId());
                log.warn("Could not publish outbox event {} to {}: {}", event.getId(), event.getTopic(), ex.getMessage());
            }
        }
    }
}
