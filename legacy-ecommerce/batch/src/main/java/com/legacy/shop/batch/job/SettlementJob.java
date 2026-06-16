package com.legacy.shop.batch.job;

import com.legacy.shop.batch.domain.OrderRow;
import com.legacy.shop.batch.repository.OrderRowRepository;
import com.legacy.shop.core.domain.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 정산 잡: 취소되지 않은 주문의 매출 합계를 낸다.
 */
@Component
public class SettlementJob {

    private static final Logger log = LoggerFactory.getLogger(SettlementJob.class);

    private final OrderRowRepository orderRowRepository;

    public SettlementJob(OrderRowRepository orderRowRepository) {
        this.orderRowRepository = orderRowRepository;
    }

    public BigDecimal settle() {
        BigDecimal revenue = BigDecimal.ZERO;
        for (OrderRow o : orderRowRepository.findAll()) {
            if (o.getStatus() == OrderStatus.CANCELLED) {
                continue; // 취소 주문은 매출에서 제외 (BT1)
            }
            revenue = revenue.add(o.getTotalAmount());
        }
        log.info("[정산] 총 매출 = {}", revenue);
        return revenue;
    }
}
