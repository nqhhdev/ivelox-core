package com.ivelox.core.config;

import java.util.Arrays;
import java.util.List;

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
        String geminiApiKey,
        String geminiModel
) {
    /** Comma-separated FRONTEND_URL values (custom domain + Fly app + local). */
    public List<String> allowedFrontendOrigins() {
        if (frontendUrl == null || frontendUrl.isBlank()) {
            return List.of();
        }
        return Arrays.stream(frontendUrl.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }
}
