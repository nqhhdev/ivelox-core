package com.ivelox.core.finance;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ivelox.core.health.CivilDay;
import com.ivelox.core.telegram.TelegramClient;

@Component
public class FinanceNotifier {

    private static final Logger log = LoggerFactory.getLogger(FinanceNotifier.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");

    private final FinanceRepository repo;
    private final TelegramClient telegram;

    public FinanceNotifier(FinanceRepository repo, TelegramClient telegram) {
        this.repo = repo;
        this.telegram = telegram;
    }

    public void afterPost(FinanceMath.MonthSlice slice, String currency, String kind, FinanceModels.Settings settings) {
        if (settings.overSlotAlert() && "daily".equals(kind) && slice.todayLeft() < 0) {
            trySend("over_slot", CivilDay.todayIct(), currency,
                    "Money " + CivilDay.todayIct().format(DAY) + " " + currency
                            + "\nover slot: spent "
                            + format(slice.dailyToday(), currency)
                            + " / slot " + format(slice.todaySlot(), currency));
        }
        if (settings.overMonthAlert() && slice.remainingMonth() < 0) {
            LocalDate monthStart = YearMonth.from(CivilDay.todayIct()).atDay(1);
            trySend("over_month", monthStart, currency,
                    "Money " + currency + "\nover month: remaining "
                            + format(slice.remainingMonth(), currency));
        }
    }

    public void sendDigest(FinanceModels.Settings settings, FinanceMath.MonthSlice slice,
                           String currency, List<FinanceModels.Upcoming> upcoming) {
        if (!settings.digestEnabled()) {
            return;
        }
        LocalDate today = CivilDay.todayIct();
        StringBuilder sb = new StringBuilder();
        sb.append("Money ").append(today.format(DAY)).append(' ').append(currency).append('\n');
        if (slice.todayLeft() < 0) {
            sb.append("over slot");
        } else {
            sb.append("Spent today: ").append(format(slice.dailyToday(), currency))
                    .append(" / slot ").append(format(slice.todaySlot(), currency));
        }
        sb.append('\n').append("Month left: ").append(format(slice.remainingMonth(), currency));
        if (!upcoming.isEmpty()) {
            sb.append('\n').append("Due in 7 days: ");
            var u = upcoming.get(0);
            sb.append(u.name()).append(' ').append(format(u.amount().amountMinor(), currency))
                    .append(" (").append(u.dueOn().format(DAY)).append(')');
        }
        trySend("digest", today, currency, sb.toString());
    }

    private void trySend(String type, LocalDate civilDay, String currency, String text) {
        if (!repo.insertNotifyIfAbsent("owner", type, civilDay, currency)) {
            return;
        }
        try {
            telegram.sendMessage(text);
        } catch (Exception e) {
            log.warn("finance telegram {} skipped: {}", type, e.getMessage());
            repo.deleteNotifyLog("owner", type, civilDay, currency);
        }
    }

    private static String format(long minor, String currency) {
        var dto = FinanceMoney.MoneyDto.of(minor, currency);
        return dto.currency() + " " + dto.amount().toPlainString();
    }
}
