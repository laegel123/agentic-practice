package com.legacy.shop.payment.service;

import com.legacy.shop.payment.domain.Ledger;
import com.legacy.shop.payment.domain.LedgerType;
import com.legacy.shop.payment.domain.Payment;
import com.legacy.shop.payment.domain.PaymentStatus;
import com.legacy.shop.payment.repository.LedgerRepository;
import com.legacy.shop.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PaymentService.charge 의 현재 동작 고정: APPROVED 결제 + CHARGE 원장(Ledger) 저장.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private LedgerRepository ledgerRepository;

    @InjectMocks private PaymentService paymentService;

    @Test
    void charge_savesApprovedPayment_andChargeLedger() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment p = paymentService.charge(1L, 2L, 100.0, "CARD");

        assertThat(p.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(p.getOrderId()).isEqualTo(1L);
        assertThat(p.getCustomerId()).isEqualTo(2L);
        assertThat(p.getAmount()).isCloseTo(100.0, within(1e-9));
        assertThat(p.getMethod()).isEqualTo("CARD");
        assertThat(p.getApprovedAt()).isNotNull();

        ArgumentCaptor<Ledger> ledger = ArgumentCaptor.forClass(Ledger.class);
        verify(ledgerRepository).save(ledger.capture());
        assertThat(ledger.getValue().getType()).isEqualTo(LedgerType.CHARGE);
        assertThat(ledger.getValue().getAmount()).isCloseTo(100.0, within(1e-9));
    }

    @Test
    void charge_nullMethod_defaultsToCard() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment p = paymentService.charge(1L, 2L, 50.0, null);

        assertThat(p.getMethod()).isEqualTo("CARD");
    }
}
