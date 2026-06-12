package com.legacy.shop.payment.service;

import com.legacy.shop.common.util.DateUtils;
import com.legacy.shop.core.error.BusinessException;
import com.legacy.shop.core.error.ErrorCode;
import com.legacy.shop.payment.domain.Ledger;
import com.legacy.shop.payment.domain.LedgerType;
import com.legacy.shop.payment.domain.Payment;
import com.legacy.shop.payment.domain.PaymentStatus;
import com.legacy.shop.payment.repository.LedgerRepository;
import com.legacy.shop.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 승인.
 */
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final LedgerRepository ledgerRepository;

    public PaymentService(PaymentRepository paymentRepository, LedgerRepository ledgerRepository) {
        this.paymentRepository = paymentRepository;
        this.ledgerRepository = ledgerRepository;
    }

    @Transactional
    public Payment charge(Long orderId, Long customerId, double amount, String method) {
        Payment p = new Payment();
        p.setOrderId(orderId);
        p.setCustomerId(customerId);
        p.setAmount(amount);
        p.setMethod(method == null ? "CARD" : method);
        p.setStatus(PaymentStatus.APPROVED);
        p.setApprovedAt(DateUtils.now());
        p = paymentRepository.save(p);

        Ledger l = new Ledger();
        l.setPaymentId(p.getId());
        l.setType(LedgerType.CHARGE);
        l.setAmount(amount);
        ledgerRepository.save(l);

        return p;
    }

    public Payment get(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
    }
}
