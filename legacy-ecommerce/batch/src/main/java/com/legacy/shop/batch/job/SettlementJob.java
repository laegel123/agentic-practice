package com.legacy.shop.batch.job;

import com.legacy.shop.batch.domain.OrderRow;
import com.legacy.shop.batch.repository.OrderRowRepository;
import org.springframework.stereotype.Component;

/**
 * 정산 잡: 전체 주문의 매출 합계를 낸다.
 */
@Component
public class SettlementJob {

    private final OrderRowRepository orderRowRepository;

    public SettlementJob(OrderRowRepository orderRowRepository) {
        this.orderRowRepository = orderRowRepository;
    }

    public double settle() {
        double revenue = 0;
        for (OrderRow o : orderRowRepository.findAll()) {
            revenue += o.getTotalAmount();
        }
        System.out.println("[정산] 총 매출 = " + revenue);
        return revenue;
    }
}
