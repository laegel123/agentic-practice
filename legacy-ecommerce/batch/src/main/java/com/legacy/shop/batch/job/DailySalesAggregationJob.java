package com.legacy.shop.batch.job;

import com.legacy.shop.batch.domain.OrderRow;
import com.legacy.shop.batch.domain.OrderStatus;
import com.legacy.shop.batch.repository.OrderRowRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 일일 매출 집계 잡: 오늘 들어온 (취소되지 않은) 주문의 건수/매출을 집계한다.
 */
@Component
public class DailySalesAggregationJob {

    private final OrderRowRepository orderRowRepository;

    public DailySalesAggregationJob(OrderRowRepository orderRowRepository) {
        this.orderRowRepository = orderRowRepository;
    }

    public DailySales aggregate() {
        LocalDate today = LocalDate.now();
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
        System.out.println("[집계] 오늘 주문수=" + count + ", 매출=" + sum);
        return new DailySales(count, sum);
    }

    /** 일일 집계 결과(건수, 매출). */
    public record DailySales(int count, double revenue) {
    }
}
