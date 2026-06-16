package com.legacy.shop.batch.job;

import com.legacy.shop.batch.domain.OrderRow;
import com.legacy.shop.batch.domain.OrderStatus;
import com.legacy.shop.batch.job.DailySalesAggregationJob.DailySales;
import com.legacy.shop.batch.repository.OrderRowRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

/**
 * DailySalesAggregationJob.aggregate 의 동작 고정.
 *
 * BT1 수정(2026-06-16): 오늘 매출 집계에서 CANCELLED 주문을 제외한다(취소 주문도 합산하던 결함).
 * (docs/known-issues.md BT1)
 *
 * 주의: 이 잡의 '오늘' 비교는 여전히 LocalDate.now()(서버 로컬) 기준이다 — 타임존 경계 결함(B7)은
 * 이번 범위 밖이라 그대로 둔다. 픽스처 시각은 정오로 잡아 경계 흔들림을 피한다.
 */
@ExtendWith(MockitoExtension.class)
class DailySalesAggregationJobTest {

    private static final double EPS = 1e-9;
    private static final LocalDateTime TODAY_NOON = LocalDate.now().atTime(12, 0);
    private static final LocalDateTime YESTERDAY_NOON = LocalDate.now().minusDays(1).atTime(12, 0);

    @Mock
    private OrderRowRepository orderRowRepository;

    @InjectMocks
    private DailySalesAggregationJob job;

    private OrderRow order(OrderStatus status, double totalAmount, LocalDateTime orderedAt) {
        OrderRow row = new OrderRow();
        ReflectionTestUtils.setField(row, "status", status);
        ReflectionTestUtils.setField(row, "totalAmount", totalAmount);
        ReflectionTestUtils.setField(row, "orderedAt", orderedAt);
        return row;
    }

    @Test
    void aggregate_today_excludesCancelled() {
        when(orderRowRepository.findAll()).thenReturn(List.of(
                order(OrderStatus.PAID, 100.0, TODAY_NOON),
                order(OrderStatus.CREATED, 50.0, TODAY_NOON),
                order(OrderStatus.CANCELLED, 30.0, TODAY_NOON)));   // 오늘이지만 제외

        DailySales result = job.aggregate();

        // 취소 제외 → 2건 / 150 (수정 전이라면 3건 / 180).
        assertThat(result.count()).isEqualTo(2);
        assertThat(result.revenue()).isCloseTo(150.0, within(EPS));
    }

    @Test
    void aggregate_excludesOtherDays() {
        when(orderRowRepository.findAll()).thenReturn(List.of(
                order(OrderStatus.PAID, 100.0, TODAY_NOON),
                order(OrderStatus.PAID, 999.0, YESTERDAY_NOON)));   // 어제 주문은 집계 제외

        DailySales result = job.aggregate();

        assertThat(result.count()).isEqualTo(1);
        assertThat(result.revenue()).isCloseTo(100.0, within(EPS));
    }
}
