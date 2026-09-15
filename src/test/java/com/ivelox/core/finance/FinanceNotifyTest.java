package com.ivelox.core.finance;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.ivelox.core.telegram.TelegramClient;

@SpringBootTest
@ActiveProfiles("test")
@Import(FinanceTestSupport.class)
@Transactional
class FinanceNotifyTest {

    @Autowired
    private FinanceRepository repo;

    @Autowired
    private FinanceNotifier notifier;

    @Autowired
    private TelegramClient telegram;

    @BeforeEach
    void resetMock() {
        reset(telegram);
    }

    /** todayLeft negative, remainingMonth non-negative: triggers over_slot only. */
    private static FinanceMath.MonthSlice overSlotOnlySlice() {
        return new FinanceMath.MonthSlice(0, 0, 0, 0, 0, 0, 0, 100, -50, -50, 50);
    }

    @Test
    void vndThenUsdOverSlotSendsTwoSeparateAlerts() {
        var settings = repo.settings("owner");

        notifier.afterPost(overSlotOnlySlice(), "VND", "daily", settings);
        notifier.afterPost(overSlotOnlySlice(), "USD", "daily", settings);

        verify(telegram, times(2)).sendMessage(anyString());
    }

    @Test
    void secondVndOverSlotSameDayIsDeduped() {
        var settings = repo.settings("owner");

        notifier.afterPost(overSlotOnlySlice(), "VND", "daily", settings);
        notifier.afterPost(overSlotOnlySlice(), "VND", "daily", settings);

        verify(telegram, times(1)).sendMessage(anyString());
    }

    @Test
    void digestSkippedWhenDisabled() {
        var settings = new FinanceModels.Settings("owner", "VND", 21, false, true, true);
        var slice = new FinanceMath.MonthSlice(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

        notifier.sendDigest(settings, slice, "VND", List.of());

        verify(telegram, times(0)).sendMessage(anyString());
    }

    @Test
    void overMonthAlertDedupedByMonth() {
        var settings = repo.settings("owner");
        // todayLeft non-negative, remainingMonth negative: triggers over_month only.
        var slice = new FinanceMath.MonthSlice(-10, 0, 0, 0, 0, 0, 0, 0, 0, 0, -10);

        notifier.afterPost(slice, "VND", "fixed", settings);
        notifier.afterPost(slice, "VND", "fixed", settings);

        verify(telegram, times(1)).sendMessage(anyString());
    }
}
