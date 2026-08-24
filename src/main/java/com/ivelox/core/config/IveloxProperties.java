package com.ivelox.core.config;

import java.util.LinkedHashSet;
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
    /** Comma-separated FRONTEND_URL; auto-adds www/apex twins and local aliases. */
    public List<String> allowedFrontendOrigins() {
        if (frontendUrl == null || frontendUrl.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> origins = new LinkedHashSet<>();
        for (String raw : frontendUrl.split(",")) {
            String origin = raw.trim();
            if (origin.isEmpty()) {
                continue;
            }
            origins.add(origin);
            if (origin.startsWith("https://www.")) {
                origins.add("https://" + origin.substring("https://www.".length()));
            } else if (origin.startsWith("https://")) {
                String host = origin.substring("https://".length());
                if (!host.isEmpty() && !host.startsWith("www.") && !host.startsWith("localhost")) {
                    origins.add("https://www." + host);
                }
            }
            if (origin.startsWith("http://localhost") || origin.startsWith("http://127.0.0.1")) {
                origins.add("http://localhost:5173");
                origins.add("http://127.0.0.1:5173");
            }
        }
        return List.copyOf(origins);
    }
}
