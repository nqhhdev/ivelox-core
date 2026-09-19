package com.ivelox.core.modules.auth.application.service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.ivelox.core.common.notification.NotificationSenderPort;
import com.ivelox.core.config.IveloxProperties;
import com.ivelox.core.modules.auth.application.port.in.RequestOtpUseCase;
import com.ivelox.core.modules.auth.application.port.in.VerifyOtpAndIssueSessionUseCase;
import com.ivelox.core.modules.auth.application.port.out.TokenIssuerPort;
import com.ivelox.core.modules.auth.domain.model.OtpChallenge;

@Service
public class AuthService implements RequestOtpUseCase, VerifyOtpAndIssueSessionUseCase {

    private final IveloxProperties props;
    private final NotificationSenderPort notificationSender;
    private final TokenIssuerPort tokenIssuer;
    private final Supplier<String> codeSupplier;
    private final AtomicReference<OtpChallenge> challenge = new AtomicReference<>();
    private final AtomicReference<Instant> lastRequestAt = new AtomicReference<>(Instant.EPOCH);

    @Autowired
    public AuthService(
            IveloxProperties props,
            NotificationSenderPort notificationSender,
            TokenIssuerPort tokenIssuer
    ) {
        this(props, notificationSender, tokenIssuer, defaultCodeSupplier());
    }

    /** Test-only constructor. */
    public AuthService(
            IveloxProperties props,
            NotificationSenderPort notificationSender,
            TokenIssuerPort tokenIssuer,
            Supplier<String> codeSupplier
    ) {
        this.props = props;
        this.notificationSender = notificationSender;
        this.tokenIssuer = tokenIssuer;
        this.codeSupplier = codeSupplier;
    }

    private static Supplier<String> defaultCodeSupplier() {
        java.security.SecureRandom random = new java.security.SecureRandom();
        return () -> String.format("%06d", random.nextInt(1_000_000));
    }

    @Override
    public void requestOtp() {
        Instant now = Instant.now();
        Instant last = lastRequestAt.get();
        if (last.plusSeconds(props.otpMinIntervalSeconds()).isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "wait before requesting another otp");
        }
        String code = codeSupplier.get();
        Instant expiresAt = now.plusSeconds(props.otpTtlSeconds());
        challenge.set(OtpChallenge.issue(code, expiresAt));
        lastRequestAt.set(now);
        notificationSender.sendMessage("iVelox login OTP: " + code + "\nExpires in " + props.otpTtlSeconds() + "s");
    }

    @Override
    public Session verifyOtpAndIssueSession(String code) {
        if (code == null || !code.matches("\\d{6}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid code");
        }
        OtpChallenge current = challenge.get();
        if (current == null || current.used()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no active otp");
        }
        if (current.isExpired(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "otp expired");
        }
        if (!current.matches(code)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid otp");
        }
        if (!challenge.compareAndSet(current, current.markUsed())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "otp already used");
        }
        String token = tokenIssuer.issueOwnerToken();
        return new Session(token, "Bearer", tokenIssuer.ttlSeconds());
    }
}
