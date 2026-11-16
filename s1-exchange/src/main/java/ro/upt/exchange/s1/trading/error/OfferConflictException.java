package ro.upt.exchange.s1.trading.error;

import org.springframework.http.HttpStatus;
import ro.upt.exchange.s1.trading.dto.OfferResponse;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 409 from buy-from-offer. The body carries {@code reason} and the <em>current</em> offer so the
 * web app can show "Price changed to 12.00, confirm?" and retry with the new {@code seenVersion}.
 */
public class OfferConflictException extends TradingException {

    public enum Reason {
        /** The offer's version is not the one the buyer saw (price or quantity changed) - case 2. */
        PRICE_CHANGED,
        /** The offer is gone or has fewer shares than requested - case 1. */
        NO_LONGER_AVAILABLE
    }

    private final Reason reason;
    private final OfferResponse currentOffer;

    public OfferConflictException(Reason reason, OfferResponse currentOffer) {
        super(HttpStatus.CONFLICT, reason.name(), messageFor(reason, currentOffer), props(reason, currentOffer));
        this.reason = reason;
        this.currentOffer = currentOffer;
    }

    public Reason getReason() { return reason; }
    public OfferResponse getCurrentOffer() { return currentOffer; }

    private static String messageFor(Reason reason, OfferResponse offer) {
        return switch (reason) {
            case PRICE_CHANGED -> "The offer changed since you saw it: price is now " + offer.price()
                    + " for " + offer.remainingQty() + " shares (version " + offer.version() + ")";
            case NO_LONGER_AVAILABLE -> "The offer is no longer available"
                    + (offer == null ? "" : " for the requested quantity (" + offer.remainingQty() + " left)");
        };
    }

    private static Map<String, Object> props(Reason reason, OfferResponse offer) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reason", reason.name());
        m.put("offer", offer);
        return m;
    }
}
