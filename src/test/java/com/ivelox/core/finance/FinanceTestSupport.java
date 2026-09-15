package com.ivelox.core.finance;

import static org.mockito.Mockito.mock;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.ivelox.core.telegram.TelegramClient;

@TestConfiguration
public class FinanceTestSupport {

    @Bean
    @Primary
    public TelegramClient telegramClient() {
        return mock(TelegramClient.class);
    }
}
