package ro.upt.exchange.s1.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import ro.upt.exchange.s1.events.BookSnapshot;
import ro.upt.exchange.s1.events.BotConfigChangedPayload;
import ro.upt.exchange.s1.events.OrderEventPayload;
import ro.upt.exchange.s1.events.PriceUpdatedPayload;
import ro.upt.exchange.s1.events.TradeExecutedPayload;

/**
 * Real STOMP LivePublisher implementation. <b>Owner: Paul.</b>
 * Pushes live updates over STOMP WebSocket destinations matching contracts/s1/ops.yaml.
 */
@Service
public class StompLivePublisher implements LivePublisher {

    private static final Logger log = LoggerFactory.getLogger(StompLivePublisher.class);

    private final SimpMessagingTemplate messaging;

    public StompLivePublisher(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @Override
    public void priceChanged(PriceUpdatedPayload price) {
        log.debug("Live push price: {} -> {}", price.symbol(), price.value());
        messaging.convertAndSend("/topic/prices/" + price.symbol(), price);
    }

    @Override
    public void bookChanged(BookSnapshot book) {
        log.debug("Live push book: {}", book.symbol());
        messaging.convertAndSend("/topic/book/" + book.symbol(), book);
    }

    @Override
    public void tradeExecuted(TradeExecutedPayload trade) {
        log.debug("Live push trade: {} id={}", trade.symbol(), trade.tradeId());
        messaging.convertAndSend("/topic/trades/" + trade.symbol(), trade);
    }

    @Override
    public void orderUpdated(long userId, OrderEventPayload order) {
        log.debug("Live push order to user {}: orderId={} status={}", userId, order.orderId(), order.status());
        messaging.convertAndSendToUser(String.valueOf(userId), "/queue/orders", order);
    }

    @Override
    public void botConfigChanged(BotConfigChangedPayload config) {
        log.debug("Live push bot config changed: botUserId={} symbol={}", config.botUserId(), config.symbol());
        messaging.convertAndSendToUser(String.valueOf(config.botUserId()), "/queue/bot-config", config);
        // Also broadcast on topic for any listeners
        messaging.convertAndSend("/topic/bot-config/" + config.symbol(), config);
    }
}
