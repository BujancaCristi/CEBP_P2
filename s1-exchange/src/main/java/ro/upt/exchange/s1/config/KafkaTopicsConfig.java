package ro.upt.exchange.s1.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

/**
 * Creates the six topics at startup (compose has no init container for Kafka).
 * 3 partitions each; the symbol is the message key so every stock's events stay ordered.
 */
@Configuration
public class KafkaTopicsConfig {

    private final ExchangeTopics topics;

    public KafkaTopicsConfig(ExchangeTopics topics) {
        this.topics = topics;
    }

    @Bean
    public KafkaAdmin.NewTopics exchangeTopics() {
        return new KafkaAdmin.NewTopics(
                topic(topics.orderCommands()),
                topic(topics.orders()),
                topic(topics.trades()),
                topic(topics.prices()),
                topic(topics.incidents()),
                topic(topics.bots()));
    }

    private NewTopic topic(String name) {
        return TopicBuilder.name(name)
                .partitions(topics.partitions())
                .replicas(topics.replication())
                .build();
    }
}
