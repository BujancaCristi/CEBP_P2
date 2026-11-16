package ro.upt.exchange.s1.trading.error;

import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.Map;

/** Base of every error trading/ turns into an RFC 9457 problem response. */
public class TradingException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> properties;

    public TradingException(HttpStatus status, String code, String message) {
        this(status, code, message, Collections.emptyMap());
    }

    public TradingException(HttpStatus status, String code, String message, Map<String, Object> properties) {
        super(message);
        this.status = status;
        this.code = code;
        this.properties = properties;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public Map<String, Object> getProperties() { return properties; }

    // ----- factories for the common cases

    public static TradingException notFound(String what, Object id) {
        return new TradingException(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " " + id + " not found");
    }

    public static TradingException badRequest(String message) {
        return new TradingException(HttpStatus.BAD_REQUEST, "VALIDATION", message);
    }

    public static TradingException forbidden(String message) {
        return new TradingException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public static TradingException invalidState(String message) {
        return new TradingException(HttpStatus.CONFLICT, "INVALID_STATE", message);
    }

    public static TradingException insufficientFunds(String message, Map<String, Object> props) {
        return new TradingException(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS", message, props);
    }

    public static TradingException insufficientShares(String message, Map<String, Object> props) {
        return new TradingException(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_SHARES", message, props);
    }
}
