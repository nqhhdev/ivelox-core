package com.ivelox.core.health;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class FoodNameNormalizer {

    private static final Pattern COMBINING = Pattern.compile("\\p{M}+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private FoodNameNormalizer() {
    }

    /** Trim, lowercase, strip Vietnamese diacritics, collapse spaces. */
    public static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String out = s.trim().toLowerCase(Locale.ROOT).replace('đ', 'd');
        out = Normalizer.normalize(out, Normalizer.Form.NFD);
        out = COMBINING.matcher(out).replaceAll("");
        out = Normalizer.normalize(out, Normalizer.Form.NFC);
        return WHITESPACE.matcher(out).replaceAll(" ").trim();
    }
}
