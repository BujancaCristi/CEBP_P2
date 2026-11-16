package ro.upt.exchange.s1.trading;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import ro.upt.exchange.s1.domain.Stock;
import ro.upt.exchange.s1.platform.AuthenticatedUser;
import ro.upt.exchange.s1.testsupport.AbstractIntegrationTest;
import ro.upt.exchange.s1.testsupport.Fixtures;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Buy-from-offer through the HTTP API (SAFE mode): the happy path and every 4xx. */
@AutoConfigureMockMvc
@Import(Fixtures.class)
class BuyOfferIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired Fixtures fx;

    private static final BigDecimal CASH = new BigDecimal("10000.00");

    @Test
    void buyPartOfAnOfferSettlesInOneTransaction() throws Exception {
        Stock stock = fx.stock(new BigDecimal("10.0000"));
        AuthenticatedUser seller = fx.trader("seller", CASH);
        AuthenticatedUser buyer = fx.trader("buyer", CASH);
        long offer = fx.offer(seller, stock, new BigDecimal("10.00"), 100);
        long v = fx.version(offer);

        mvc.perform(post("/api/offers/{id}/buy", offer).header("X-User-Id", buyer.userId()).contentType(APPLICATION_JSON)
                        .content("{\"qty\":40,\"maxPrice\":10.00,\"seenVersion\":" + v + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trade.qty").value(40))
                .andExpect(jsonPath("$.trade.price").value(10.0))
                .andExpect(jsonPath("$.order.status").value("FILLED"))
                .andExpect(jsonPath("$.offer.remainingQty").value(60))
                .andExpect(jsonPath("$.offer.version").value(v + 1));

        assertThat(fx.cash(buyer)).isEqualByComparingTo("9600.00");
        assertThat(fx.reservedCash(buyer)).isEqualByComparingTo("0.00");
        assertThat(fx.shares(buyer, stock)).isEqualTo(40);
        assertThat(fx.cash(seller)).isEqualByComparingTo("10400.00");
        assertThat(fx.shares(seller, stock)).isEqualTo(60);
        assertThat(fx.reservedShares(seller, stock)).isEqualTo(60);
        assertThat(fx.status(offer)).isEqualTo("PARTIALLY_FILLED");
    }

    @Test
    void staleVersionGives409PriceChangedWithTheCurrentOffer() throws Exception {
        Stock stock = fx.stock(new BigDecimal("10.0000"));
        AuthenticatedUser seller = fx.trader("seller", CASH);
        AuthenticatedUser buyer = fx.trader("buyer", CASH);
        long offer = fx.offer(seller, stock, new BigDecimal("10.00"), 100);
        long seen = fx.version(offer);

        // the seller raises the price (bumps the version)
        assertThat(fx.reprice(offer, new BigDecimal("12.00"), seen)).isTrue();

        mvc.perform(post("/api/offers/{id}/buy", offer).header("X-User-Id", buyer.userId()).contentType(APPLICATION_JSON)
                        .content("{\"qty\":100,\"maxPrice\":10.00,\"seenVersion\":" + seen + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRICE_CHANGED"))
                .andExpect(jsonPath("$.reason").value("PRICE_CHANGED"))
                .andExpect(jsonPath("$.offer.price").value(12.0))
                .andExpect(jsonPath("$.offer.version").value(seen + 1));

        // nothing happened: no trade, no money moved, the buyer's order was rolled back
        assertThat(fx.soldFrom(offer)).isZero();
        assertThat(fx.cash(buyer)).isEqualByComparingTo(CASH);
        assertThat(fx.reservedCash(buyer)).isEqualByComparingTo("0.00");

        // confirming with the new version and price succeeds
        mvc.perform(post("/api/offers/{id}/buy", offer).header("X-User-Id", buyer.userId()).contentType(APPLICATION_JSON)
                        .content("{\"qty\":100,\"maxPrice\":12.00,\"seenVersion\":" + (seen + 1) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trade.price").value(12.0))
                .andExpect(jsonPath("$.offer.status").value("FILLED"));
    }

    @Test
    void tooFewSharesGives409NoLongerAvailable() throws Exception {
        Stock stock = fx.stock(new BigDecimal("10.0000"));
        AuthenticatedUser seller = fx.trader("seller", CASH);
        AuthenticatedUser buyer = fx.trader("buyer", CASH);
        long offer = fx.offer(seller, stock, new BigDecimal("10.00"), 100);
        long v = fx.version(offer);

        mvc.perform(post("/api/offers/{id}/buy", offer).header("X-User-Id", buyer.userId()).contentType(APPLICATION_JSON)
                        .content("{\"qty\":70,\"maxPrice\":10.00,\"seenVersion\":" + v + "}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/offers/{id}/buy", offer).header("X-User-Id", buyer.userId()).contentType(APPLICATION_JSON)
                        .content("{\"qty\":70,\"maxPrice\":10.00,\"seenVersion\":" + (v + 1) + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("NO_LONGER_AVAILABLE"))
                .andExpect(jsonPath("$.offer.remainingQty").value(30));
    }

    @Test
    void insufficientFundsGives422AndOwnOfferGives400() throws Exception {
        Stock stock = fx.stock(new BigDecimal("10.0000"));
        AuthenticatedUser seller = fx.trader("seller", CASH);
        AuthenticatedUser poor = fx.trader("poor", new BigDecimal("5.00"));
        long offer = fx.offer(seller, stock, new BigDecimal("10.00"), 100);
        long v = fx.version(offer);

        mvc.perform(post("/api/offers/{id}/buy", offer).header("X-User-Id", poor.userId()).contentType(APPLICATION_JSON)
                        .content("{\"qty\":10,\"maxPrice\":10.00,\"seenVersion\":" + v + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
        mvc.perform(post("/api/offers/{id}/buy", offer).header("X-User-Id", seller.userId()).contentType(APPLICATION_JSON)
                        .content("{\"qty\":10,\"maxPrice\":10.00,\"seenVersion\":" + v + "}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/offers/{id}/buy", 999_999_999L).header("X-User-Id", poor.userId()).contentType(APPLICATION_JSON)
                        .content("{\"qty\":10,\"maxPrice\":10.00,\"seenVersion\":0}"))
                .andExpect(status().isNotFound());
        assertThat(fx.remaining(offer)).isEqualTo(100);
    }
}
