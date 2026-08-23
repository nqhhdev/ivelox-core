package com.ivelox.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

class DatabaseUrlEnvironmentPostProcessorTest {

    @Test
    void parsesPostgresUrl() throws Exception {
        Map<String, Object> props = DatabaseUrlEnvironmentPostProcessor.parse(
                "postgres://myuser:s%40cret@db.example.com:6543/postgres"
        );
        assertEquals("jdbc:postgresql://db.example.com:6543/postgres", props.get("spring.datasource.url"));
        assertEquals("myuser", props.get("spring.datasource.username"));
        assertEquals("s@cret", props.get("spring.datasource.password"));
        assertEquals("org.postgresql.Driver", props.get("spring.datasource.driver-class-name"));
    }
}
