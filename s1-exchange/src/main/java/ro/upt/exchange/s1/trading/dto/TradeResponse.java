package ro.upt.exchange.s1.trading.dto;

import ro.upt.exchange.s1.domain.Trade;

import java.math.BigDecimal;
import java.time.Instant;

/** @param side BUY or SELL from the point of view of the requesting user (null on public listings) */
public record TradeResponse(
        long id,
        String symbol,
        long buyOrderId,
        long sellOrderId,
        long buyerUserId,
        long sellerUserId,
        long qty,
        BigDecimal price,
        BigDecimal totalValue,
        Instant executedAt,
        String side) {

    public static TradeResponse from(Trade t, String symbol, Long viewerUserId) {
        String side = viewerUserId == null ? null
                : viewerUserId == t.getBuyerUserId() ? "BUY"
                : viewerUserId == t.getSellerUserId() ? "SELL" : null;
        return new TradeResponse(t.getId(), symbol, t.getBuyOrderId(), t.getSellOrderId(), t.getBuyerUserId(),
                t.getSellerUserId(), t.getQty(), t.getPrice(), t.getTotalValue(), t.getExecutedAt(), side);
    }
}
