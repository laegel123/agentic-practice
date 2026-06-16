package com.legacy.shop.batch.job;

import com.legacy.shop.batch.domain.OrderRow;
import com.legacy.shop.batch.job.DailySalesAggregationJob.DailySales;
import com.legacy.shop.core.domain.OrderStatus;
import com.legacy.shop.batch.repository.OrderRowRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * DailySalesAggregationJob.aggregate 의 동작 고정.
 *
 * BT1 수정(2026-06-16): 오늘 매출 집계에서 CANCELLED 주문을 제외한다(취소 주문도 합산하던 결함).
 * B7 수정(2026-06-16): '오늘' 판정을 서버 로컬(LocalDate.now())이 아니라 주문 시각(UTC)과 같은
 *   기준(주입된 UTC Clock)의 날짜로 한다. 시각을 고정 Clock 으로 박제해 자정 부근 날짜 경계를
 *   결정론적으로 검증한다. (docs/known-issues.md BT1·B7)
 * 금액 타입 전환(2026-06-16, [ADR-0006]): 매출 누계가 BigDecimal. 값 동등성(isEqualByComparingTo)으로 단언.
 *
 * OrderRow 는 읽기 전용 프로젝션(setter 없음)이라 ReflectionTestUtils 로 필드를 채워 픽스처를 만든다.
 */
@ExtendWith(MockitoExtension.class)
class DailySalesAggregationJobTest {

    // 집계 기준 시각을 UTC 정오로 고정 → '오늘' = 2024-01-15 (UTC).
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2024-01-15T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.of(2024, 1, 15);
    private static final LocalDateTime TODAY_NOON = TODAY.atTime(12, 0);
    private static final LocalDateTime YESTERDAY_NOON = TODAY.minusDays(1).atTime(12, 0);

    @Mock
    private OrderRowRepository orderRowRepository;

    private DailySalesAggregationJob job() {
        return new DailySalesAggregationJob(orderRowRepository, CLOCK);
    }

    private OrderRow order(OrderStatus status, String totalAmount, LocalDateTime orderedAt) {
        OrderRow row = new OrderRow();
        ReflectionTestUtils.setField(row, "status", status);
        ReflectionTestUtils.setField(row, "totalAmount", new BigDecimal(totalAmount));
        ReflectionTestUtils.setField(row, "orderedAt", orderedAt);
        return row;
    }

    @Test
    void aggregate_today_excludesCancelled() {
        when(orderRowRepository.findAll()).thenReturn(List.of(
                order(OrderStatus.PAID, "100.0", TODAY_NOON),
                order(OrderStatus.CREATED, "50.0", TODAY_NOON),
                order(OrderStatus.CANCELLED, "30.0", TODAY_NOON)));   // 오늘이지만 제외

        DailySales result = job().aggregate();

        // 취소 제외 → 2건 / 150 (수정 전이라면 3건 / 180).
        assertThat(result.count()).isEqualTo(2);
        assertThat(result.revenue()).isEqualByComparingTo("150.0");
    }

    @Test
    void aggregate_excludesOtherDays() {
        when(orderRowRepository.findAll()).thenReturn(List.of(
                order(OrderStatus.PAID, "100.0", TODAY_NOON),
                order(OrderStatus.PAID, "999.0", YESTERDAY_NOON)));   // 어제 주문은 집계 제외

        DailySales result = job().aggregate();

        assertThat(result.count()).isEqualTo(1);
        assertThat(result.revenue()).isEqualByComparingTo("100.0");
    }

    @Test
    void aggregate_dayBoundaryIsUtc_notServerLocal() {
        // 집계 기준 시각을 UTC 늦은 밤(23:30)으로 고정 → '오늘' = 2024-01-15 (UTC).
        Clock lateNight = Clock.fixed(Instant.parse("2024-01-15T23:30:00Z"), ZoneOffset.UTC);
        DailySalesAggregationJob job = new DailySalesAggregationJob(orderRowRepository, lateNight);

        when(orderRowRepository.findAll()).thenReturn(List.of(
                // UTC 로는 아직 2024-01-15 → 포함. (서버가 KST 였다면 로컬 날짜는 2024-01-16 이라
                // 옛 LocalDate.now() 기준으로는 빠졌을 주문이다.)
                order(OrderStatus.PAID, "100.0", LocalDateTime.of(2024, 1, 15, 23, 30)),
                // UTC 로 이미 다음 날(2024-01-16) → 제외.
                order(OrderStatus.PAID, "200.0", LocalDateTime.of(2024, 1, 16, 0, 30))));

        DailySales result = job.aggregate();

        // 일 경계가 UTC 기준이라 23:30(UTC) 주문만 오늘로 집계된다.
        assertThat(result.count()).isEqualTo(1);
        assertThat(result.revenue()).isEqualByComparingTo("100.0");
    }
}
