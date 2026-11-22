package ro.upt.exchange.s1.realtime;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import ro.upt.exchange.s1.events.BookSnapshot;
import ro.upt.exchange.s1.events.BotConfigChangedPayload;
import ro.upt.exchange.s1.events.OrderEventPayload;
import ro.upt.exchange.s1.events.PriceUpdatedPayload;
import ro.upt.exchange.s1.events.TradeExecutedPayload;

/** Pushes live updates to the destinations listed in contracts/s1/ops.yaml. Owner: Paul. */
@Service
public class StompLivePublisher implements LivePublisher {

    private final SimpMessagingTemplate stomp;

    public StompLivePublisher(SimpMessagingTemplate stomp) {
        this.stomp = stomp;
    }

    @Override
    public void priceChanged(PriceUpdatedPayload price) {
        stomp.convertAndSend("/topic/prices/" + price.symbol(), price);
        stomp.convertAndSend("/topic/prices", price);
    }

    @Override
    public void bookChanged(BookSnapshot book) {
        stomp.convertAndSend("/topic/book/" + book.symbol(), book);
    }

    @Override
    public void tradeExecuted(TradeExecutedPayload trade) {
        stomp.convertAndSend("/topic/trades/" + trade.symbol(), trade);
    }

    @Override
    public void orderUpdated(long userId, OrderEventPayload order) {
        stomp.convertAndSendToUser(Long.toString(userId), "/queue/orders", order);
    }

    @Override
    public void botConfigChanged(BotConfigChangedPayload config) {
        stomp.convertAndSendToUser(Long.toString(config.botUserId()), "/queue/bot-config", config);
        stomp.convertAndSend("/topic/bot-config", config);
    }
}
