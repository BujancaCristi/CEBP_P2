package ro.upt.exchange.s1.platform;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import ro.upt.exchange.s1.domain.*;

import java.math.BigDecimal;

@Configuration
public class PlatformSeed {
    @Bean
    @Profile({"local", "demo"})
    CommandLineRunner seedPlatform(UserRepository users, AccountRepository accounts, CompanyRepository companies,
                                   StockRepository stocks, PortfolioRepository portfolios,
                                   PasswordEncoder passwords, PlatformProperties properties) {
        return args -> seed(users, accounts, companies, stocks, portfolios, passwords, properties);
    }

    @Transactional
    void seed(UserRepository users, AccountRepository accounts, CompanyRepository companies, StockRepository stocks,
              PortfolioRepository portfolios, PasswordEncoder passwords, PlatformProperties properties) {
        if (users.count() > 0) return;
        User admin = users.save(new User("admin", "admin@example.com", passwords.encode("admin-password"), Role.ADMIN));
        User trader = users.save(new User("trader", "trader@example.com", passwords.encode("trader-password"), Role.TRADER));
        User bot = users.save(new User("market-maker", "bot@example.com", passwords.encode("bot-password"), Role.MARKET_MAKER));
        accounts.save(new Account(admin.getId(), properties.startingCash()));
        Account traderAccount = accounts.save(new Account(trader.getId(), properties.startingCash()));
        accounts.save(new Account(bot.getId(), properties.startingCash()));
        Company company = companies.save(new Company("Demo Technologies", "Technology", "RO", "Seed company"));
        Stock stock = stocks.save(new Stock(company.getId(), "DEMO", new BigDecimal("100.00"), 1_000_000));
        Portfolio position = new Portfolio(traderAccount.getId(), stock.getId());
        position.setQuantity(1_000);
        position.setAvgCost(new BigDecimal("90.00"));
        portfolios.save(position);
    }
}
