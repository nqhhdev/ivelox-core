package com.ivelox.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class IveloxPropertiesOriginsTest {

    @Test
    void splitsCommaSeparatedFrontendUrlsAndAddsWwwTwin() {
        var props = new IveloxProperties(
                "https://i-velox.app, https://ivelox-app.fly.dev,http://localhost:5173",
                "secret",
                3600,
                "bot",
                "1",
                300,
                30,
                true,
                "",
                "gemini"
        );
        List<String> origins = props.allowedFrontendOrigins();
        assertTrue(origins.contains("https://i-velox.app"));
        assertTrue(origins.contains("https://www.i-velox.app"));
        assertTrue(origins.contains("https://ivelox-app.fly.dev"));
        assertTrue(origins.contains("https://www.ivelox-app.fly.dev"));
        assertTrue(origins.contains("http://localhost:5173"));
        assertTrue(origins.contains("http://127.0.0.1:5173"));
        assertEquals(6, origins.size());
    }
}
