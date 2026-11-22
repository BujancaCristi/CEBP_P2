package ro.upt.exchange.s1.platform;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ro.upt.exchange.s1.domain.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api")
public class PlatformController {
    private final AuthService auth;
    private final AccountRepository accounts;
    private final PortfolioRepository portfolios;
    private final UserRepository users;
    private final CompanyRepository companies;
    private final StockRepository stocks;

    public PlatformController(AuthService auth, AccountRepository accounts, PortfolioRepository portfolios,
                              UserRepository users, CompanyRepository companies, StockRepository stocks) {
        this.auth = auth;
        this.accounts = accounts;
        this.portfolios = portfolios;
        this.users = users;
        this.companies = companies;
        this.stocks = stocks;
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthService.AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return auth.register(request.username(), request.email(), request.password());
    }

    @PostMapping("/auth/login")
    public AuthService.AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request.username(), request.password());
    }

    @GetMapping("/me")
    public MeResponse me(AuthenticatedUser user) {
        User entity = users.findById(user.userId()).orElseThrow();
        Account account = accounts.findById(user.accountId()).orElseThrow();
        return new MeResponse(new AuthService.UserSummary(entity.getId(), entity.getUsername(), entity.getEmail(),
                entity.getRole().name()), account(account));
    }

    @GetMapping("/companies")
    public List<Company> companies() {
        return companies.findAll();
    }

    @GetMapping("/stocks")
    public List<Stock> stocks() {
        return stocks.findByActiveTrueOrderBySymbol();
    }

    @GetMapping("/portfolio")
    public List<PositionResponse> portfolio(AuthenticatedUser user) {
        return portfolios.findByAccountId(user.accountId()).stream().map(position -> {
            Stock stock = stocks.findById(position.getStockId()).orElseThrow();
            return new PositionResponse(stock.getSymbol(), position.getQuantity(), position.getReservedQty(),
                    position.availableQty(), position.getAvgCost(), stock.getValue());
        }).toList();
    }

    private static AccountResponse account(Account account) {
        return new AccountResponse(account.getId(), account.getCurrency(), account.getCashBalance(),
                account.getReservedCash(), account.availableCash());
    }

    public record RegisterRequest(@NotBlank @Size(max = 50) String username,
                                  @NotBlank @Email String email,
                                  @NotBlank @Size(min = 8, max = 72) String password) {}
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record MeResponse(AuthService.UserSummary user, AccountResponse account) {}
    public record AccountResponse(long id, String currency, BigDecimal balance, BigDecimal reserved,
                                  BigDecimal available) {}
    public record PositionResponse(String symbol, long quantity, long reserved, long available,
                                   BigDecimal averageCost, BigDecimal lastPrice) {}
}
