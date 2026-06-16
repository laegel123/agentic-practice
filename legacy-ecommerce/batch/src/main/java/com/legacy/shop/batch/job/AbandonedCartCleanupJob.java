package com.legacy.shop.batch.job;

import com.legacy.shop.batch.domain.CartRow;
import com.legacy.shop.batch.repository.CartRowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 방치된 장바구니 정리 잡: 30일 이상 안 쓴 장바구니를 정리 대상으로 본다.
 * (실제 삭제는 하지 않고 건수만 리포트한다)
 */
@Component
public class AbandonedCartCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(AbandonedCartCleanupJob.class);

    private final CartRowRepository cartRowRepository;

    public AbandonedCartCleanupJob(CartRowRepository cartRowRepository) {
        this.cartRowRepository = cartRowRepository;
    }

    public void report() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        int candidates = 0;
        for (CartRow cart : cartRowRepository.findAll()) {
            if (cart.getCreatedAt() != null && cart.getCreatedAt().isBefore(cutoff)) {
                candidates++;
            }
        }
        log.info("[장바구니정리] 정리 대상(30일 경과) = {}건", candidates);
    }
}
