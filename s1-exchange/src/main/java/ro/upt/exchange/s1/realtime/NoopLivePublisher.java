package ro.upt.exchange.s1.realtime;

import org.springframework.stereotype.Service;
import ro.upt.exchange.s1.events.BookSnapshot;
import ro.upt.exchange.s1.events.BotConfigChangedPayload;
import ro.upt.exchange.s1.events.OrderEventPayload;
import ro.upt.exchange.s1.events.PriceUpdatedPayload;
import ro.upt.exchange.s1.events.TradeExecutedPayload;

/** STUB - owned by Paul, delete when the real STOMP {@code LivePublisher} lands. */
@Service
public class NoopLivePublisher implements LivePublisher {
    @Override public void priceChanged(PriceUpdatedPayload price) { }
    @Override public void bookChanged(BookSnapshot book) { }
    @Override public void tradeExecuted(TradeExecutedPayload trade) { }
    @Override public void orderUpdated(long userId, OrderEventPayload order) { }
    @Override public void botConfigChanged(BotConfigChangedPayload config) { }
}
