package com.legacy.shop.batch.job;

import com.legacy.shop.batch.domain.InventoryRow;
import com.legacy.shop.batch.repository.InventoryRowRepository;
import org.springframework.stereotype.Component;

/**
 * 재고 대사 잡: 음수 재고가 있는지 점검한다.
 */
@Component
public class InventoryReconciliationJob {

    private final InventoryRowRepository inventoryRowRepository;

    public InventoryReconciliationJob(InventoryRowRepository inventoryRowRepository) {
        this.inventoryRowRepository = inventoryRowRepository;
    }

    public void reconcile() {
        int negatives = 0;
        for (InventoryRow inv : inventoryRowRepository.findAll()) {
            if (inv.getQuantity() < 0) {
                negatives++;
                System.out.println("[재고대사] 음수 재고 발견 productId=" + inv.getProductId()
                        + " qty=" + inv.getQuantity());
            }
        }
        System.out.println("[재고대사] 음수 재고 건수 = " + negatives);
    }
}
