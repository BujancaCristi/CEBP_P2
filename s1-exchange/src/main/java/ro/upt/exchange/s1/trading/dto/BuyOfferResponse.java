package ro.upt.exchange.s1.trading.dto;

/** 200 from buy-from-offer: the trade, the buyer's (filled) order and the offer as it is now. */
public record BuyOfferResponse(TradeResponse trade, OrderResponse order, OfferResponse offer) {
}
