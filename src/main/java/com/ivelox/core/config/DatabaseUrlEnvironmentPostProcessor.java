package com.ivelox.core.config;

import java.net.URI;
import java.net.URISyntaxException;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps Heroku/Fly-style {@code DATABASE_URL} (postgres:// or postgresql://)
 * onto Spring datasource properties when {@code SPRING_DATASOURCE_URL} is unset.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String existing = environment.getProperty("SPRING_DATASOURCE_URL");
        if (existing != null && !existing.isBlank()) {
            return;
        }
        String raw = environment.getProperty("DATABASE_URL");
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            Map<String, Object> props = parse(raw);
            // Also set env-style keys so application.yml ${SPRING_DATASOURCE_URL:...} resolves.
            Object jdbc = props.get("spring.datasource.url");
            if (jdbc != null) {
                props.put("SPRING_DATASOURCE_URL", jdbc);
            }
            Object user = props.get("spring.datasource.username");
            if (user != null) {
                props.put("SPRING_DATASOURCE_USERNAME", user);
            }
            Object pass = props.get("spring.datasource.password");
            if (pass != null) {
                props.put("SPRING_DATASOURCE_PASSWORD", pass);
            }
            Object driver = props.get("spring.datasource.driver-class-name");
            if (driver != null) {
                props.put("SPRING_DATASOURCE_DRIVER", driver);
            }
            environment.getPropertySources().addFirst(new MapPropertySource("databaseUrl", props));
        } catch (Exception e) {
            throw new IllegalStateException("Invalid DATABASE_URL: " + e.getMessage(), e);
        }
    }

    static Map<String, Object> parse(String raw) throws URISyntaxException {
        String normalized = raw.trim();
        if (normalized.startsWith("postgres://")) {
            normalized = "postgresql://" + normalized.substring("postgres://".length());
        }
        if (!normalized.startsWith("postgresql://") && !normalized.startsWith("jdbc:postgresql://")) {
            throw new IllegalArgumentException("expected postgres://, postgresql://, or jdbc:postgresql://");
        }
        if (normalized.startsWith("jdbc:postgresql://")) {
            Map<String, Object> props = new HashMap<>();
            props.put("spring.datasource.url", normalized);
            props.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
            return props;
        }

        URI uri = new URI(normalized);
        String userInfo = uri.getUserInfo();
        String username = null;
        String password = "";
        if (userInfo != null) {
            int colon = userInfo.indexOf(':');
            if (colon >= 0) {
                username = decode(userInfo.substring(0, colon));
                password = decode(userInfo.substring(colon + 1));
            } else {
                username = decode(userInfo);
            }
        }
        String host = uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : 5432;
        String path = uri.getPath() == null || uri.getPath().isBlank() ? "/postgres" : uri.getPath();
        String jdbc = "jdbc:postgresql://" + host + ":" + port + path;
        if (uri.getQuery() != null && !uri.getQuery().isBlank()) {
            jdbc = jdbc + "?" + uri.getQuery();
        }

        Map<String, Object> props = new HashMap<>();
        props.put("spring.datasource.url", jdbc);
        props.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
        if (username != null) {
            props.put("spring.datasource.username", username);
        }
        props.put("spring.datasource.password", password);
        return props;
    }

    private static String decode(String s) {
        return java.net.URLDecoder.decode(s, java.nio.charset.StandardCharsets.UTF_8);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
