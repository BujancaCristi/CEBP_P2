package ro.upt.exchange.s1.realtime;

import ro.upt.exchange.s1.events.BookSnapshot;
import ro.upt.exchange.s1.events.BotConfigChangedPayload;
import ro.upt.exchange.s1.events.OrderEventPayload;
import ro.upt.exchange.s1.events.PriceUpdatedPayload;
import ro.upt.exchange.s1.events.TradeExecutedPayload;

/** STUB - replaced by StompLivePublisher. Kept without @Service for test reference. */
public class NoopLivePublisher implements LivePublisher {
    @Override public void priceChanged(PriceUpdatedPayload price) { }
    @Override public void bookChanged(BookSnapshot book) { }
    @Override public void tradeExecuted(TradeExecutedPayload trade) { }
    @Override public void orderUpdated(long userId, OrderEventPayload order) { }
    @Override public void botConfigChanged(BotConfigChangedPayload config) { }
}
