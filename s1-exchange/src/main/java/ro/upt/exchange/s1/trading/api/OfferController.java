package ro.upt.exchange.s1.trading.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import ro.upt.exchange.s1.platform.AuthenticatedUser;
import ro.upt.exchange.s1.trading.dto.BuyOfferRequest;
import ro.upt.exchange.s1.trading.dto.BuyOfferResponse;
import ro.upt.exchange.s1.trading.dto.OfferResponse;
import ro.upt.exchange.s1.trading.service.BookService;
import ro.upt.exchange.s1.trading.service.OfferService;

import java.util.List;

/** contracts/s1/trading.yaml - Offers (open SELL orders). */
@RestController
@RequestMapping("/api/offers")
@Tag(name = "Offers", description = "Open SELL orders you can buy from at the price you see")
public class OfferController {

    private final BookService books;
    private final OfferService offers;

    public OfferController(BookService books, OfferService offers) {
        this.books = books;
        this.offers = offers;
    }

    @GetMapping
    @Operation(summary = "Offers of a stock, best price first",
            description = "Each offer carries totalPrice = price × remainingQty and the version to send back as seenVersion.")
    public List<OfferResponse> list(@RequestParam String symbol) {
        return books.offers(symbol);
    }

    @PostMapping("/{orderId}/buy")
    @Operation(summary = "Buy from a specific offer at the price you saw",
            description = "One conditional UPDATE: succeeds only if the offer is still open, has ≥ qty shares (case 1) "
                    + "and its version equals seenVersion (case 2). Otherwise 409 with reason PRICE_CHANGED or "
                    + "NO_LONGER_AVAILABLE and the offer's current state, so the client can confirm and retry.")
    @ApiResponse(responseCode = "200", description = "Trade executed and settled")
    @ApiResponse(responseCode = "409", description = "PRICE_CHANGED or NO_LONGER_AVAILABLE; body.offer is the current offer")
    @ApiResponse(responseCode = "422", description = "INSUFFICIENT_FUNDS")
    public BuyOfferResponse buy(AuthenticatedUser user, @PathVariable long orderId, @Valid @RequestBody BuyOfferRequest req) {
        return offers.buy(user, orderId, req);
    }
}
