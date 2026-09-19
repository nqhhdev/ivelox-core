package com.ivelox.core.modules.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.ivelox.core.common.notification.NotificationSenderPort;
import com.ivelox.core.config.IveloxProperties;
import com.ivelox.core.modules.auth.application.port.out.TokenIssuerPort;
import com.ivelox.core.modules.auth.application.service.AuthService;

class AuthServiceTest {

    private AuthService authService;
    private TokenIssuerPort tokenIssuer;

    @BeforeEach
    void setUp() {
        IveloxProperties props = new IveloxProperties(
                "http://localhost:5173",
                "test-secret-key-at-least-32-bytes!!",
                3600,
                "token",
                "123",
                300,
                0,
                true,
                "",
                "gemini-2.5-flash",
                true,
                true
        );
        NotificationSenderPort telegram = mock(NotificationSenderPort.class);
        doNothing().when(telegram).sendMessage(anyString());
        tokenIssuer = mock(TokenIssuerPort.class);
        when(tokenIssuer.issueOwnerToken()).thenReturn("fake.jwt.token");
        when(tokenIssuer.ttlSeconds()).thenReturn(3600L);
        authService = new AuthService(props, telegram, tokenIssuer, () -> "123456");
    }

    @Test
    void verifyRejectsWithoutRequest() {
        assertThrows(ResponseStatusException.class, () -> authService.verifyOtpAndIssueSession("123456"));
    }

    @Test
    void verifyAcceptsMatchingCodeOnceAndIssuesSession() {
        authService.requestOtp();
        var session = assertDoesNotThrow(() -> authService.verifyOtpAndIssueSession("123456"));
        assertEquals("fake.jwt.token", session.accessToken());
        assertEquals("Bearer", session.tokenType());
        assertEquals(3600L, session.expiresInSeconds());

        assertThrows(ResponseStatusException.class, () -> authService.verifyOtpAndIssueSession("123456"));
    }

    @Test
    void verifyRejectsWrongCode() {
        authService.requestOtp();
        assertThrows(ResponseStatusException.class, () -> authService.verifyOtpAndIssueSession("000000"));
    }
}
