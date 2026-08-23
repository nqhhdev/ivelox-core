package com.ivelox.core.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.ivelox.core.config.IveloxProperties;
import com.ivelox.core.telegram.TelegramClient;

@Service
public class OtpService {

    private final IveloxProperties props;
    private final TelegramClient telegram;
    private final Supplier<String> codeSupplier;
    private final AtomicReference<Challenge> challenge = new AtomicReference<>();
    private final AtomicReference<Instant> lastRequestAt = new AtomicReference<>(Instant.EPOCH);

    @Autowired
    public OtpService(IveloxProperties props, TelegramClient telegram) {
        this.props = props;
        this.telegram = telegram;
        this.codeSupplier = defaultCodeSupplier();
    }

    /** Test-only constructor. */
    public OtpService(IveloxProperties props, TelegramClient telegram, Supplier<String> codeSupplier) {
        this.props = props;
        this.telegram = telegram;
        this.codeSupplier = codeSupplier;
    }

    private static Supplier<String> defaultCodeSupplier() {
        SecureRandom random = new SecureRandom();
        return () -> String.format("%06d", random.nextInt(1_000_000));
    }

    public void requestOtp() {
        Instant now = Instant.now();
        Instant last = lastRequestAt.get();
        if (last.plusSeconds(props.otpMinIntervalSeconds()).isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "wait before requesting another otp");
        }
        String code = codeSupplier.get();
        Instant expiresAt = now.plusSeconds(props.otpTtlSeconds());
        challenge.set(new Challenge(sha256(code), expiresAt, false));
        lastRequestAt.set(now);
        telegram.sendMessage("iVelox login OTP: " + code + "\nExpires in " + props.otpTtlSeconds() + "s");
    }

    public void verifyOtp(String code) {
        if (code == null || !code.matches("\\d{6}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid code");
        }
        Challenge current = challenge.get();
        if (current == null || current.used()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no active otp");
        }
        if (Instant.now().isAfter(current.expiresAt())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "otp expired");
        }
        if (!constantTimeEquals(current.codeHash(), sha256(code))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid otp");
        }
        if (!challenge.compareAndSet(current, new Challenge(current.codeHash(), current.expiresAt(), true))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "otp already used");
        }
    }

    static String sha256(String value) {
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

    record Challenge(String codeHash, Instant expiresAt, boolean used) {
    }
}
