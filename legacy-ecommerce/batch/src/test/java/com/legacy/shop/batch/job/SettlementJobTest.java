package com.legacy.shop.batch.job;

import com.legacy.shop.batch.domain.OrderRow;
import com.legacy.shop.batch.repository.OrderRowRepository;
import com.legacy.shop.core.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

/**
 * SettlementJob.settle 의 동작 고정.
 *
 * BT1 수정(2026-06-16): 정산 매출 합계에서 CANCELLED 주문을 제외한다(이전에는 status 를 매핑만 하고
 * 필터에 쓰지 않아 취소 주문도 합산 → 매출 과대계상). (docs/known-issues.md BT1)
 *
 * OrderRow 는 읽기 전용 프로젝션(setter 없음)이라 ReflectionTestUtils 로 필드를 채워 픽스처를 만든다.
 */
@ExtendWith(MockitoExtension.class)
class SettlementJobTest {

    private static final double EPS = 1e-9;

    @Mock
    private OrderRowRepository orderRowRepository;

    @InjectMocks
    private SettlementJob settlementJob;

    private OrderRow order(OrderStatus status, double totalAmount) {
        OrderRow row = new OrderRow();
        ReflectionTestUtils.setField(row, "status", status);
        ReflectionTestUtils.setField(row, "totalAmount", totalAmount);
        return row;
    }

    @Test
    void settle_excludesCancelledOrders_sumsTheRest() {
        when(orderRowRepository.findAll()).thenReturn(List.of(
                order(OrderStatus.PAID, 100.0),
                order(OrderStatus.CREATED, 50.0),
                order(OrderStatus.CANCELLED, 30.0)));   // 제외 대상

        // 100 + 50 = 150 (취소 30 은 합산하지 않음). 수정 전이라면 180.
        assertThat(settlementJob.settle()).isCloseTo(150.0, within(EPS));
    }

    @Test
    void settle_emptyOrders_isZero() {
        when(orderRowRepository.findAll()).thenReturn(List.of());

        assertThat(settlementJob.settle()).isCloseTo(0.0, within(EPS));
    }
}
