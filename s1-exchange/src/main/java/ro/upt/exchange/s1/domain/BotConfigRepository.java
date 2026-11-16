package ro.upt.exchange.s1.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BotConfigRepository extends JpaRepository<BotConfig, Long> {
    List<BotConfig> findByBotUserIdOrderByStockId(Long botUserId);
    Optional<BotConfig> findByBotUserIdAndStockId(Long botUserId, Long stockId);
}
