package ro.upt.exchange.bot.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import ro.upt.exchange.bot.config.BotProperties;

import java.lang.reflect.Type;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * STOMP client connecting to S1's /ws endpoint to receive live fills and config changes.
 */
@Component
public class ExchangeStompClient {

    private static final Logger log = LoggerFactory.getLogger(ExchangeStompClient.class);

    private final BotProperties props;
    private final ExchangeApiClient apiClient;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private StompSession session;

    private Consumer<JsonNode> onOrderFillListener;
    private Consumer<JsonNode> onConfigChangeListener;

    public ExchangeStompClient(BotProperties props, ExchangeApiClient apiClient, ObjectMapper objectMapper) {
        this.props = props;
        this.apiClient = apiClient;
        this.objectMapper = objectMapper;
    }

    public void setOnOrderFillListener(Consumer<JsonNode> listener) {
        this.onOrderFillListener = listener;
    }

    public void setOnConfigChangeListener(Consumer<JsonNode> listener) {
        this.onConfigChangeListener = listener;
    }

    public synchronized void connect() {
        if (connected.get()) return;

        String wsUrl = props.s1Url().replace("http://", "ws://").replace("https://", "wss://") + "/ws";
        log.info("Connecting to STOMP WebSocket at {}", wsUrl);

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new StringMessageConverter());

        StompHeaders connectHeaders = new StompHeaders();
        String token = apiClient.getAuthToken();
        if (token != null) {
            connectHeaders.add("Authorization", "Bearer " + token);
        }

        stompClient.connectAsync(wsUrl, (WebSocketHttpHeaders) null, connectHeaders, new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(@NonNull StompSession newSession, @NonNull StompHeaders connectedHeaders) {
                session = newSession;
                connected.set(true);
                log.info("STOMP connection established to S1");

                // Subscribe to user queue for own order updates
                session.subscribe("/user/queue/orders", new StompFrameHandler() {
                    @Override
                    public @NonNull Type getPayloadType(@NonNull StompHeaders headers) {
                        return String.class;
                    }

                    @Override
                    public void handleFrame(@NonNull StompHeaders headers, Object payload) {
                        try {
                            JsonNode node = objectMapper.readTree(String.valueOf(payload));
                            log.info("STOMP order update received: {}", node.get("status"));
                            if (onOrderFillListener != null) {
                                onOrderFillListener.accept(node);
                            }
                        } catch (Exception e) {
                            log.warn("Error parsing STOMP order update: {}", e.getMessage());
                        }
                    }
                });

                // Subscribe to user queue for bot config changes
                session.subscribe("/user/queue/bot-config", new StompFrameHandler() {
                    @Override
                    public @NonNull Type getPayloadType(@NonNull StompHeaders headers) {
                        return String.class;
                    }

                    @Override
                    public void handleFrame(@NonNull StompHeaders headers, Object payload) {
                        try {
                            JsonNode node = objectMapper.readTree(String.valueOf(payload));
                            log.info("STOMP bot-config update received: {}", node);
                            if (onConfigChangeListener != null) {
                                onConfigChangeListener.accept(node);
                            }
                        } catch (Exception e) {
                            log.warn("Error parsing STOMP config update: {}", e.getMessage());
                        }
                    }
                });
            }

            @Override
            public void handleTransportError(@NonNull StompSession session, @NonNull Throwable exception) {
                log.warn("STOMP transport error: {}", exception.getMessage());
                connected.set(false);
            }
        });
    }

    public boolean isConnected() {
        return connected.get();
    }
}
