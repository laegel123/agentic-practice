package com.legacy.shop.batch.job;

import com.legacy.shop.batch.domain.OrderRow;
import com.legacy.shop.batch.repository.OrderRowRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 일일 매출 집계 잡: 오늘 들어온 주문의 건수/매출을 집계한다.
 */
@Component
public class DailySalesAggregationJob {

    private final OrderRowRepository orderRowRepository;

    public DailySalesAggregationJob(OrderRowRepository orderRowRepository) {
        this.orderRowRepository = orderRowRepository;
    }

    public void aggregate() {
        LocalDate today = LocalDate.now();
        int count = 0;
        double sum = 0;
        for (OrderRow o : orderRowRepository.findAll()) {
            if (o.getOrderedAt() != null && o.getOrderedAt().toLocalDate().equals(today)) {
                count++;
                sum += o.getTotalAmount();
            }
        }
        System.out.println("[집계] 오늘 주문수=" + count + ", 매출=" + sum);
    }
}
