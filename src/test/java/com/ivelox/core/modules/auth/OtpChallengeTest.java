package com.ivelox.core.modules.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.ivelox.core.modules.auth.domain.model.OtpChallenge;

class OtpChallengeTest {

    @Test
    void matchesOnlyTheIssuedCode() {
        var challenge = OtpChallenge.issue("123456", Instant.now().plusSeconds(60));
        assertTrue(challenge.matches("123456"));
        assertFalse(challenge.matches("654321"));
    }

    @Test
    void isExpiredAfterExpiryInstant() {
        var challenge = OtpChallenge.issue("123456", Instant.now().minusSeconds(1));
        assertTrue(challenge.isExpired(Instant.now()));
    }

    @Test
    void markUsedPreservesHashAndExpiryButFlipsUsed() {
        var expiresAt = Instant.now().plusSeconds(60);
        var challenge = OtpChallenge.issue("123456", expiresAt);
        var used = challenge.markUsed();

        assertFalse(challenge.used());
        assertTrue(used.used());
        assertTrue(used.matches("123456"));
    }
}
