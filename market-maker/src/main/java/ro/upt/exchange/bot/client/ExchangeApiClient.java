package ro.upt.exchange.bot.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ro.upt.exchange.bot.config.BotProperties;

import java.math.BigDecimal;
import java.util.*;

/**
 * REST Client for S1 Exchange Core.
 * Handles authentication, fetching configs, querying books, and placing/canceling orders.
 */
@Component
public class ExchangeApiClient {

    private static final Logger log = LoggerFactory.getLogger(ExchangeApiClient.class);

    private final RestClient restClient;
    private final BotProperties props;
    private final ObjectMapper objectMapper;
    private volatile String authToken;

    public ExchangeApiClient(BotProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(props.s1Url())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public synchronized boolean login() {
        log.info("Attempting login to S1 at {} as '{}'", props.s1Url(), props.username());
        try {
            Map<String, String> body = Map.of(
                    "username", props.username(),
                    "password", props.password()
            );

            String response = restClient.post()
                    .uri("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            if (root.has("token")) {
                this.authToken = root.get("token").asText();
                log.info("Successfully authenticated with S1");
                return true;
            } else {
                log.error("Login response did not contain token: {}", response);
                return false;
            }
        } catch (Exception e) {
            log.error("Login failed: {}", e.getMessage());
            return false;
        }
    }

    public String getAuthToken() {
        return authToken;
    }

    public List<Map<String, Object>> getMyConfigs() {
        ensureAuthenticated();
        try {
            String json = restClient.get()
                    .uri("/api/bots/me/configs")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                    .retrieve()
                    .body(String.class);

            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to fetch bot configs: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public Map<String, Object> getStock(String symbol) {
        ensureAuthenticated();
        try {
            String json = restClient.get()
                    .uri("/api/stocks/" + symbol)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                    .retrieve()
                    .body(String.class);

            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to fetch stock {}: {}", symbol, e.getMessage());
            return Collections.emptyMap();
        }
    }

    public Map<String, Object> getBook(String symbol) {
        ensureAuthenticated();
        try {
            String json = restClient.get()
                    .uri("/api/stocks/" + symbol + "/book")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                    .retrieve()
                    .body(String.class);

            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to fetch order book for {}: {}", symbol, e.getMessage());
            return Collections.emptyMap();
        }
    }

    public List<Map<String, Object>> getPortfolio() {
        ensureAuthenticated();
        try {
            String json = restClient.get()
                    .uri("/api/portfolio")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                    .retrieve()
                    .body(String.class);

            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to fetch portfolio: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public Long placeLimitOrder(String symbol, String side, BigDecimal price, long qty) {
        ensureAuthenticated();
        try {
            Map<String, Object> orderReq = new LinkedHashMap<>();
            orderReq.put("symbol", symbol);
            orderReq.put("side", side);
            orderReq.put("type", "LIMIT");
            orderReq.put("price", price);
            orderReq.put("qty", qty);

            String response = restClient.post()
                    .uri("/api/orders")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(orderReq)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            if (root.has("id")) {
                long orderId = root.get("id").asLong();
                log.debug("Placed {} order #{} for {} {} @ {}", side, orderId, qty, symbol, price);
                return orderId;
            }
        } catch (Exception e) {
            log.warn("Failed to place {} order for {} @ {}: {}", side, symbol, price, e.getMessage());
        }
        return null;
    }

    public boolean cancelOrder(long orderId) {
        ensureAuthenticated();
        try {
            restClient.delete()
                    .uri("/api/orders/" + orderId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                    .retrieve()
                    .toBodilessEntity();
            log.debug("Cancelled order #{}", orderId);
            return true;
        } catch (Exception e) {
            log.warn("Failed to cancel order #{}: {}", orderId, e.getMessage());
            return false;
        }
    }

    private void ensureAuthenticated() {
        if (authToken == null) {
            if (!login()) {
                throw new IllegalStateException("Not authenticated with S1 Exchange Core");
            }
        }
    }
}
