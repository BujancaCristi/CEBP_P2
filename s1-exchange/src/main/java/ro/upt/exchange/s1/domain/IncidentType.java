package ro.upt.exchange.s1.domain;

/** incidents.type */
public enum IncidentType {
    ORDER_REJECTED,
    INSUFFICIENT_FUNDS,
    INSUFFICIENT_SHARES,
    CONCURRENCY_CONFLICT,
    PRICE_SPIKE,
    BOT_INVENTORY_LIMIT,
    EVENT_PUBLISH_FAILED
}
