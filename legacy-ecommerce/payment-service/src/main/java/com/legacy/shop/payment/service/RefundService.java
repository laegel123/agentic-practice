package com.legacy.shop.payment.service;

import com.legacy.shop.common.util.DateUtils;
import com.legacy.shop.core.error.BusinessException;
import com.legacy.shop.core.error.ErrorCode;
import com.legacy.shop.payment.domain.Ledger;
import com.legacy.shop.payment.domain.LedgerType;
import com.legacy.shop.payment.domain.Payment;
import com.legacy.shop.payment.domain.PaymentStatus;
import com.legacy.shop.payment.domain.Refund;
import com.legacy.shop.payment.repository.LedgerRepository;
import com.legacy.shop.payment.repository.PaymentRepository;
import com.legacy.shop.payment.repository.RefundRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 환불 처리.
 */
@Service
public class RefundService {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final LedgerRepository ledgerRepository;

    public RefundService(PaymentRepository paymentRepository,
                         RefundRepository refundRepository,
                         LedgerRepository ledgerRepository) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.ledgerRepository = ledgerRepository;
    }

    @Transactional
    public Refund refund(Long paymentId, BigDecimal amount, String reason) {
        // 음수/0/null 환불액 거부 (B6 후속 — 리뷰 차단). 음수면 누계를 줄여 과다환불 가드를 통과하고,
        // 음수 Refund·원장을 만들며 상태를 거꾸로 뒤집을 수 있다. BigDecimal 은 null 이 가능하므로(과거
        // primitive double 과 달리) null 도 함께 막는다. validation 스타터가 없어 여기서 막는다.
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REFUND_AMOUNT);
        }

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        // 기존 환불 누계 + 이번 환불액이 결제액을 초과하면 거부한다 (과다 환불 차단 — B6).
        BigDecimal alreadyRefunded = BigDecimal.ZERO;
        for (Refund r : refundRepository.findByPaymentId(paymentId)) {
            alreadyRefunded = alreadyRefunded.add(r.getAmount());
        }
        BigDecimal refundedTotal = alreadyRefunded.add(amount);
        if (refundedTotal.compareTo(payment.getAmount()) > 0) {
            throw new BusinessException(ErrorCode.REFUND_EXCEEDS_PAYMENT);
        }

        Refund refund = new Refund();
        refund.setPaymentId(paymentId);
        refund.setAmount(amount);
        refund.setReason(reason);
        refund.setRefundedAt(DateUtils.now());
        refund = refundRepository.save(refund);

        Ledger l = new Ledger();
        l.setPaymentId(paymentId);
        l.setType(LedgerType.REFUND);
        l.setAmount(amount);
        ledgerRepository.save(l);

        // 누적 환불액으로 상태 갱신
        if (refundedTotal.compareTo(payment.getAmount()) >= 0) {
            payment.setStatus(PaymentStatus.REFUNDED);
        } else {
            payment.setStatus(PaymentStatus.PARTIALLY_REFUNDED);
        }
        paymentRepository.save(payment);

        return refund;
    }
}
