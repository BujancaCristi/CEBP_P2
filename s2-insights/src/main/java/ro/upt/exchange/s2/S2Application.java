package ro.upt.exchange.s2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/** S2 - Insights: event-driven read model + statistics API + nightly report agent. Owner: Cristi. */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class S2Application {
    public static void main(String[] args) {
        SpringApplication.run(S2Application.class, args);
    }
}
