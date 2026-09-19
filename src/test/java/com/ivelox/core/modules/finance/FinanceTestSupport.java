package com.ivelox.core.modules.finance;

import static org.mockito.Mockito.mock;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.ivelox.core.common.notification.NotificationSenderPort;

@TestConfiguration
public class FinanceTestSupport {

    @Bean
    @Primary
    public NotificationSenderPort notificationSenderPort() {
        return mock(NotificationSenderPort.class);
    }
}
