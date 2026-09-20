package com.ivelox.core.modules.paymentapproval.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import com.ivelox.core.modules.paymentapproval.application.port.out.PaymentApprovalRepositoryPort;
import com.ivelox.core.modules.paymentapproval.domain.model.Payment;
import com.ivelox.core.modules.paymentapproval.domain.model.PaymentStatus;
import com.ivelox.core.modules.paymentapproval.domain.model.PaymentSummary;

@Component
public class PaymentApprovalRepositoryAdapter implements PaymentApprovalRepositoryPort {

    private static final RowMapper<Payment> PAYMENT = (rs, n) -> new Payment(
            UUID.fromString(rs.getString("id")),
            rs.getString("user_id"),
            rs.getString("recipient_name"),
            rs.getLong("amount_minor"),
            rs.getString("currency").trim(),
            PaymentStatus.valueOf(rs.getString("status")),
            rs.getString("reference"),
            rs.getString("note"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("decided_at") == null ? null : rs.getTimestamp("decided_at").toInstant()
    );

    private final JdbcTemplate jdbc;

    public PaymentApprovalRepositoryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Payment insert(Payment payment) {
        jdbc.update("""
                insert into payment_approval_payments
                (id, user_id, recipient_name, amount_minor, currency, status, reference, note, created_at, decided_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, payment.id(), payment.userId(), payment.recipientName(), payment.amountMinor(),
                payment.currency(), payment.status().name(), payment.reference(), payment.note(),
                Timestamp.from(payment.createdAt()), null);
        return payment;
    }

    @Override
    public List<Payment> listDecided(String userId) {
        return jdbc.query("""
                select * from payment_approval_payments
                where user_id = ? and status in ('APPROVED', 'REJECTED')
                order by decided_at desc, id desc
                """, PAYMENT, userId);
    }

    @Override
    public List<Payment> listPending(String userId) {
        return jdbc.query("""
                select * from payment_approval_payments
                where user_id = ? and status = 'PENDING'
                order by created_at desc, id desc
                """, PAYMENT, userId);
    }

    @Override
    public Optional<Payment> find(String userId, UUID id) {
        return jdbc.query("""
                select * from payment_approval_payments where id = ? and user_id = ?
                """, PAYMENT, id, userId).stream().findFirst();
    }

    @Override
    public PaymentSummary approvedSummary(String userId, Instant from, Instant to) {
        var row = jdbc.queryForObject("""
                select coalesce(sum(amount_minor), 0), count(*)
                from payment_approval_payments
                where user_id = ? and status = 'APPROVED'
                  and decided_at >= ? and decided_at < ?
                """, (rs, n) -> new long[]{rs.getLong(1), rs.getLong(2)},
                userId, Timestamp.from(from), Timestamp.from(to));
        return new PaymentSummary(YearMonth.from(from.atZone(com.ivelox.core.health.CivilDay.ZONE)), row[0], row[1]);
    }

    @Override
    public int decide(String userId, UUID id, PaymentStatus status, Instant decidedAt) {
        return jdbc.update("""
                update payment_approval_payments
                set status = ?, decided_at = ?
                where id = ? and user_id = ? and status = 'PENDING'
                """, status.name(), Timestamp.from(decidedAt), id, userId);
    }

    @Override
    public int deleteByIds(String userId, List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        var placeholders = String.join(", ", java.util.Collections.nCopies(ids.size(), "?"));
        Object[] args = new Object[ids.size() + 1];
        args[0] = userId;
        for (int i = 0; i < ids.size(); i++) {
            args[i + 1] = ids.get(i);
        }
        return jdbc.update(
                "delete from payment_approval_payments where user_id = ? and id in (" + placeholders + ")",
                args);
    }
}
