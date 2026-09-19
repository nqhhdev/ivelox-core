package com.ivelox.core.modules.auth.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/** Pure domain model — no Spring, no JPA. A single-use, time-boxed OTP challenge. */
public record OtpChallenge(String codeHash, Instant expiresAt, boolean used) {

    public static OtpChallenge issue(String code, Instant expiresAt) {
        return new OtpChallenge(sha256(code), expiresAt, false);
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public boolean matches(String code) {
        return constantTimeEquals(codeHash, sha256(code));
    }

    public OtpChallenge markUsed() {
        return new OtpChallenge(codeHash, expiresAt, true);
    }

    private static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int r = 0;
        for (int i = 0; i < a.length(); i++) {
            r |= a.charAt(i) ^ b.charAt(i);
        }
        return r == 0;
    }
}
