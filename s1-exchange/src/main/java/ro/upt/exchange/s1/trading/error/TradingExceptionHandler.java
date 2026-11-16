package ro.upt.exchange.s1.trading.error;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import ro.upt.exchange.s1.platform.InsufficientFundsException;
import ro.upt.exchange.s1.platform.InsufficientSharesException;
import ro.upt.exchange.s1.trading.api.BookController;
import ro.upt.exchange.s1.trading.api.OfferController;
import ro.upt.exchange.s1.trading.api.OrderController;
import ro.upt.exchange.s1.trading.api.TradeController;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps trading errors to RFC 9457 {@link ProblemDetail} bodies. Scoped to the trading
 * controllers so it never shadows another owner's advice.
 *
 * <pre>
 * 400 VALIDATION          bad input (also bean-validation failures, with "errors": {field: message})
 * 403 FORBIDDEN           role not allowed
 * 404 NOT_FOUND
 * 409 PRICE_CHANGED       buy-from-offer: version changed → "offer" has the current state
 * 409 NO_LONGER_AVAILABLE buy-from-offer: gone or too few shares
 * 409 INVALID_STATE       e.g. cancelling a filled order
 * 422 INSUFFICIENT_FUNDS / INSUFFICIENT_SHARES
 * </pre>
 */
@RestControllerAdvice(assignableTypes = {OrderController.class, OfferController.class, BookController.class, TradeController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TradingExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(TradingExceptionHandler.class);
    private static final String TYPE_PREFIX = "urn:exchange:trading:";

    @ExceptionHandler(TradingException.class)
    public ProblemDetail trading(TradingException ex) {
        return problem(ex.getStatus(), ex.getCode(), ex.getMessage(), ex.getProperties());
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ProblemDetail funds(InsufficientFundsException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS", ex.getMessage(),
                Map.of("requested", ex.getRequested()));
    }

    @ExceptionHandler(InsufficientSharesException.class)
    public ProblemDetail shares(InsufficientSharesException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_SHARES", ex.getMessage(),
                Map.of("requested", ex.getRequested(), "stockId", ex.getStockId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail beanValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe -> errors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors().forEach(ge -> errors.putIfAbsent(ge.getObjectName(), ge.getDefaultMessage()));
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION", "Request validation failed", Map.of("errors", errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail constraint(ConstraintViolationException ex) {
        Map<String, Object> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v -> errors.put(v.getPropertyPath().toString(), v.getMessage()));
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION", "Request validation failed", Map.of("errors", errors));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail illegalArgument(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION", ex.getMessage(), Map.of());
    }

    /** e.g. 401 from the AuthenticatedUser argument resolver: keep the status it carries. */
    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail responseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return problem(status, status.name(), ex.getReason() == null ? status.getReasonPhrase() : ex.getReason(), Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail unexpected(Exception ex) {
        log.error("Unhandled error in trading API", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL", "Unexpected error", Map.of());
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail, Map<String, Object> props) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create(TYPE_PREFIX + code.toLowerCase()));
        pd.setTitle(code);
        pd.setProperty("code", code);
        props.forEach(pd::setProperty);
        return pd;
    }
}
