package com.ivelox.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ivelox")
public record IveloxProperties(
        String frontendUrl,
        String jwtSecret,
        long jwtTtlSeconds,
        String telegramBotToken,
        String telegramChatId,
        long otpTtlSeconds,
        long otpMinIntervalSeconds,
        boolean healthEnabled,
        String geminiApiKey
) {
}
