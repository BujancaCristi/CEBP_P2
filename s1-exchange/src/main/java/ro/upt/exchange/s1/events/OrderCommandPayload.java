package ro.upt.exchange.s1.events;

import java.math.BigDecimal;

/**
 * Payload of {@code exchange.order-commands} (S1 internal). Types: PlaceOrder, ModifyOrder, CancelOrder.
 * The command carries only identifiers plus the requested change; the matcher re-reads the order
 * from the database, so a replayed command is harmless.
 *
 * @param price  ModifyOrder: new limit price (null = unchanged). PlaceOrder: informational copy.
 * @param qty    ModifyOrder: new total quantity (null = unchanged). PlaceOrder: informational copy.
 */
public record OrderCommandPayload(
        long orderId,
        String symbol,
        String side,
        String type,
        BigDecimal price,
        Long qty) {
}
