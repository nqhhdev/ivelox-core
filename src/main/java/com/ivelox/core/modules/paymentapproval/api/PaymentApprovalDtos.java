package com.ivelox.core.modules.paymentapproval.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ivelox.core.modules.finance.domain.model.FinanceMoney;
import com.ivelox.core.modules.paymentapproval.domain.model.Payment;
import com.ivelox.core.modules.paymentapproval.domain.model.PaymentSummary;

public final class PaymentApprovalDtos {
    private PaymentApprovalDtos() {
    }

    public record PaymentView(
            UUID id,
            @JsonProperty("recipient_name") String recipientName,
            java.math.BigDecimal amount,
            @JsonProperty("amount_minor") long amountMinor,
            String currency,
            String status,
            String reference,
            String note,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("decided_at") Instant decidedAt
    ) {
        public static PaymentView of(Payment payment) {
            var money = FinanceMoney.MoneyDto.of(payment.amountMinor(), payment.currency());
            return new PaymentView(payment.id(), payment.recipientName(), money.amount(), money.amountMinor(),
                    money.currency(), payment.status().name(), payment.reference(), payment.note(),
                    payment.createdAt(), payment.decidedAt());
        }
    }

    public record PaymentList(List<PaymentView> items) {
    }

    public record PaymentSummaryView(
            String month,
            String currency,
            @JsonProperty("total_amount") java.math.BigDecimal totalAmount,
            @JsonProperty("total_amount_minor") long totalAmountMinor,
            @JsonProperty("payment_count") long paymentCount
    ) {
        public static PaymentSummaryView of(PaymentSummary summary) {
            var money = FinanceMoney.MoneyDto.of(summary.totalAmountMinor(), "AED");
            return new PaymentSummaryView(summary.month().toString(), money.currency(), money.amount(),
                    money.amountMinor(), summary.paymentCount());
        }
    }

    public record DecisionRequest(String otp) {
    }

    public record CreateRequest() {
    }

    public record DeletePaymentsRequest(List<UUID> ids) {
    }

    public record DeletePaymentsResult(int deleted) {
    }
}
