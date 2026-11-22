package ro.upt.exchange.mm;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import ro.upt.exchange.mm.MarketMakerApplication.MmProperties;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The small part of S1's public API the bot needs (POST /api/auth/login, orders, configs, portfolio). */
@Component
public class S1Api {

    private final RestClient http;
    private final MmProperties props;
    private volatile String token;

    public S1Api(RestClient.Builder builder, MmProperties props) {
        this.http = builder.baseUrl(props.s1Url()).build();
        this.props = props;
    }

    public void login() {
        JsonNode body = http.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", props.username(), "password", props.password()))
                .retrieve().body(JsonNode.class);
        token = body.path("token").asText();
    }

    public boolean loggedIn() {
        return token != null;
    }

    public JsonNode myConfigs() {
        return get("/api/bots/me/configs");
    }

    public JsonNode stocks() {
        return get("/api/stocks");
    }

    public JsonNode portfolio() {
        return get("/api/portfolio");
    }

    /** Open, partially filled or pending orders of the bot (used once at startup to clean up old quotes). */
    public List<Long> myOpenOrderIds() {
        List<Long> ids = new ArrayList<>();
        JsonNode page = get("/api/orders/mine?page=0&size=500");
        for (JsonNode o : page.path("content")) {
            String status = o.path("status").asText();
            if (status.equals("OPEN") || status.equals("PARTIALLY_FILLED") || status.equals("PENDING")) {
                ids.add(o.path("id").asLong());
            }
        }
        return ids;
    }

    public Optional<Long> placeLimit(String symbol, String side, BigDecimal price, long qty) {
        try {
            JsonNode o = http.post().uri("/api/orders").contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + token)
                    .body(Map.of("symbol", symbol, "side", side, "type", "LIMIT", "price", price, "qty", qty))
                    .retrieve().body(JsonNode.class);
            return Optional.of(o.path("id").asLong());
        } catch (RestClientResponseException e) {
            return Optional.empty();
        }
    }

    public void cancel(long orderId) {
        try {
            http.delete().uri("/api/orders/{id}", orderId).header("Authorization", "Bearer " + token)
                    .retrieve().toBodilessEntity();
        } catch (RestClientResponseException ignored) {
            // already filled or cancelled
        }
    }

    public void reportInventoryLimit(String symbol, long inventory, long maxInventory) {
        try {
            http.post().uri(u -> u.path("/api/bots/me/inventory-limit").queryParam("symbol", symbol)
                            .queryParam("inventory", inventory).queryParam("maxInventory", maxInventory).build())
                    .header("Authorization", "Bearer " + token).retrieve().toBodilessEntity();
        } catch (RestClientResponseException ignored) {
            // best effort
        }
    }

    private JsonNode get(String path) {
        try {
            return http.get().uri(path).header("Authorization", "Bearer " + token).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) token = null;
            throw e;
        }
    }
}
