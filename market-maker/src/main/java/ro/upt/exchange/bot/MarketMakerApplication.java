package ro.upt.exchange.bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import ro.upt.exchange.bot.config.BotProperties;

@SpringBootApplication
@EnableConfigurationProperties(BotProperties.class)
public class MarketMakerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketMakerApplication.class, args);
    }
}
