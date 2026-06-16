package com.legacy.shop.batch.job;

import com.legacy.shop.batch.domain.OrderRow;
import com.legacy.shop.batch.domain.OrderStatus;
import com.legacy.shop.batch.repository.OrderRowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * 일일 매출 집계 잡: 오늘 들어온 (취소되지 않은) 주문의 건수/매출을 집계한다.
 *
 * B7 수정(2026-06-16): 주문 시각(orderedAt)은 DateUtils.now()=UTC 로 저장되므로 '오늘' 비교도
 * 같은 기준(UTC)으로 해야 한다. 이전에는 LocalDate.now()(서버 로컬)로 비교해 자정 부근에서 날짜
 * 경계가 어긋나 집계 누락/중복이 났다(예: KST 00:00~09:00 주문이 전날로 분류). 비교 기준을
 * 주입된 Clock(기본 Clock.systemUTC())의 날짜로 통일했다 — 테스트에서 시각을 고정할 수 있다.
 */
@Component
public class DailySalesAggregationJob {

    private static final Logger log = LoggerFactory.getLogger(DailySalesAggregationJob.class);

    private final OrderRowRepository orderRowRepository;
    private final Clock clock;

    public DailySalesAggregationJob(OrderRowRepository orderRowRepository, Clock clock) {
        this.orderRowRepository = orderRowRepository;
        this.clock = clock;
    }

    public DailySales aggregate() {
        LocalDate today = LocalDate.now(clock);   // 주문 시각(UTC)과 같은 기준(UTC)의 오늘 (B7)
        int count = 0;
        double sum = 0;
        for (OrderRow o : orderRowRepository.findAll()) {
            if (o.getStatus() != OrderStatus.CANCELLED            // 취소 주문은 매출 집계에서 제외 (BT1)
                    && o.getOrderedAt() != null
                    && o.getOrderedAt().toLocalDate().equals(today)) {
                count++;
                sum += o.getTotalAmount();
            }
        }
        log.info("[집계] 오늘 주문수={}, 매출={}", count, sum);
        return new DailySales(count, sum);
    }

    /** 일일 집계 결과(건수, 매출). */
    public record DailySales(int count, double revenue) {
    }
}
