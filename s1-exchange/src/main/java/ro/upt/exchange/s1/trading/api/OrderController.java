package ro.upt.exchange.s1.trading.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ro.upt.exchange.s1.platform.AuthenticatedUser;
import ro.upt.exchange.s1.trading.dto.ModifyOrderRequest;
import ro.upt.exchange.s1.trading.dto.OrderResponse;
import ro.upt.exchange.s1.trading.dto.PageResponse;
import ro.upt.exchange.s1.trading.dto.PlaceOrderRequest;
import ro.upt.exchange.s1.trading.service.OrderService;

/** contracts/s1/trading.yaml - Orders. */
@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders", description = "Place, modify, cancel and list your orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Place a LIMIT or MARKET order",
            description = "Reserves cash (LIMIT BUY) or shares (SELL) and queues the order for the matching engine. "
                    + "202 = accepted as PENDING; 422 = rejected at placement (insufficient funds/shares), the body is the REJECTED order.")
    @ApiResponse(responseCode = "202", description = "Accepted, status PENDING")
    @ApiResponse(responseCode = "422", description = "Rejected: insufficient funds or shares")
    public ResponseEntity<OrderResponse> place(AuthenticatedUser user, @Valid @RequestBody PlaceOrderRequest req) {
        OrderService.PlaceResult r = service.place(user, req);
        return ResponseEntity.status(r.rejected() ? HttpStatus.UNPROCESSABLE_ENTITY : HttpStatus.ACCEPTED).body(r.order());
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Change price and/or quantity of an open LIMIT order",
            description = "Queued to the matcher so it is serialised with fills; the response is the order as it is now. "
                    + "Bumps the order's version, so a buyer holding the old version gets 409 PRICE_CHANGED.")
    @ApiResponse(responseCode = "202", description = "Change queued")
    public ResponseEntity<OrderResponse> modify(AuthenticatedUser user, @PathVariable long id, @Valid @RequestBody ModifyOrderRequest req) {
        return ResponseEntity.accepted().body(service.modify(user, id, req));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel an order", description = "Queued to the matcher; the reservation is released when applied.")
    @ApiResponse(responseCode = "202", description = "Cancel queued")
    public ResponseEntity<OrderResponse> cancel(AuthenticatedUser user, @PathVariable long id) {
        return ResponseEntity.accepted().body(service.cancel(user, id));
    }

    @GetMapping("/mine")
    @Operation(summary = "My orders, newest first")
    public PageResponse<OrderResponse> mine(AuthenticatedUser user,
                                            @Parameter(description = "0-based") @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "50") int size) {
        return service.mine(user, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "One of my orders")
    public OrderResponse get(AuthenticatedUser user, @PathVariable long id) {
        return service.get(user, id);
    }
}
