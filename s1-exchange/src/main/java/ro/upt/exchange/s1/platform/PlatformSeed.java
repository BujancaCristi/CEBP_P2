package ro.upt.exchange.s1.platform;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import ro.upt.exchange.s1.domain.*;
import ro.upt.exchange.s1.ops.BotConfigService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Demo seed (profile {@code demo} or {@code local}), as in Application_Description section 9:
 * 6 companies and stocks, an admin, a market maker with settings for every stock, and 3 traders.
 * The market maker and the traders start with shares, so both sides of the book can trade.
 *
 * <p>Logins: admin / admin-password, market-maker / bot-password, trader / trader-password,
 * trader2 / trader-password, trader3 / trader-password.
 */
@Configuration
public class PlatformSeed {

    record SeedStock(String symbol, String company, String sector, String price) {}

    static final List<SeedStock> STOCKS = List.of(
            new SeedStock("ACME", "ACME Technologies", "Technology", "124.80"),
            new SeedStock("NOVA", "Nova Energy", "Energy", "68.35"),
            new SeedStock("ORBX", "Orbex Logistics", "Industrials", "43.70"),
            new SeedStock("VLT", "Violet Telecom", "Telecom", "91.10"),
            new SeedStock("BRSV", "Banat River Bank", "Financials", "27.45"),
            new SeedStock("TIMS", "Timis Foods", "Consumer", "15.20"));

    static final long BOT_SHARES = 5_000;
    static final long TRADER_SHARES = 300;
    static final BigDecimal BOT_CASH = new BigDecimal("2000000.00");

    @Bean
    @Profile({"local", "demo"})
    CommandLineRunner seedPlatform(UserRepository users, AccountRepository accounts, CompanyRepository companies,
                                   StockRepository stocks, PortfolioRepository portfolios, BotConfigRepository botConfigs,
                                   BotConfigService botConfigService, PasswordEncoder passwords,
                                   PlatformProperties properties) {
        return args -> {
            if (users.count() > 0) return;
            User admin = users.save(new User("admin", "admin@example.com", passwords.encode("admin-password"), Role.ADMIN));
            User bot = users.save(new User("market-maker", "bot@example.com", passwords.encode("bot-password"), Role.MARKET_MAKER));
            accounts.save(new Account(admin.getId(), properties.startingCash()));
            Account botAccount = accounts.save(new Account(bot.getId(), BOT_CASH));

            List<Account> traderAccounts = new ArrayList<>();
            for (String name : List.of("trader", "trader2", "trader3")) {
                User trader = users.save(new User(name, name + "@example.com", passwords.encode("trader-password"), Role.TRADER));
                traderAccounts.add(accounts.save(new Account(trader.getId(), properties.startingCash())));
            }

            for (SeedStock s : STOCKS) {
                Company company = companies.save(new Company(s.company(), s.sector(), "RO", s.company() + " (demo)"));
                BigDecimal price = new BigDecimal(s.price());
                Stock stock = stocks.save(new Stock(company.getId(), s.symbol(), price, 1_000_000));

                position(portfolios, botAccount.getId(), stock.getId(), BOT_SHARES, price);
                for (Account t : traderAccounts) {
                    position(portfolios, t.getId(), stock.getId(), TRADER_SHARES, price);
                }
                // spread 50 bps, 20 shares per quote, inventory cap 10 000 shares
                botConfigs.save(new BotConfig(bot.getId(), stock.getId(), 50, 20, 10_000));
            }
            // so S2 (and the nightly report) know the bot's settings from the first minute
            botConfigService.publishAll("seed");
        };
    }

    private static void position(PortfolioRepository portfolios, long accountId, long stockId, long qty, BigDecimal cost) {
        Portfolio p = new Portfolio(accountId, stockId);
        p.setQuantity(qty);
        p.setAvgCost(cost);
        portfolios.save(p);
    }
}
