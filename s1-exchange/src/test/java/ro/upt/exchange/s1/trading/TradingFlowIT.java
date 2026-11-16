package ro.upt.exchange.s1.trading;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ro.upt.exchange.s1.domain.Stock;
import ro.upt.exchange.s1.platform.AuthenticatedUser;
import ro.upt.exchange.s1.testsupport.AbstractIntegrationTest;
import ro.upt.exchange.s1.testsupport.Fixtures;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end through the HTTP API and the Kafka matcher (SAFE mode):
 * place → PENDING → matched → trades → balances, book and outbox events.
 * Booting this context also validates the JPA mapping against the Flyway schema (ddl-auto=validate).
 */
@AutoConfigureMockMvc
@Import(Fixtures.class)
class TradingFlowIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired Fixtures fx;
    @Autowired ObjectMapper json;

    private static final BigDecimal CASH = new BigDecimal("10000.00");

    @Test
    void limitBuyMatchesRestingSellAtRestingPriceAndSettles() throws Exception {
        Stock stock = fx.stock(new BigDecimal("10.0000"));
        AuthenticatedUser seller = fx.trader("seller", CASH);
        AuthenticatedUser buyer = fx.trader("buyer", CASH);
        long offer = fx.offer(seller, stock, new BigDecimal("10.00"), 100);

        // buyer is willing to pay 11, gets filled at the resting 10
        long orderId = place(buyer, stock, "BUY", "LIMIT", "11.00", 40);
        assertThat(fx.reservedCash(buyer)).isEqualByComparingTo("440.00");

        await().atMost(Duration.ofSeconds(15)).until(() -> fx.status(orderId).equals("FILLED"));

        assertThat(fx.remaining(offer)).isEqualTo(60);
        assertThat(fx.status(offer)).isEqualTo("PARTIALLY_FILLED");
        assertThat(fx.soldFrom(offer)).isEqualTo(40);
        assertThat(fx.cash(buyer)).isEqualByComparingTo("9600.00");       // 40 × 10
        assertThat(fx.reservedCash(buyer)).isEqualByComparingTo("0.00");  // the 11-vs-10 excess was released
        assertThat(fx.shares(buyer, stock)).isEqualTo(40);
        assertThat(fx.cash(seller)).isEqualByComparingTo("10400.00");
        assertThat(fx.shares(seller, stock)).isEqualTo(60);
        assertThat(fx.reservedShares(seller, stock)).isEqualTo(60);

        // book shows the remaining ask; last price moved to 10
        mvc.perform(get("/api/stocks/{s}/book", stock.getSymbol()).header("X-User-Id", buyer.userId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastPrice").value(10.0))
                .andExpect(jsonPath("$.bestAsk").value(10.0))
                .andExpect(jsonPath("$.asks[0].qty").value(60));

        // my trades / my orders
        mvc.perform(get("/api/trades/mine").header("X-User-Id", buyer.userId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].side").value("BUY"))
                .andExpect(jsonPath("$.content[0].qty").value(40));
        mvc.perform(get("/api/orders/mine").header("X-User-Id", seller.userId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("PARTIALLY_FILLED"));

        assertThat(fx.outboxCount("TradeExecuted")).isGreaterThanOrEqualTo(1);
        assertThat(fx.outboxCount("PriceUpdated")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void limitSellRestsInBookThenCancelReleasesShares() throws Exception {
        Stock stock = fx.stock(new BigDecimal("20.0000"));
        AuthenticatedUser seller = fx.trader("seller", CASH);
        fx.position(seller, stock, 500, 0);

        long orderId = place(seller, stock, "SELL", "LIMIT", "21.00", 100);
        assertThat(fx.reservedShares(seller, stock)).isEqualTo(100);
        await().atMost(Duration.ofSeconds(15)).until(() -> fx.status(orderId).equals("OPEN"));

        mvc.perform(get("/api/offers").param("symbol", stock.getSymbol()).header("X-User-Id", seller.userId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value(orderId))
                .andExpect(jsonPath("$[0].totalPrice").value(2100.0))
                .andExpect(jsonPath("$[0].version").isNumber());

        mvc.perform(delete("/api/orders/{id}", orderId).header("X-User-Id", seller.userId()))
                .andExpect(status().isAccepted());
        await().atMost(Duration.ofSeconds(15)).until(() -> fx.status(orderId).equals("CANCELLED"));
        assertThat(fx.reservedShares(seller, stock)).isZero();
        assertThat(fx.shares(seller, stock)).isEqualTo(500);
    }

    @Test
    void marketBuyIsImmediateOrCancelAndPaysExactly() throws Exception {
        Stock stock = fx.stock(new BigDecimal("5.0000"));
        AuthenticatedUser seller = fx.trader("seller", CASH);
        AuthenticatedUser buyer = fx.trader("buyer", CASH);
        fx.offer(seller, stock, new BigDecimal("5.00"), 30);
        fx.offer(seller, stock, new BigDecimal("6.00"), 30);

        long orderId = place(buyer, stock, "BUY", "MARKET", null, 100);
        await().atMost(Duration.ofSeconds(15)).until(() -> fx.status(orderId).equals("CANCELLED"));

        assertThat(fx.remaining(orderId)).isEqualTo(40);                   // 60 filled, 40 nothing to buy
        assertThat(fx.cash(buyer)).isEqualByComparingTo("9670.00");        // 30×5 + 30×6
        assertThat(fx.reservedCash(buyer)).isEqualByComparingTo("0.00");
        assertThat(fx.shares(buyer, stock)).isEqualTo(60);
    }

    @Test
    void placeIsRejectedWithoutFunds() throws Exception {
        Stock stock = fx.stock(new BigDecimal("100.0000"));
        AuthenticatedUser poor = fx.trader("poor", new BigDecimal("50.00"));
        mvc.perform(post("/api/orders").header("X-User-Id", poor.userId()).contentType(APPLICATION_JSON)
                        .content(body(stock, "BUY", "LIMIT", "100.00", 10)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectReason").value(org.hamcrest.Matchers.startsWith("INSUFFICIENT_FUNDS")));
    }

    @Test
    void validationErrorsAreProblemDetails() throws Exception {
        Stock stock = fx.stock(new BigDecimal("1.0000"));
        AuthenticatedUser t = fx.trader("t", CASH);
        mvc.perform(post("/api/orders").header("X-User-Id", t.userId()).contentType(APPLICATION_JSON)
                        .content("{\"symbol\":\"" + stock.getSymbol() + "\",\"side\":\"BUY\",\"type\":\"MARKET\",\"price\":1,\"qty\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.errors.qty").exists());
        mvc.perform(post("/api/orders").header("X-User-Id", t.userId()).contentType(APPLICATION_JSON)
                        .content(body(stock, "BUY", "LIMIT", "1.005", 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("tick size")));
        mvc.perform(get("/api/orders/mine"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void openApiSpecIsPublishedWithTheTradingOperations() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/orders'].post.operationId").value("place"))
                .andExpect(jsonPath("$.paths['/api/offers/{orderId}/buy'].post").exists())
                .andExpect(jsonPath("$.paths['/api/stocks/{symbol}/book'].get").exists())
                .andExpect(jsonPath("$.paths['/api/trades/mine'].get").exists());
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
    }

    // ------------------------------------------------------------------ helpers

    private long place(AuthenticatedUser who, Stock stock, String side, String type, String price, long qty) throws Exception {
        MvcResult r = mvc.perform(post("/api/orders").header("X-User-Id", who.userId()).contentType(APPLICATION_JSON)
                        .content(body(stock, side, type, price, qty)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        JsonNode n = json.readTree(r.getResponse().getContentAsString());
        return n.get("id").asLong();
    }

    private static String body(Stock stock, String side, String type, String price, long qty) {
        return "{\"symbol\":\"" + stock.getSymbol() + "\",\"side\":\"" + side + "\",\"type\":\"" + type + "\""
                + (price == null ? "" : ",\"price\":" + price) + ",\"qty\":" + qty + "}";
    }
}
