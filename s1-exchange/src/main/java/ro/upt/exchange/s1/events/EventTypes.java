package ro.upt.exchange.s1.events;

/** Event type names, exactly as they appear in the {@code type} field of the envelope. */
public final class EventTypes {
    private EventTypes() {}

    // exchange.order-commands (S1 internal)
    public static final String PLACE_ORDER = "PlaceOrder";
    public static final String MODIFY_ORDER = "ModifyOrder";
    public static final String CANCEL_ORDER = "CancelOrder";

    // exchange.orders
    public static final String ORDER_PLACED = "OrderPlaced";
    public static final String ORDER_UPDATED = "OrderUpdated";
    public static final String ORDER_CANCELLED = "OrderCancelled";
    public static final String ORDER_REJECTED = "OrderRejected";

    // exchange.trades
    public static final String TRADE_EXECUTED = "TradeExecuted";

    // exchange.prices
    public static final String PRICE_UPDATED = "PriceUpdated";

    // exchange.incidents
    public static final String INCIDENT_RAISED = "IncidentRaised";
    public static final String INCIDENT_UPDATED = "IncidentUpdated";

    // exchange.bots
    public static final String BOT_CONFIG_CHANGED = "BotConfigChanged";

    /** Key used on exchange.incidents when the incident is not about one stock. */
    public static final String GLOBAL_KEY = "global";
}
