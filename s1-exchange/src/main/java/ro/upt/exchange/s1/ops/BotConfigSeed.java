package ro.upt.exchange.s1.ops;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import ro.upt.exchange.s1.domain.*;

import java.math.BigDecimal;

/**
 * Seeds default bot configurations for configured stocks so the market maker bot can start quoting.
 * <b>Owner: Paul.</b>
 */
@Configuration
public class BotConfigSeed {

    private static final Logger log = LoggerFactory.getLogger(BotConfigSeed.class);

    @Bean
    @Profile({"local", "demo"})
    @Order(100)
    CommandLineRunner seedBotConfigs(BotConfigRepository botConfigRepository,
                                     UserRepository userRepository,
                                     StockRepository stockRepository,
                                     AccountRepository accountRepository,
                                     PortfolioRepository portfolioRepository) {
        return args -> {
            userRepository.findByUsername("market-maker").ifPresent(bot -> {
                Account botAccount = accountRepository.findByUserId(bot.getId()).orElse(null);

                for (Stock stock : stockRepository.findAll()) {
                    if (botConfigRepository.findByBotUserIdAndStockId(bot.getId(), stock.getId()).isEmpty()) {
                        BotConfig cfg = new BotConfig(
                                bot.getId(),
                                stock.getId(),
                                100, // 100 bps (1.00%) spread
                                50L, // quote 50 shares
                                2000L // max 2000 shares inventory
                        );
                        cfg.setEnabled(true);
                        cfg.setSkewFactor(new BigDecimal("0.500"));
                        cfg.setRefreshMs(2000);
                        botConfigRepository.save(cfg);
                        log.info("Seeded bot configuration for stock {} (symbol={})", stock.getId(), stock.getSymbol());
                    }

                    if (botAccount != null && portfolioRepository.findByAccountIdAndStockId(botAccount.getId(), stock.getId()).isEmpty()) {
                        Portfolio botPosition = new Portfolio(botAccount.getId(), stock.getId());
                        botPosition.setQuantity(500);
                        botPosition.setAvgCost(stock.getValue());
                        portfolioRepository.save(botPosition);
                        log.info("Seeded initial inventory of 500 shares for bot on stock {}", stock.getSymbol());
                    }
                }
            });
        };
    }
}
