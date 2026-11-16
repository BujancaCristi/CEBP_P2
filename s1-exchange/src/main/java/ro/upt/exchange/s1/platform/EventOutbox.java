package ro.upt.exchange.s1.platform;

import ro.upt.exchange.s1.events.EventEnvelope;

/**
 * Transactional outbox. <b>Owner: Cezar.</b> Signature fixed in Phase 0.
 *
 * <p>{@code add} inserts a row in {@code event_outbox} inside the caller's transaction; the relay
 * (also Cezar's) publishes it to Kafka with the same key and marks it published. The envelope's
 * {@code eventId} becomes the row id so S2 can de-duplicate on it.
 *
 * @param topic   one of the names in {@code ExchangeTopics}
 * @param key     the stock symbol (or {@code "global"} for incidents that are not about one stock)
 * @param type    the event type (see {@code EventTypes})
 * @param payload the payload record; the implementation wraps it in an {@link EventEnvelope}
 */
public interface EventOutbox {

    void add(String topic, String key, String type, Object payload);
}
