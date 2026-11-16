package ro.upt.exchange.s1.trading.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Retry policy for the matcher: an infrastructure failure (DB down, deadlock) is retried 3 times
 * with a 1 s pause, then the record is logged and skipped so one bad command cannot block a
 * whole stock's partition. The pending-order sweeper re-publishes anything left PENDING.
 */
@Configuration
public class MatchingKafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(MatchingKafkaConfig.class);

    @Bean
    public CommonErrorHandler matchingErrorHandler() {
        DefaultErrorHandler handler = new DefaultErrorHandler(
                (ConsumerRecord<?, ?> rec, Exception ex) ->
                        log.error("Giving up on command at {}-{}@{} after retries: {}", rec.topic(), rec.partition(), rec.offset(), ex.toString()),
                new FixedBackOff(1000L, 3L));
        handler.setAckAfterHandle(true);
        return handler;
    }
}
