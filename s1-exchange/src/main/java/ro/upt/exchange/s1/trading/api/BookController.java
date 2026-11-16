package ro.upt.exchange.s1.trading.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ro.upt.exchange.s1.events.BookSnapshot;
import ro.upt.exchange.s1.trading.service.BookService;

/** contracts/s1/trading.yaml - Order book. */
@RestController
@Tag(name = "Book", description = "Order book grouped by price level")
public class BookController {

    private final BookService books;

    public BookController(BookService books) {
        this.books = books;
    }

    @GetMapping("/api/stocks/{symbol}/book")
    @Operation(summary = "Order book of a stock",
            description = "Bids (highest first) and asks (lowest first) aggregated per price level. "
                    + "The same shape is pushed on /topic/book/{symbol}.")
    public BookSnapshot book(@PathVariable String symbol, @RequestParam(defaultValue = "10") int depth) {
        return books.snapshot(symbol, depth);
    }
}
