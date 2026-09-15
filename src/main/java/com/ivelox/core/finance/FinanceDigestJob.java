package com.ivelox.core.finance;

import java.time.LocalDate;
import java.time.YearMonth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ivelox.core.health.CivilDay;

@Component
@ConditionalOnProperty(prefix = "ivelox", name = "finance-enabled", havingValue = "true", matchIfMissing = true)
public class FinanceDigestJob {

    private static final String OWNER = "owner";

    private final FinanceRepository repo;
    private final FinanceDuePoster duePoster;
    private final FinanceNotifier notifier;

    public FinanceDigestJob(FinanceRepository repo, FinanceDuePoster duePoster, FinanceNotifier notifier) {
        this.repo = repo;
        this.duePoster = duePoster;
        this.notifier = notifier;
    }

    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Ho_Chi_Minh")
    public void postDues() {
        duePoster.catchUpTo(CivilDay.todayIct());
    }

    @Scheduled(cron = "0 0 21 * * *", zone = "Asia/Ho_Chi_Minh")
    public void sendDigest() {
        var settings = repo.settings(OWNER);
        if (!settings.digestEnabled()) {
            return;
        }
        String currency = settings.homeCurrency();
        LocalDate today = CivilDay.todayIct();
        YearMonth month = YearMonth.from(today);

        var txs = repo.txsInMonth(OWNER, month).stream()
                .map(t -> new FinanceMath.Tx(t.kind(), t.amountMinor(), t.occurredOn(), t.currency()))
                .toList();
        var slice = FinanceMath.compute(txs, currency, month, today);

        LocalDate horizon = today.plusDays(7);
        var upcoming = repo.fixed(OWNER).stream()
                .filter(f -> currency.equals(f.currency()))
                .map(f -> {
                    LocalDate due = today.withDayOfMonth(Math.min(f.dayOfMonth(), today.lengthOfMonth()));
                    if (due.isBefore(today)) {
                        YearMonth next = month.plusMonths(1);
                        due = next.atDay(Math.min(f.dayOfMonth(), next.lengthOfMonth()));
                    }
                    return new FinanceModels.Upcoming("fixed", f.name(), due,
                            FinanceMoney.MoneyDto.of(f.amountMinor(), currency));
                })
                .filter(u -> !u.dueOn().isAfter(horizon))
                .sorted((a, b) -> a.dueOn().compareTo(b.dueOn()))
                .toList();

        notifier.sendDigest(settings, slice, currency, upcoming);
    }
}
