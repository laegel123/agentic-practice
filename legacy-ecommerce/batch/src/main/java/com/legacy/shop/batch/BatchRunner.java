package com.legacy.shop.batch;

import com.legacy.shop.batch.job.AbandonedCartCleanupJob;
import com.legacy.shop.batch.job.DailySalesAggregationJob;
import com.legacy.shop.batch.job.InventoryReconciliationJob;
import com.legacy.shop.batch.job.SettlementJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 배치 실행기. 앱이 뜨면 잡들을 순서대로 한 번 돌리고 종료한다.
 */
@Component
public class BatchRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BatchRunner.class);

    private final SettlementJob settlementJob;
    private final DailySalesAggregationJob dailySalesAggregationJob;
    private final InventoryReconciliationJob inventoryReconciliationJob;
    private final AbandonedCartCleanupJob abandonedCartCleanupJob;

    public BatchRunner(SettlementJob settlementJob,
                       DailySalesAggregationJob dailySalesAggregationJob,
                       InventoryReconciliationJob inventoryReconciliationJob,
                       AbandonedCartCleanupJob abandonedCartCleanupJob) {
        this.settlementJob = settlementJob;
        this.dailySalesAggregationJob = dailySalesAggregationJob;
        this.inventoryReconciliationJob = inventoryReconciliationJob;
        this.abandonedCartCleanupJob = abandonedCartCleanupJob;
    }

    @Override
    public void run(String... args) {
        log.info("===== 배치 시작 =====");
        settlementJob.settle();
        dailySalesAggregationJob.aggregate();
        inventoryReconciliationJob.reconcile();
        abandonedCartCleanupJob.report();
        log.info("===== 배치 종료 =====");
    }
}
