package ro.upt.exchange.s1.trading;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import ro.upt.exchange.s1.domain.Order;
import ro.upt.exchange.s1.domain.OrderSide;
import ro.upt.exchange.s1.domain.OrderSource;
import ro.upt.exchange.s1.domain.OrderStatus;
import ro.upt.exchange.s1.domain.OrderType;
import ro.upt.exchange.s1.domain.Trade;
import ro.upt.exchange.s1.trading.dto.ModifyOrderRequest;
import ro.upt.exchange.s1.trading.dto.PlaceOrderRequest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Fast, container-free checks of the pure logic in trading/. */
class TradingUnitTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void limitNeedsPriceAndMarketMustNotHaveOne() {
        assertThat(validator.validate(new PlaceOrderRequest("ACME", OrderSide.BUY, OrderType.LIMIT, null, 10))).isNotEmpty();
        assertThat(validator.validate(new PlaceOrderRequest("ACME", OrderSide.BUY, OrderType.MARKET, BigDecimal.ONE, 10))).isNotEmpty();
        assertThat(validator.validate(new PlaceOrderRequest("ACME", OrderSide.BUY, OrderType.MARKET, null, 10))).isEmpty();
        assertThat(validator.validate(new PlaceOrderRequest("ACME", OrderSide.SELL, OrderType.LIMIT, new BigDecimal("1.2345"), 10))).isEmpty();
        assertThat(validator.validate(new PlaceOrderRequest("ACME", OrderSide.SELL, OrderType.LIMIT, new BigDecimal("1.23456"), 10))).isNotEmpty();
        assertThat(validator.validate(new PlaceOrderRequest("ACME", OrderSide.SELL, OrderType.LIMIT, BigDecimal.ONE, 0))).isNotEmpty();
    }

    @Test
    void modifyNeedsAtLeastOneField() {
        assertThat(validator.validate(new ModifyOrderRequest(null, null))).isNotEmpty();
        assertThat(validator.validate(new ModifyOrderRequest(BigDecimal.TEN, null))).isEmpty();
        assertThat(validator.validate(new ModifyOrderRequest(null, 5L))).isEmpty();
    }

    @Test
    void tradeTotalIsRoundedToCents() {
        assertThat(Trade.totalOf(3, new BigDecimal("10.3333"))).isEqualByComparingTo("31.00");
        assertThat(Trade.totalOf(3, new BigDecimal("10.3350"))).isEqualByComparingTo("31.01");
    }

    @Test
    void orderTotalPriceAndStatusHelpers() {
        Order o = new Order(1L, 1L, 1L, OrderSide.SELL, OrderType.LIMIT, new BigDecimal("12.5000"), 40, OrderSource.WEB);
        assertThat(o.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(o.totalPrice()).isEqualByComparingTo("500.00");
        assertThat(Order.statusFor(40, 40)).isEqualTo(OrderStatus.OPEN);
        assertThat(Order.statusFor(40, 10)).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(Order.statusFor(40, 0)).isEqualTo(OrderStatus.FILLED);
        assertThat(OrderStatus.CANCELLABLE).contains(OrderStatus.PENDING, OrderStatus.OPEN, OrderStatus.PARTIALLY_FILLED);
        assertThat(OrderStatus.FILLED.isTerminal()).isTrue();
    }
}
