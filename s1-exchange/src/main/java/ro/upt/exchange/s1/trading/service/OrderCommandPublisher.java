package ro.upt.exchange.s1.trading.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import ro.upt.exchange.s1.config.ExchangeTopics;
import ro.upt.exchange.s1.domain.Order;
import ro.upt.exchange.s1.events.EventEnvelope;
import ro.upt.exchange.s1.events.EventTypes;
import ro.upt.exchange.s1.events.OrderCommandPayload;

import java.math.BigDecimal;

/**
 * Sends PlaceOrder / ModifyOrder / CancelOrder to {@code exchange.order-commands}.
 *
 * <p>Always called <em>after</em> the transaction that created or validated the order has
 * committed, so the matcher never reads an order that does not exist yet. The key is the symbol
 * (SAFE) so that one consumer thread sees every command of a stock, in order.
 */
@Component
public class OrderCommandPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderCommandPublisher.class);

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final ExchangeTopics topics;
    private final OrderWriteStrategy strategy;

    public OrderCommandPublisher(KafkaTemplate<String, String> kafka, ObjectMapper mapper,
                                 ExchangeTopics topics, OrderWriteStrategy strategy) {
        this.kafka = kafka;
        this.mapper = mapper;
        this.topics = topics;
        this.strategy = strategy;
    }

    public void placeAfterCommit(Order o, String symbol) {
        TradingEvents.afterCommit(() -> place(o, symbol));
    }

    public void place(Order o, String symbol) {
        send(EventTypes.PLACE_ORDER, new OrderCommandPayload(o.getId(), symbol, o.getSide().name(),
                o.getType().name(), o.getPrice(), o.getQty()));
    }

    public void modifyAfterCommit(Order o, String symbol, BigDecimal newPrice, Long newQty) {
        TradingEvents.afterCommit(() -> send(EventTypes.MODIFY_ORDER, new OrderCommandPayload(o.getId(), symbol,
                o.getSide().name(), o.getType().name(), newPrice, newQty)));
    }

    public void cancelAfterCommit(Order o, String symbol) {
        TradingEvents.afterCommit(() -> send(EventTypes.CANCEL_ORDER, new OrderCommandPayload(o.getId(), symbol,
                o.getSide().name(), o.getType().name(), null, null)));
    }

    private void send(String type, OrderCommandPayload payload) {
        EventEnvelope<OrderCommandPayload> envelope = EventEnvelope.of(type, payload);
        String key = strategy.commandKey(payload.orderId(), payload.symbol());
        try {
            String json = mapper.writeValueAsString(envelope);
            kafka.send(topics.orderCommands(), key, json).whenComplete((r, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish {} for order {} (the pending-order sweeper will retry)", type, payload.orderId(), ex);
                }
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise order command", e);
        }
    }
}
