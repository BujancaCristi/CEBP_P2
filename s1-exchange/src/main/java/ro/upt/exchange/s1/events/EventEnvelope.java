package ro.upt.exchange.s1.events;

import java.time.Instant;
import java.util.UUID;

/**
 * The envelope every Kafka event uses (Application_Description.pdf §4, contracts/events/envelope.schema.json).
 *
 * <pre>{ "eventId": uuid, "type": "TradeExecuted", "occurredAt": iso-8601, "schemaVersion": 1, "payload": {…} }</pre>
 *
 * @param <P> one of the payload records in this package
 */
public record EventEnvelope<P>(UUID eventId, String type, Instant occurredAt, int schemaVersion, P payload) {

    public static final int SCHEMA_VERSION = 1;

    public static <P> EventEnvelope<P> of(String type, P payload) {
        return new EventEnvelope<>(UUID.randomUUID(), type, Instant.now(), SCHEMA_VERSION, payload);
    }
}
