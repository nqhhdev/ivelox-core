package com.ivelox.core.modules.paymentapproval.application.service;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ivelox.core.health.CivilDay;
import com.ivelox.core.modules.paymentapproval.application.port.in.ApprovePaymentUseCase;
import com.ivelox.core.modules.paymentapproval.application.port.in.CreatePaymentRequestUseCase;
import com.ivelox.core.modules.paymentapproval.application.port.in.DeletePaymentUseCase;
import com.ivelox.core.modules.paymentapproval.application.port.in.DeletePaymentsUseCase;
import com.ivelox.core.modules.paymentapproval.application.port.in.GetPaymentDetailsUseCase;
import com.ivelox.core.modules.paymentapproval.application.port.in.GetPaymentSummaryUseCase;
import com.ivelox.core.modules.paymentapproval.application.port.in.ListPaymentsUseCase;
import com.ivelox.core.modules.paymentapproval.application.port.in.ListPendingPaymentsUseCase;
import com.ivelox.core.modules.paymentapproval.application.port.in.RejectPaymentUseCase;
import com.ivelox.core.modules.paymentapproval.application.port.out.PaymentApprovalRepositoryPort;
import com.ivelox.core.modules.paymentapproval.domain.model.Payment;
import com.ivelox.core.modules.paymentapproval.domain.model.PaymentStatus;
import com.ivelox.core.modules.paymentapproval.domain.model.PaymentSummary;

@Service
public class PaymentApprovalService implements CreatePaymentRequestUseCase, GetPaymentDetailsUseCase,
        GetPaymentSummaryUseCase, ListPaymentsUseCase, ListPendingPaymentsUseCase,
        ApprovePaymentUseCase, RejectPaymentUseCase, DeletePaymentUseCase, DeletePaymentsUseCase {

    private static final String DEMO_OTP = "8888";
    private static final List<String> RECIPIENTS = List.of("Ahmed K.", "Mariam S.", "Omar R.", "Noura A.");
    private final PaymentApprovalRepositoryPort repo;

    public PaymentApprovalService(PaymentApprovalRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public Payment create(String userId) {
        int index = ThreadLocalRandom.current().nextInt(RECIPIENTS.size());
        long amountMinor = ThreadLocalRandom.current().nextLong(1_000, 250_001);
        String reference = "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return repo.insert(Payment.createIncoming(userId, RECIPIENTS.get(index), amountMinor,
                reference, "Demo incoming payment", Instant.now()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Payment> list(String userId) {
        return repo.listDecided(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Payment> listPending(String userId) {
        return repo.listPending(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Payment get(String userId, UUID id) {
        Payment payment = repo.find(userId, id).orElseThrow(() -> notFound());
        if (!payment.isDecided()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "payment_not_decided");
        }
        return payment;
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentSummary summary(String userId, YearMonth month) {
        Instant from = month.atDay(1).atStartOfDay(CivilDay.ZONE).toInstant();
        Instant to = month.plusMonths(1).atDay(1).atStartOfDay(CivilDay.ZONE).toInstant();
        return repo.approvedSummary(userId, from, to);
    }

    @Override
    @Transactional
    public Payment approve(String userId, UUID id, String otp) {
        return decide(userId, id, otp, PaymentStatus.APPROVED);
    }

    @Override
    @Transactional
    public Payment reject(String userId, UUID id, String otp) {
        return decide(userId, id, otp, PaymentStatus.REJECTED);
    }

    @Override
    @Transactional
    public void deleteOne(String userId, UUID id) {
        if (repo.deleteByIds(userId, List.of(id)) == 0) {
            throw notFound();
        }
    }

    @Override
    @Transactional
    public int deleteMany(String userId, List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_ids");
        }
        int deleted = repo.deleteByIds(userId, ids);
        if (deleted == 0) {
            throw notFound();
        }
        return deleted;
    }

    private Payment decide(String userId, UUID id, String otp, PaymentStatus status) {
        if (!DEMO_OTP.equals(otp)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_otp");
        }
        Instant now = Instant.now();
        if (repo.decide(userId, id, status, now) == 1) {
            return repo.find(userId, id).orElseThrow(() -> notFound());
        }
        repo.find(userId, id).orElseThrow(() -> notFound());
        throw new ResponseStatusException(HttpStatus.CONFLICT, "payment_already_decided");
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "payment_not_found");
    }
}
