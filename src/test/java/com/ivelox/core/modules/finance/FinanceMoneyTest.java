package com.ivelox.core.modules.finance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.ivelox.core.modules.finance.domain.model.FinanceMoney;

class FinanceMoneyTest {

    @Test
    void vndWholeAmountMapsToSameMinorValue() {
        assertEquals(20000L, FinanceMoney.toMinor(new BigDecimal("20000"), "VND"));
    }

    @Test
    void usdTwoDecimalsMapToCents() {
        assertEquals(450L, FinanceMoney.toMinor(new BigDecimal("4.50"), "USD"));
    }

    @Test
    void usdOneDecimalMapsToCents() {
        assertEquals(450L, FinanceMoney.toMinor(new BigDecimal("4.5"), "USD"));
    }

    @Test
    void usdThreeDecimalsRejected() {
        assertThrows(ResponseStatusException.class,
                () -> FinanceMoney.toMinor(new BigDecimal("4.123"), "USD"));
    }

    @Test
    void jpyWithFractionRejected() {
        assertThrows(ResponseStatusException.class,
                () -> FinanceMoney.toMinor(new BigDecimal("100.5"), "JPY"));
    }

    @Test
    void nonPositiveAmountRejected() {
        assertThrows(ResponseStatusException.class,
                () -> FinanceMoney.toMinor(new BigDecimal("0"), "USD"));
        assertThrows(ResponseStatusException.class,
                () -> FinanceMoney.toMinor(new BigDecimal("-1"), "USD"));
    }

    @Test
    void unknownCurrencyRejected() {
        assertThrows(ResponseStatusException.class,
                () -> FinanceMoney.toMinor(new BigDecimal("10"), "ZZZ"));
        assertThrows(ResponseStatusException.class, () -> FinanceMoney.requireCurrency("ZZZ"));
    }

    @Test
    void moneyDtoFormatsMajorFromMinor() {
        var dto = FinanceMoney.MoneyDto.of(450L, "USD");
        assertEquals(new BigDecimal("4.50"), dto.amount());
        assertEquals(450L, dto.amountMinor());
        assertEquals("USD", dto.currency());

        var vnd = FinanceMoney.MoneyDto.of(45000L, "VND");
        assertEquals(new BigDecimal("45000"), vnd.amount());
    }
}
