package com.ivelox.core;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "ivelox.jwt-secret=test-secret-key-at-least-32-bytes!!",
        "ivelox.telegram-bot-token=test",
        "ivelox.telegram-chat-id=1"
})
class IveloxCoreApplicationTests {

    @Test
    void contextLoads() {
    }
}
