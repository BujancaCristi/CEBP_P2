package ro.upt.exchange.s1.trading.error;

import ro.upt.exchange.s1.domain.IncidentType;

/**
 * Thrown inside the matcher when a command cannot be applied for a business reason (e.g. a
 * modify that needs more cash than the user has). Rolls the command's transaction back; the
 * consumer records an incident and acknowledges the record - it is never retried.
 */
public class CommandRejectedException extends RuntimeException {

    private final IncidentType incidentType;
    private final long orderId;
    private final Long userId;
    private final Long stockId;

    public CommandRejectedException(IncidentType incidentType, long orderId, Long userId, Long stockId, String message) {
        super(message);
        this.incidentType = incidentType;
        this.orderId = orderId;
        this.userId = userId;
        this.stockId = stockId;
    }

    public IncidentType getIncidentType() { return incidentType; }
    public long getOrderId() { return orderId; }
    public Long getUserId() { return userId; }
    public Long getStockId() { return stockId; }
}
