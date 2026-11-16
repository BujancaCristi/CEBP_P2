package ro.upt.exchange.s1.realtime;

import ro.upt.exchange.s1.events.BookSnapshot;
import ro.upt.exchange.s1.events.BotConfigChangedPayload;
import ro.upt.exchange.s1.events.OrderEventPayload;
import ro.upt.exchange.s1.events.PriceUpdatedPayload;
import ro.upt.exchange.s1.events.TradeExecutedPayload;

/**
 * Live updates over STOMP. <b>Owner: Paul.</b> Signatures fixed in Phase 0.
 *
 * <p>Trading calls these <em>after</em> its transaction commits. The payloads are the same records
 * that go to Kafka, so the web app sees one shape everywhere. Destinations
 * (contracts/s1/ops.yaml): {@code /topic/prices/{symbol}}, {@code /topic/book/{symbol}},
 * {@code /topic/trades/{symbol}}, {@code /user/queue/orders}, {@code /user/queue/bot-config}.
 */
public interface LivePublisher {

    void priceChanged(PriceUpdatedPayload price);

    void bookChanged(BookSnapshot book);

    void tradeExecuted(TradeExecutedPayload trade);

    /** Sent only to the order's owner. */
    void orderUpdated(long userId, OrderEventPayload order);

    void botConfigChanged(BotConfigChangedPayload config);
}
