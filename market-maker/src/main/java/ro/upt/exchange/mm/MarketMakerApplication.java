package ro.upt.exchange.mm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Rule-based market maker: keeps a bid and an ask on every enabled stock (Application_Description 3.3). */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(MarketMakerApplication.MmProperties.class)
public class MarketMakerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketMakerApplication.class, args);
    }

    @ConfigurationProperties("mm")
    public record MmProperties(String s1Url, String username, String password, boolean autostart, long tickMs) {}
}
