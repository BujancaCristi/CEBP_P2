package ro.upt.exchange.s1.domain;

import java.util.EnumSet;
import java.util.Set;

/** orders.status */
public enum OrderStatus {
    /** Accepted by the API, reserved, waiting for the matching consumer. */
    PENDING,
    /** Resting in the book, nothing filled yet. */
    OPEN,
    PARTIALLY_FILLED,
    FILLED,
    CANCELLED,
    REJECTED;

    /** Orders that sit in the book and can still trade (the "offers" when side = SELL). */
    public static final Set<OrderStatus> LIVE = EnumSet.of(OPEN, PARTIALLY_FILLED);

    /** Orders the owner may still cancel or modify. */
    public static final Set<OrderStatus> CANCELLABLE = EnumSet.of(PENDING, OPEN, PARTIALLY_FILLED);

    public boolean isLive() {
        return LIVE.contains(this);
    }

    public boolean isTerminal() {
        return this == FILLED || this == CANCELLED || this == REJECTED;
    }
}
