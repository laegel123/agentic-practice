package com.legacy.shop.payment.service;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RefundService.refund 의 현재 동작 고정: 누적 환불액으로 상태 전이, REFUND 원장 기록.
 *
 * 주의: 환불 누계가 결제액을 초과해도 막지 않는다(REFUND_EXCEEDS_PAYMENT 미사용). over-refund 케이스가
 * 그 누락을 박제한다 — 한도 검증을 추가하면 해당 단언을 뒤집어야 한다. (docs/known-issues.md B6)
 */
@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private RefundRepository refundRepository;
    @Mock private LedgerRepository ledgerRepository;

    @InjectMocks private RefundService refundService;

    private static final Long PAYMENT_ID = 5L;

    private Payment paymentOf(double amount) {
        Payment p = new Payment();
        p.setAmount(amount);
        p.setStatus(PaymentStatus.APPROVED);
        return p;
    }

    private Refund refundOf(double amount) {
        Refund r = new Refund();
        r.setPaymentId(PAYMENT_ID);
        r.setAmount(amount);
        return r;
    }

    @Test
    void partialRefund_setsPartiallyRefunded_andWritesRefundLedger() {
        Payment payment = paymentOf(100.0);
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));
        when(refundRepository.findByPaymentId(PAYMENT_ID)).thenReturn(List.of(refundOf(30.0)));

        refundService.refund(PAYMENT_ID, 30.0, "단순변심");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);

        ArgumentCaptor<Ledger> ledger = ArgumentCaptor.forClass(Ledger.class);
        verify(ledgerRepository).save(ledger.capture());
        assertThat(ledger.getValue().getType()).isEqualTo(LedgerType.REFUND);
    }

    @Test
    void fullRefund_setsRefunded_whenCumulativeEqualsPayment() {
        Payment payment = paymentOf(100.0);
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));
        when(refundRepository.findByPaymentId(PAYMENT_ID)).thenReturn(List.of(refundOf(100.0)));

        refundService.refund(PAYMENT_ID, 100.0, "전액환불");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void overRefund_isNotBlocked_currentlyAllowed() {
        // 결제 100 인데 150 환불 시도 → 한도 검증이 없어 그대로 REFUNDED 처리됨(버그).
        Payment payment = paymentOf(100.0);
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));
        when(refundRepository.findByPaymentId(PAYMENT_ID)).thenReturn(List.of(refundOf(150.0)));

        Refund r = refundService.refund(PAYMENT_ID, 150.0, "과다환불");

        assertThat(r.getAmount()).isCloseTo(150.0, within(1e-9));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void unknownPayment_throwsPaymentNotFound() {
        when(paymentRepository.findById(9L)).thenReturn(Optional.empty());

        BusinessException ex = catchThrowableOfType(BusinessException.class,
                () -> refundService.refund(9L, 10.0, "x"));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);
    }
}
