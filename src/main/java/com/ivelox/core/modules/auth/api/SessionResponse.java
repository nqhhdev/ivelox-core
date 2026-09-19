package com.ivelox.core.modules.auth.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.ivelox.core.modules.auth.application.port.in.VerifyOtpAndIssueSessionUseCase.Session;

public record SessionResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn
) {
    public static SessionResponse from(Session session) {
        return new SessionResponse(session.accessToken(), session.tokenType(), session.expiresInSeconds());
    }
}
