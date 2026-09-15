package com.ivelox.core.finance;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class FinanceMoney {

    public record MoneyDto(
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("amount_minor") long amountMinor,
            @JsonProperty("currency") String currency
    ) {
        public static MoneyDto of(long minor, String currency) {
            var info = FinanceCatalog.find(currency)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_currency"));
            return new MoneyDto(toMajor(minor, info.minorDigits()), minor, info.code());
        }
    }

    private FinanceMoney() {
    }

    public static String requireCurrency(String code) {
        return FinanceCatalog.find(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_currency"))
                .code();
    }

    public static long toMinor(BigDecimal amount, String currency) {
        var info = FinanceCatalog.find(currency)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_currency"));
        if (amount == null || amount.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_amount");
        }
        if (amount.scale() > info.minorDigits()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_amount");
        }
        try {
            BigDecimal shifted = amount.movePointRight(info.minorDigits()).setScale(0, RoundingMode.UNNECESSARY);
            long minor = shifted.longValueExact();
            if (minor < 1 || minor > 999_999_999_999_999L) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_amount");
            }
            return minor;
        } catch (ArithmeticException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_amount");
        }
    }

    public static BigDecimal toMajor(long minor, int digits) {
        return BigDecimal.valueOf(minor).movePointLeft(digits);
    }
}
