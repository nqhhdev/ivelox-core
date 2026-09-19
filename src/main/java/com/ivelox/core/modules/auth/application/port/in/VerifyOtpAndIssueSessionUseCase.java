package com.ivelox.core.modules.auth.application.port.in;

public interface VerifyOtpAndIssueSessionUseCase {

    /** Verifies the OTP code and, on success, issues a signed session token. */
    Session verifyOtpAndIssueSession(String code);

    record Session(String accessToken, String tokenType, long expiresInSeconds) {
    }
}
