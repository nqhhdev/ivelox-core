package com.ivelox.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class IveloxPropertiesOriginsTest {

    @Test
    void splitsCommaSeparatedFrontendUrls() {
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
        assertEquals(
                List.of(
                        "https://i-velox.app",
                        "https://ivelox-app.fly.dev",
                        "http://localhost:5173"
                ),
                props.allowedFrontendOrigins()
        );
    }
}
