package ro.upt.exchange.s1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * S1 - Exchange core. The only writer of the {@code exchange} database.
 *
 * <p>Package ownership (see Work_Split.pdf):
 * <ul>
 *   <li>{@code domain/}, {@code trading/}, {@code config/} - Patric</li>
 *   <li>{@code platform/} - Cezar</li>
 *   <li>{@code ops/}, {@code realtime/} - Paul</li>
 * </ul>
 */
@SpringBootApplication
@EnableKafka
@EnableScheduling
@ConfigurationPropertiesScan
public class S1Application {

    public static void main(String[] args) {
        SpringApplication.run(S1Application.class, args);
    }
}
