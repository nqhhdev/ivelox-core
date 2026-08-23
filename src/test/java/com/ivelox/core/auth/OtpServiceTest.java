package com.ivelox.core.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.ivelox.core.config.IveloxProperties;
import com.ivelox.core.telegram.TelegramClient;

class OtpServiceTest {

    private OtpService otpService;

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
                "gemini-2.0-flash"
        );
        TelegramClient telegram = mock(TelegramClient.class);
        doNothing().when(telegram).sendMessage(anyString());
        otpService = new OtpService(props, telegram, () -> "123456");
    }

    @Test
    void verifyRejectsWithoutRequest() {
        assertThrows(ResponseStatusException.class, () -> otpService.verifyOtp("123456"));
    }

    @Test
    void verifyAcceptsMatchingCodeOnce() {
        otpService.requestOtp();
        assertDoesNotThrow(() -> otpService.verifyOtp("123456"));
        assertThrows(ResponseStatusException.class, () -> otpService.verifyOtp("123456"));
    }

    @Test
    void verifyRejectsWrongCode() {
        otpService.requestOtp();
        assertThrows(ResponseStatusException.class, () -> otpService.verifyOtp("000000"));
    }

    @Test
    void hashIsDeterministic() {
        String a = OtpService.sha256("123456");
        String b = OtpService.sha256("123456");
        assertEquals(a, b);
        assertNotEquals(a, OtpService.sha256("654321"));
    }
}
