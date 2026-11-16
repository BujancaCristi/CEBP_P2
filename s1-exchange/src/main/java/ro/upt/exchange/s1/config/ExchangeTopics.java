package ro.upt.exchange.s1.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Names of the six Kafka topics. Bound from {@code exchange.topics.*} in application.yml.
 * Every producer in S1 (trading, platform outbox relay, ops) refers to topics through this bean,
 * never through string literals.
 */
@Validated
@ConfigurationProperties(prefix = "exchange.topics")
public record ExchangeTopics(
        @Min(1) int partitions,
        @Min(1) short replication,
        @NotBlank String orderCommands,
        @NotBlank String orders,
        @NotBlank String trades,
        @NotBlank String prices,
        @NotBlank String incidents,
        @NotBlank String bots) {
}
