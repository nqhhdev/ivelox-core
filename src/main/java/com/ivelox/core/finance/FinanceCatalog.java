package com.ivelox.core.finance;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class FinanceCatalog {

    public record CurrencyInfo(String code, int minorDigits, String symbol, String name) {
    }

    private static final List<CurrencyInfo> ALL = List.of(
            new CurrencyInfo("VND", 0, "₫", "Vietnamese dong"),
            new CurrencyInfo("USD", 2, "$", "US dollar"),
            new CurrencyInfo("EUR", 2, "€", "Euro"),
            new CurrencyInfo("GBP", 2, "£", "Pound sterling"),
            new CurrencyInfo("JPY", 0, "¥", "Japanese yen"),
            new CurrencyInfo("KRW", 0, "₩", "South Korean won"),
            new CurrencyInfo("SGD", 2, "S$", "Singapore dollar"),
            new CurrencyInfo("AUD", 2, "A$", "Australian dollar"),
            new CurrencyInfo("CAD", 2, "C$", "Canadian dollar"),
            new CurrencyInfo("CNY", 2, "¥", "Chinese yuan"),
            new CurrencyInfo("THB", 2, "฿", "Thai baht"),
            new CurrencyInfo("MYR", 2, "RM", "Malaysian ringgit"),
            new CurrencyInfo("IDR", 0, "Rp", "Indonesian rupiah"),
            new CurrencyInfo("PHP", 2, "₱", "Philippine peso"),
            new CurrencyInfo("INR", 2, "₹", "Indian rupee"),
            new CurrencyInfo("CHF", 2, "CHF", "Swiss franc"),
            new CurrencyInfo("HKD", 2, "HK$", "Hong Kong dollar"),
            new CurrencyInfo("TWD", 0, "NT$", "New Taiwan dollar"),
            new CurrencyInfo("NZD", 2, "NZ$", "New Zealand dollar")
    );

    private static final Map<String, CurrencyInfo> BY_CODE = ALL.stream()
            .collect(Collectors.toUnmodifiableMap(c -> c.code(), c -> c));

    public static final List<String> KINDS = List.of(
            "income", "daily", "fixed", "saving", "loan_payment", "loan_disbursement"
    );

    public static final List<String> CATEGORIES = List.of(
            "food", "coffee", "transport", "grocery", "health", "fun", "other"
    );

    private FinanceCatalog() {
    }

    public static List<CurrencyInfo> currencies() {
        return ALL;
    }

    public static Optional<CurrencyInfo> find(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_CODE.get(code.trim().toUpperCase(Locale.ROOT)));
    }

    public static boolean isKind(String kind) {
        return kind != null && KINDS.contains(kind);
    }

    public static boolean isCategory(String category) {
        return category != null && CATEGORIES.contains(category);
    }
}
