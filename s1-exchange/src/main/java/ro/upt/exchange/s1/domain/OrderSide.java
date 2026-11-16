package ro.upt.exchange.s1.domain;

/** orders.side */
public enum OrderSide {
    BUY, SELL;

    public OrderSide opposite() {
        return this == BUY ? SELL : BUY;
    }
}
