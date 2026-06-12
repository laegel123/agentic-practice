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
    public Refund refund(Long paymentId, double amount, String reason) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

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
        double refundedTotal = 0;
        for (Refund r : refundRepository.findByPaymentId(paymentId)) {
            refundedTotal += r.getAmount();
        }
        if (refundedTotal >= payment.getAmount()) {
            payment.setStatus(PaymentStatus.REFUNDED);
        } else {
            payment.setStatus(PaymentStatus.PARTIALLY_REFUNDED);
        }
        paymentRepository.save(payment);

        return refund;
    }
}
