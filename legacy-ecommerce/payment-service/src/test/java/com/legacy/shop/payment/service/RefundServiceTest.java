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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RefundService.refund 의 동작 고정: 누적 환불액으로 상태 전이, REFUND 원장 기록, 과다 환불 차단.
 *
 * B6 수정: 기존 환불 누계 + 이번 환불액이 결제액을 초과하면 REFUND_EXCEEDS_PAYMENT 로 거부한다.
 * (이전에는 한도 검증이 없어 과다 환불이 통과했다 — overRefund 케이스가 그 차단을 단언한다.)
 * stub 규약: refundRepository.findByPaymentId 는 '이번 호출 이전에 이미 존재하던' 환불 목록을 돌려준다.
 * (docs/known-issues.md B6)
 */
@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private RefundRepository refundRepository;
    @Mock private LedgerRepository ledgerRepository;

    @InjectMocks private RefundService refundService;

    private static final Long PAYMENT_ID = 5L;

    private Payment paymentOf(String amount) {
        Payment p = new Payment();
        p.setAmount(new BigDecimal(amount));
        p.setStatus(PaymentStatus.APPROVED);
        return p;
    }

    private Refund refundOf(String amount) {
        Refund r = new Refund();
        r.setPaymentId(PAYMENT_ID);
        r.setAmount(new BigDecimal(amount));
        return r;
    }

    @Test
    void partialRefund_setsPartiallyRefunded_andWritesRefundLedger() {
        Payment payment = paymentOf("100.0");
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(refundRepository.findByPaymentId(PAYMENT_ID)).thenReturn(List.of()); // 첫 환불
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));

        refundService.refund(PAYMENT_ID, new BigDecimal("30.0"), "단순변심");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);

        ArgumentCaptor<Ledger> ledger = ArgumentCaptor.forClass(Ledger.class);
        verify(ledgerRepository).save(ledger.capture());
        assertThat(ledger.getValue().getType()).isEqualTo(LedgerType.REFUND);
    }

    @Test
    void fullRefund_setsRefunded_whenCumulativeEqualsPayment() {
        // 기존 60 환불 + 이번 40 = 정확히 100(=결제액) → 경계상 허용되고 REFUNDED.
        Payment payment = paymentOf("100.0");
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(refundRepository.findByPaymentId(PAYMENT_ID)).thenReturn(List.of(refundOf("60.0")));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));

        refundService.refund(PAYMENT_ID, new BigDecimal("40.0"), "잔액환불");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void overRefund_isBlocked_throwsRefundExceedsPayment() {
        // 결제 100, 기존 60 환불 상태에서 50 추가 환불 시도(누계 110 > 100) → 한도 초과로 거부(B6 수정).
        Payment payment = paymentOf("100.0");
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(refundRepository.findByPaymentId(PAYMENT_ID)).thenReturn(List.of(refundOf("60.0")));

        BusinessException ex = catchThrowableOfType(BusinessException.class,
                () -> refundService.refund(PAYMENT_ID, new BigDecimal("50.0"), "과다환불"));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.REFUND_EXCEEDS_PAYMENT);
        // 한도 초과 시 환불/원장은 기록되지 않고 결제 상태도 바뀌지 않는다.
        verify(refundRepository, never()).save(any());
        verify(ledgerRepository, never()).save(any());
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void negativeAmount_isRejected_throwsInvalidRefundAmount_andWritesNothing() {
        // B6 후속(리뷰 차단): 음수 환불액은 누계를 줄여 과다환불 가드를 우회하므로 입력 단계에서 거부.
        // 환불/원장 미기록, 결제 상태 불변. (결제 조회 전에 막히므로 findById 스텁도 불필요)
        BusinessException ex = catchThrowableOfType(BusinessException.class,
                () -> refundService.refund(PAYMENT_ID, new BigDecimal("-10.0"), "음수환불"));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFUND_AMOUNT);
        verify(refundRepository, never()).save(any());
        verify(ledgerRepository, never()).save(any());
    }

    @Test
    void zeroAmount_isRejected_throwsInvalidRefundAmount() {
        BusinessException ex = catchThrowableOfType(BusinessException.class,
                () -> refundService.refund(PAYMENT_ID, BigDecimal.ZERO, "영원환불"));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFUND_AMOUNT);
        verify(refundRepository, never()).save(any());
        verify(ledgerRepository, never()).save(any());
    }

    @Test
    void unknownPayment_throwsPaymentNotFound() {
        when(paymentRepository.findById(9L)).thenReturn(Optional.empty());

        BusinessException ex = catchThrowableOfType(BusinessException.class,
                () -> refundService.refund(9L, new BigDecimal("10.0"), "x"));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);
    }
}
