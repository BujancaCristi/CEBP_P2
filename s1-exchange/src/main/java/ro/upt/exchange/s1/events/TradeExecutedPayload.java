package ro.upt.exchange.s1.events;

import ro.upt.exchange.s1.domain.Trade;

import java.math.BigDecimal;
import java.time.Instant;

/** Payload of {@code exchange.trades}: TradeExecuted. */
public record TradeExecutedPayload(
        long tradeId,
        String symbol,
        long buyOrderId,
        long sellOrderId,
        long buyerUserId,
        long sellerUserId,
        long qty,
        BigDecimal price,
        BigDecimal totalValue,
        Instant executedAt) {

    public static TradeExecutedPayload from(Trade t, String symbol) {
        return new TradeExecutedPayload(t.getId(), symbol, t.getBuyOrderId(), t.getSellOrderId(),
                t.getBuyerUserId(), t.getSellerUserId(), t.getQty(), t.getPrice(), t.getTotalValue(), t.getExecutedAt());
    }
}
