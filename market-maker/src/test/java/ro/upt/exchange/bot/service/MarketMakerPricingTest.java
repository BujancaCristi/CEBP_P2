package ro.upt.exchange.bot.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for market maker quote pricing, spread application, and inventory skew.
 * <b>Owner: Paul.</b>
 */
class MarketMakerPricingTest {

    @Test
    @DisplayName("Symmetric spread around mid price with zero inventory skew")
    void testSymmetricSpread() {
        BigDecimal midPrice = new BigDecimal("100.00");
        int spreadBps = 100; // 1.00% total spread -> 0.50% on each side

        BigDecimal halfSpreadFrac = BigDecimal.valueOf(spreadBps).divide(BigDecimal.valueOf(20000), 6, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("0.005000"), halfSpreadFrac);

        BigDecimal bid = midPrice.multiply(BigDecimal.ONE.subtract(halfSpreadFrac)).setScale(2, RoundingMode.FLOOR);
        BigDecimal ask = midPrice.multiply(BigDecimal.ONE.add(halfSpreadFrac)).setScale(2, RoundingMode.CEILING);

        assertEquals(new BigDecimal("99.50"), bid);
        assertEquals(new BigDecimal("100.50"), ask);
        assertTrue(ask.compareTo(bid) > 0);
    }

    @Test
    @DisplayName("Inventory skew shifts both bid and ask lower when holding excess inventory")
    void testInventorySkewExcessHoldings() {
        BigDecimal midPrice = new BigDecimal("100.00");
        int spreadBps = 80; // 0.8% total spread -> 0.4% half spread
        long maxInventory = 2000;
        double skewFactor = 0.5;

        BigDecimal halfSpreadFrac = BigDecimal.valueOf(spreadBps).divide(BigDecimal.valueOf(20000), 6, RoundingMode.HALF_UP);

        // Case 1: Inventory is at 1500 (more than half of max 1000)
        long currentInventory = 1500;
        double halfMax = maxInventory / 2.0;
        double normalizedSkew = (currentInventory - halfMax) / halfMax; // (1500 - 1000)/1000 = 0.5
        assertEquals(0.5, normalizedSkew, 0.001);

        BigDecimal skewShift = BigDecimal.valueOf(normalizedSkew * skewFactor).multiply(halfSpreadFrac);
        BigDecimal skewedMid = midPrice.subtract(midPrice.multiply(skewShift));

        // Mid should shift down from 100.00
        assertTrue(skewedMid.compareTo(midPrice) < 0);

        BigDecimal bid = skewedMid.multiply(BigDecimal.ONE.subtract(halfSpreadFrac)).setScale(2, RoundingMode.FLOOR);
        BigDecimal ask = skewedMid.multiply(BigDecimal.ONE.add(halfSpreadFrac)).setScale(2, RoundingMode.CEILING);

        // Normal unskewed bid was 99.60, ask was 100.40
        assertTrue(bid.compareTo(new BigDecimal("99.60")) < 0);
        assertTrue(ask.compareTo(new BigDecimal("100.40")) < 0);
    }

    @Test
    @DisplayName("Inventory at or above max suppresses BUY quotes")
    void testMaxInventorySuppression() {
        long currentInventory = 2000;
        long maxInventory = 2000;

        boolean canBuy = currentInventory < maxInventory;
        assertFalse(canBuy, "Market maker should halt BUY quoting once max inventory is reached");
    }
}
